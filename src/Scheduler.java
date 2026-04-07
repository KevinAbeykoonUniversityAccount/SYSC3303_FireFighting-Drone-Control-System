import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * The Scheduler class is the central program of the fire fighting
 * control system. It is responsible for receiving fire events,
 * scheduling drones to put out fires, and reporting back that
 * the fire has been put out.
 *
 * This version includes a state machine to model the scheduler's behaviour.
 *
 * @author Kevin Abeykoon (101301971)
 * @author Aryan Kumar Singh (101299776)
 */
public class Scheduler implements Runnable {

    // ========= CONSTANTS =======
    public static final int PORT = 6000;
    private static final int BUFFER_SIZE = 1024;
    private static final int FULL_BATTERY_LEVEL = 100;


    // ====== SCHEDULER STATE MACHINE ======
    public enum SchedulerState {
        IDLE, DISPATCHING, MONITORING, REFILLING, FAULT_HANDLING
    }

    // ====== FIRE EVENT QUEUES (high -> moderate -> low priority) ======
    private Deque<FireEvent> lowFireEventQueue;
    private Deque<FireEvent> moderateFireEventQueue;
    private Deque<FireEvent> highFireEventQueue;

    // ========= DRONE REGISTRY =======
    /**
     * droneId → lightweight record; replaces direct DroneSubsystem references.
     */
    private final Map<Integer, DroneInfo> droneRegistry;

    /**
     * Litres of water already committed (pushed) to each zone.
     */
    private final Map<Integer, Integer> assignedWaterPerZone;

    /**
     * Zone definitions used to compute centre coordinates for dispatch.
     */
    private final Map<Integer, Zone> zones;
    private final Map<Integer, Deque<FireEvent>> pendingFiresByZone = new HashMap<>();

    /** Incremented every time zones are successfully replaced; GUI polls this. */
    private volatile int zonesVersion = 0;

    // ======= COUNTERS & STATE ============
    private final SimulationClock clock;
    private SchedulerState currentState = SchedulerState.IDLE;
    private int activeMissionCount = 0;
    private int refillingCount = 0;
    /** Prevents sending RETURN_TO_BASE more than once per completed simulation run. */
    private boolean allDronesReturnedHome = false;


    // =========== NETWORKING =======
    private final DatagramSocket socket;
    private volatile boolean running = true;

    /** Callback that mirrors key log messages to the GUI System Log panel. */
    private volatile Consumer<String> logCallback = null;
    private InetAddress loggerAddress;

    public void setLogCallback(Consumer<String> cb) { this.logCallback = cb; }

    /** Prints to console and forwards to the GUI log (if wired). */
    private void log(String msg) {
        System.out.print(msg);
        if (logCallback != null) logCallback.accept(msg.trim());
    }

    public void logEvent(String msg) {
        byte[] event = msg.getBytes();
        try {
            socket.send(new DatagramPacket(event, event.length, loggerAddress, EventLogger.DEFAULT_PORT));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private final Map<Integer, FireEvent> droneActiveMission = new HashMap<>();

    public Scheduler() throws SocketException, UnknownHostException {
        highFireEventQueue = new LinkedList<>();
        moderateFireEventQueue = new LinkedList<>();
        lowFireEventQueue = new LinkedList<>();
        droneRegistry = new HashMap<>();
        assignedWaterPerZone = new HashMap<>();
        zones = new HashMap<>();
        clock = SimulationClock.getInstance();
        socket = new DatagramSocket(PORT);
        loggerAddress = InetAddress.getLocalHost();

        zones.put(1, new Zone(1, 0, 14, 0, 14));
        zones.put(2, new Zone(2, 15, 29, 0, 14));
        zones.put(3, new Zone(3, 0, 14, 15, 29));
        zones.put(4, new Zone(4, 15, 29, 15, 29));

        System.out.println("Scheduler: Listening on UDP port " + PORT);
    }

    public Scheduler(String loggerHost) throws SocketException, UnknownHostException {
        highFireEventQueue = new LinkedList<>();
        moderateFireEventQueue = new LinkedList<>();
        lowFireEventQueue = new LinkedList<>();
        droneRegistry = new HashMap<>();
        assignedWaterPerZone = new HashMap<>();
        zones = new HashMap<>();
        clock = SimulationClock.getInstance();
        socket = new DatagramSocket(PORT);
        this.loggerAddress = InetAddress.getByName(loggerHost);

        zones.put(1, new Zone(1, 0, 14, 0, 14));
        zones.put(2, new Zone(2, 15, 29, 0, 14));
        zones.put(3, new Zone(3, 0, 14, 15, 29));
        zones.put(4, new Zone(4, 15, 29, 15, 29));

        System.out.println("Scheduler: Listening on UDP port " + PORT);
    }


    @Override
    public void run() {
        logEvent("Scheduler,STARTED");
        byte[] buf = new byte[BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
                dispatch(msg, pkt.getAddress(), pkt.getPort());
            } catch (SocketException e) {
                if (running) System.err.println("Scheduler socket error: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Scheduler dispatch error: " + e.getMessage());
            }
        }
        logEvent("Scheduler,ENDED");
    }

    /**
     * Routes an incoming UDP message to the correct existing method.
     * Format: methodName|arg1|arg2|...
     */
    private synchronized void dispatch(String message, InetAddress addr, int port)
            throws Exception {
        String[] parts = message.split("\\|");
        switch (parts[0]) {
            case "startClock": {
                int speed = Integer.parseInt(parts[2]);
                clock.setClockSpeedMultiplier(speed);
                clock.setSimulationStartTime(0, 0, 0);  // always reset to 0 for new run
                if (!clock.isRunning()) {
                    new Thread(clock, "SimulationClock").start();
                }
                sendReply("ACK", addr, port);
                System.out.printf("Scheduler: Clock started at %s (x%d)%n",
                        clock.getFormattedTime(), speed);
                break;
            }

            case "registerDrone": {
                // registerDrone|droneId|x|y|water|listenPort
                int droneId = Integer.parseInt(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int water = Integer.parseInt(parts[4]);
                int listenPort = Integer.parseInt(parts[5]);
                int battery = Integer.parseInt(parts[6]);

                // Remove any stale entry for this droneId from a previous run
                droneRegistry.remove(droneId);

                DroneInfo info = new DroneInfo(droneId, x, y, water, addr, listenPort, battery);
                droneRegistry.put(droneId, info);
                System.out.printf("Scheduler: Drone %d registered at %s:%d%n",
                        droneId, addr.getHostAddress(), listenPort);
                sendReply("ACK", addr, port);
                tryDispatch();
                break;
            }

            case "receiveFireEvent": {
                // receiveFireEvent|zoneId|eventType|severity|secondsFromStart
                FireEvent event = new FireEvent(
                        Integer.parseInt(parts[1]),
                        parts[2],
                        parts[3],
                        Integer.parseInt(parts[4]));
                receiveFireEvent(event);
                sendReply("ACK", addr, port);
                break;
            }

            case "injectFaultEvent": {
                // injectFaultEvent|droneId|faultType
                // Sent by FireIncidentSubsystem when a fault row is reached in the CSV.
                // We forward INJECT_FAULT directly to the target drone.
                int       droneId = Integer.parseInt(parts[1]);
                FaultType fault   = FaultType.from(parts[2]);
                DroneInfo drone   = droneRegistry.get(droneId);

                if (drone != null) {
                    log(String.format("Scheduler [%s]: Injecting %s into Drone %d%n",
                            clock.getFormattedTime(), fault, droneId));
                    logEvent("Scheduler,DRONE_FAULT,Drone " + droneId);
                    String injectMsg = "INJECT_FAULT|" + droneId + "|" + fault.name();
                    byte[] data = injectMsg.getBytes();
                    socket.send(new DatagramPacket(data, data.length, drone.address, drone.port));
                } else {
                    System.err.printf("Scheduler: injectFaultEvent — unknown droneId %d%n", droneId);
                }
                sendReply("ACK", addr, port);
                break;
            }

            // Drone finished extinguishing
            case "missionCompleted": {
                // missionCompleted|droneId|zoneId|waterUsed
                missionCompleted(
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]));
                sendReply("ACK", addr, port);
                break;
            }

            // Drone heading back to base
            case "droneRefilling": {
                droneRefilling(Integer.parseInt(parts[1]));
                sendReply("ACK", addr, port);
                break;
            }

            // Drone refill complete, ready for missions
            case "droneRefillComplete": {
                droneRefillComplete(Integer.parseInt(parts[1]));
                sendReply("ACK", addr, port);
                break;
            }

            // Continuous position report from drone
            case "locationUpdate": {
                // locationUpdate|droneId|x|y|state
                int droneId = Integer.parseInt(parts[1]);
                DroneInfo info = droneRegistry.get(droneId);
                if (info != null) {
                    int newX = Integer.parseInt(parts[2]);
                    int newY = Integer.parseInt(parts[3]);
                    String newState = parts[4];
                    // Log meaningful mid-flight state transitions
                    if ("FAULTED".equals(newState) && !"FAULTED".equals(info.state)) {
                        log(String.format("Scheduler [%s]: Drone %d STUCK at (%d,%d) — pausing%n",
                                clock.getFormattedTime(), droneId, newX, newY));
                    }
                    info.x     = newX;
                    info.y     = newY;
                    info.state = newState;
                }
                sendReply("ACK", addr, port);
                break;
            }
            case "batteryUpdate": {
                // batteryUpdate|droneId|battery
                int droneId = Integer.parseInt(parts[1]);
                DroneInfo info = droneRegistry.get(droneId);
                if (info != null) {
                    int battery = Integer.parseInt(parts[2]);
                    info.batteryLevel     = battery;
                }
                sendReply("ACK", addr, port);
                break;
            }

            // Drone returns an interrupted mission for re-queuing
            // rescheduleFireEvent|zoneId|eventType|severity|waterRemaining|secondsFromStart
            case "rescheduleFireEvent": {
                int waterRemaining = Integer.parseInt(parts[4]);
                int secondsFromStart = Integer.parseInt(parts[5]);
                FireEvent event = new FireEvent(
                        Integer.parseInt(parts[1]),
                        parts[2],
                        parts[3],
                        secondsFromStart);
                // Restore the exact water still needed so another drone picks up the right amount
                int deficit = event.getWaterRemaining() - waterRemaining;
                if (deficit > 0) {
                    event.waterUsed(deficit);
                }
                rescheduleUnfinishedFireEvent(event);
                sendReply("ACK", addr, port);
                break;
            }

            // FireIncidentSubsystem clock query
            case "getTime": {
                sendReply(String.valueOf(clock.getSimulationTimeSeconds()), addr, port);
                break;
            }

            case "loadZones": {
                String filePath = parts[1];
                try {
                    List<String> errors = loadZonesFromFile(filePath);
                    if (errors.isEmpty()) {
                        sendReply("ACK", addr, port);
                        log("Scheduler: Zones loaded from " + filePath);
                    } else {
                        sendReply("ERR|" + String.join(";", errors), addr, port);
                        log("Scheduler: Zone load errors: " + errors);
                    }
                } catch (Exception e) {
                    sendReply("ERR|" + e.getMessage(), addr, port);
                }
                break;
            }

            // Soft fault
            case "droneFaulted": {
                int droneId = Integer.parseInt(parts[1]);
                DroneInfo drone = droneRegistry.get(droneId);
                if (drone != null) drone.state = "FAULTED";

                log(String.format("Scheduler [%s]: Drone %d SOFT FAULT — re-queuing mission%n",
                        clock.getFormattedTime(), droneId));

                activeMissionCount = Math.max(0, activeMissionCount - 1);
                retrieveAndRescheduleLostMission(droneId);

                // ACK only — do NOT send FAULT back to the drone.
                // The drone manages its own recovery and will call droneRecovered.
                sendReply("ACK", addr, port);
                currentState = SchedulerState.FAULT_HANDLING;
                break;
            }

            // Hard fault
            case "droneHardFault": {
                int droneId = Integer.parseInt(parts[1]);
                DroneInfo drone = droneRegistry.get(droneId);

                log(String.format("Scheduler [%s]: Drone %d HARD FAULT — decommissioning%n",
                        clock.getFormattedTime(), droneId));

                if (drone != null) drone.state = "DECOMMISSIONED";

                activeMissionCount = Math.max(0, activeMissionCount - 1);
                retrieveAndRescheduleLostMission(droneId);

                // Tell DroneSubsystem to shut this drone down permanently.
                // DECOMMISSION|droneId so DroneSubsystem can route it.
                if (drone != null) {
                    sendReply("DECOMMISSION|" + droneId, drone.address, drone.port);
                }

                sendReply("ACK", addr, port);
                currentState = SchedulerState.FAULT_HANDLING;
                tryDispatch();
                break;
            }

            // Soft fault recovery
            case "droneRecovered": {
                int droneId = Integer.parseInt(parts[1]);
                DroneInfo drone = droneRegistry.get(droneId);
                if (drone != null) drone.state = "IDLE";
                log(String.format("Scheduler [%s]: Drone %d recovered — IDLE%n",
                        clock.getFormattedTime(), droneId));
                // If the fault fired during extinguishing, the mission was abandoned
                // without calling missionCompleted. Re-queue it so the fire is not lost.
                retrieveAndRescheduleLostMission(droneId);
                updateSchedulerState(0);
                tryDispatch();
                break;
            }

            default:
                System.err.println("Scheduler: unknown message: " + parts[0]);
        }
    }

    /**
     * Looks up the mission that was active on the faulted drone, reduces
     * the assignedWater tracking, and re-queues it at the front of its
     * priority queue so another drone can pick it up.
     *
     * @param droneId The specified drone
     */
    private void retrieveAndRescheduleLostMission(int droneId) {
        FireEvent lost = droneActiveMission.remove(droneId);
        if (lost != null) {
            System.out.printf("Scheduler: Re-queuing lost mission from Drone %d"
                    + " (Zone %d)%n", droneId, lost.getZoneId());
            assignedWaterPerZone.computeIfPresent(
                    lost.getZoneId(),
                    (k, v) -> (v - lost.getWaterRemaining() <= 0)
                            ? null : v - lost.getWaterRemaining());
            rescheduleUnfinishedFireEvent(lost);
        }
    }


    /**
     * Greedily assigns pending fire events to idle drones by pushing
     * ASSIGN_MISSION packets.  Called whenever the queue or drone
     * availability changes.  Loops until no further assignments can be made.
     */
    private void tryDispatch() {
        while (true) {
            FireEvent mission = peekHighestPriorityEvent();
            if (mission == null) break;  // no pending fires

            Zone zone = zones.get(mission.getZoneId());
            if (zone == null) break;

            int droneId = getClosestIdleDroneWithWater(
                    zone.getCenterX(), zone.getCenterY());
            if (droneId == -1) break;  // no capable drone available right now

            // Commit to this assignment — consume the event from the queue
            mission = retrieveHighestPriorityEvent();

            DroneInfo drone = droneRegistry.get(droneId);
            int waterToAssign = Math.min(drone.waterRemaining,
                    mission.getWaterRemaining());
            int remainingWater = mission.getWaterRemaining() - waterToAssign;

            assignedWaterPerZone.merge(mission.getZoneId(), waterToAssign, Integer::sum);
            drone.state = "ONROUTE";
            activeMissionCount++;

            FireEvent droneMission;
            if (remainingWater > 0) {
                // Partial assignment — put the remainder back at the front.
                droneMission = new FireEvent(mission, waterToAssign);
                mission.waterUsed(waterToAssign);
                rescheduleUnfinishedFireEvent(mission);
                log(String.format(
                        "Scheduler [%s]: Drone %d -> Zone %d PARTIAL"
                                + " (%dL assigned, %dL remain)%n",
                        clock.getFormattedTime(), droneId,
                        mission.getZoneId(), waterToAssign, remainingWater));
            } else {
                droneMission = mission;
                log(String.format(
                        "Scheduler [%s]: Drone %d -> Zone %d FULL (%dL)%n",
                        clock.getFormattedTime(), droneId,
                        mission.getZoneId(), waterToAssign));
            }

            droneActiveMission.put(droneId, droneMission);
            pushMissionToDrone(drone, droneMission, zone.getCenterX(), zone.getCenterY());
            updateSchedulerState(remainingWater);
        }
    }


    /**
     * Sends ASSIGN_MISSION directly to the drone's registered listen port.
     * <p>
     * Message: ASSIGN_MISSION|droneId|zoneId|eventType|severity|waterAssigned|secondsFromStart|targetX|targetY
     */
    private void pushMissionToDrone(DroneInfo drone, FireEvent mission, int targetX, int targetY) {
        try {
            String msg = "ASSIGN_MISSION|"
                    + drone.droneId                  + "|"
                    + mission.getZoneId()             + "|"
                    + mission.getEventType()          + "|"
                    + mission.getSeverity().name()    + "|"
                    + mission.getWaterRemaining()     + "|"
                    + mission.getSecondsFromStart()   + "|"
                    + targetX                         + "|"
                    + targetY;
            byte[] data = msg.getBytes();
            socket.send(new DatagramPacket(
                    data, data.length, drone.address, drone.port));
            System.out.printf("Scheduler: Pushed mission to Drone %d at %s:%d%n",
                    drone.droneId, drone.address.getHostAddress(), drone.port);
        } catch (Exception e) {
            System.err.printf("Scheduler: failed to push to Drone %d: %s%n",
                    drone.droneId, e.getMessage());
        }
    }


    private int getClosestIdleDroneWithWater(int targetX, int targetY) {
        int bestId = -1;
        double bestDist = Double.MAX_VALUE;

        for (DroneInfo drone : droneRegistry.values()) {
            if (!"IDLE".equals(drone.state)) continue;
            if (drone.waterRemaining <= 0) continue;

            double dist = distance(drone.x, drone.y, targetX, targetY);
            if (dist < bestDist) {
                bestDist = dist;
                bestId = drone.droneId;
            }
        }
        return bestId;
    }

    /**
     * Euclidean distance between two grid cells.
     * Note: ^ in Java is bitwise XOR, not exponentiation — use multiplication.
     */
    public double distance(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // =========== FIRE QUEUE MANAGEMENT ===========

    public synchronized void receiveFireEvent(FireEvent event) {
        allDronesReturnedHome = false; // new fire means simulation isn't done yet
        int zoneId = event.getZoneId();

        // Check if this zone already has an active or queued fire
        boolean zoneAlreadyBurning = isZoneActive(zoneId);

        if (zoneAlreadyBurning) {
            // Hold it until the current fire is resolved
            pendingFiresByZone
                    .computeIfAbsent(zoneId, k -> new LinkedList<>())
                    .add(event);
            System.out.printf("Scheduler [%s]: Zone %d already has an active fire " +
                    "— queuing new event%n", clock.getFormattedTime(), zoneId);
            return;
        }

        log(String.format("Scheduler [%s]: Fire at Zone %d (severity=%s)%n",
                clock.getFormattedTime(), event.getZoneId(), event.getSeverity()));
        enqueue(event);
        logEvent("Scheduler,FIRE_DETECTED,ZONE " + event.getZoneId());
        if (currentState == SchedulerState.IDLE) {
            currentState = SchedulerState.DISPATCHING;
        }
        tryDispatch();
    }

    /** Returns true if this zone has a fire currently queued or being serviced. */
    private boolean isZoneActive(int zoneId) {
        // Check the priority queues
        for (FireEvent e : highFireEventQueue)
            if (e.getZoneId() == zoneId) return true;
        for (FireEvent e : moderateFireEventQueue)
            if (e.getZoneId() == zoneId) return true;
        for (FireEvent e : lowFireEventQueue)
            if (e.getZoneId() == zoneId) return true;
        // Check currently assigned missions
        return assignedWaterPerZone.containsKey(zoneId);
    }

    private void enqueue(FireEvent event) {
        switch (event.getSeverity()) {
            case HIGH:
                highFireEventQueue.add(event);
                break;
            case MODERATE:
                moderateFireEventQueue.add(event);
                break;
            default:
                lowFireEventQueue.add(event);
                break;
        }
    }

    private void releasePendingFireForZone(int zoneId) {
        Deque<FireEvent> pending = pendingFiresByZone.get(zoneId);
        if (pending != null && !pending.isEmpty()) {
            FireEvent next = pending.poll();
            System.out.printf("Scheduler [%s]: Zone %d clear — releasing held fire event%n",
                    clock.getFormattedTime(), zoneId);
            enqueue(next);
            if (currentState == SchedulerState.IDLE)
                currentState = SchedulerState.DISPATCHING;
        }
    }

    /**
     * Non-destructive peek at the highest-priority queued event.
     */
    private FireEvent peekHighestPriorityEvent() {
        if (!highFireEventQueue.isEmpty()) return highFireEventQueue.peekFirst();
        if (!moderateFireEventQueue.isEmpty()) return moderateFireEventQueue.peekFirst();
        if (!lowFireEventQueue.isEmpty()) return lowFireEventQueue.peekFirst();
        return null;
    }

    /**
     * Destructive poll of the highest-priority queued event.
     */
    private FireEvent retrieveHighestPriorityEvent() {
        if (!highFireEventQueue.isEmpty()) return highFireEventQueue.pollFirst();
        if (!moderateFireEventQueue.isEmpty()) return moderateFireEventQueue.pollFirst();
        if (!lowFireEventQueue.isEmpty()) return lowFireEventQueue.pollFirst();
        return null;
    }

    /**
     * Re-inserts a partially-serviced event at the front of its priority queue.
     */
    public synchronized void rescheduleUnfinishedFireEvent(FireEvent event) {
        switch (event.getSeverity()) {
            case HIGH:
                highFireEventQueue.addFirst(event);
                break;
            case MODERATE:
                moderateFireEventQueue.addFirst(event);
                break;
            default:
                lowFireEventQueue.addFirst(event);
                break;
        }
    }

    // =========== MISSION LIFECYCLE =========

    /**
     * Called when a drone reports it has finished extinguishing.
     *
     * @param droneId   ID of the reporting drone
     * @param zoneId    zone that was serviced
     * @param waterUsed litres actually dropped
     */
    public synchronized void missionCompleted(int droneId, int zoneId, int waterUsed) {
        log(String.format("Scheduler [%s]: Drone %d completed Zone %d (used %dL)%n",
                clock.getFormattedTime(), droneId, zoneId, waterUsed));

        // Reduce or remove the committed water entry for this zone
        assignedWaterPerZone.computeIfPresent(zoneId,
                (k, v) -> (v - waterUsed <= 0) ? null : v - waterUsed);

        if (!isZoneActive(zoneId)) logEvent("Scheduler,FIRE_EXTINGUISHED,ZONE " + zoneId);

        // Update the drone record
        DroneInfo info = droneRegistry.get(droneId);
        if (info != null) {
            info.waterRemaining = Math.max(0, info.waterRemaining - waterUsed);
            // Drone will head to base if empty; it reports droneRefilling
            // separately. Mark it idle here only if it still has water.
            if (info.waterRemaining > 0) {
                info.state = "IDLE";
            } else {
                info.state = "RETURNING";
            }
        }

        activeMissionCount--;
        updateSchedulerState(0);
        tryDispatch();
        releasePendingFireForZone(zoneId);
        checkAndSendDronesHome();
    }

    public synchronized void droneRefilling(int droneId) {
        log(String.format("Scheduler [%s]: Drone %d returning to base — refilling%n",
                clock.getFormattedTime(), droneId));
        DroneInfo info = droneRegistry.get(droneId);
        if (info != null) info.state = "REFILLING";
        refillingCount++;
        currentState = SchedulerState.REFILLING;
    }

    public synchronized void droneRefillComplete(int droneId) {
        log(String.format("Scheduler [%s]: Drone %d refill complete — IDLE%n",
                clock.getFormattedTime(), droneId));
        DroneInfo info = droneRegistry.get(droneId);
        if (info != null) {
            info.state = "IDLE";
            info.waterRemaining = 15;
            info.batteryLevel = 100;
        }
        refillingCount = Math.max(0, refillingCount - 1);
        updateSchedulerState(0);
        tryDispatch();
        checkAndSendDronesHome();
    }

    /**
     * When all fire queues are empty and no missions are active, every IDLE
     * drone that is not already at base is sent home.  Called after every
     * mission completion and every refill — the first call that finds the
     * system truly quiescent wins; subsequent calls are no-ops.
     */
    private void checkAndSendDronesHome() {
        if (allDronesReturnedHome) return;
        if (activeMissionCount > 0) return;
        if (!highFireEventQueue.isEmpty() || !moderateFireEventQueue.isEmpty()
                || !lowFireEventQueue.isEmpty()) return;
        for (Deque<FireEvent> q : pendingFiresByZone.values()) {
            if (!q.isEmpty()) return;
        }
        if (droneRegistry.isEmpty()) return;

        allDronesReturnedHome = true;
        log(String.format("Scheduler [%s]: All missions complete — sending idle drones to base%n",
                clock.getFormattedTime()));

        for (DroneInfo drone : droneRegistry.values()) {
            if ("IDLE".equals(drone.state) && (drone.x != 0 || drone.y != 0)) {
                try {
                    String msg = "RETURN_TO_BASE|" + drone.droneId;
                    byte[] data = msg.getBytes();
                    socket.send(new DatagramPacket(data, data.length, drone.address, drone.port));
                    drone.state = "RETURNING";
                    log(String.format("Scheduler [%s]: Drone %d returning to base%n",
                            clock.getFormattedTime(), drone.droneId));
                } catch (Exception e) {
                    System.err.printf("Scheduler: failed to send RETURN_TO_BASE to Drone %d%n",
                            drone.droneId);
                }
            }
        }
    }

    // =========== SCHEDULER STATE TRANSITIONS =========

    private void updateSchedulerState(int remainingWater) {
        if (remainingWater > 0) {
            currentState = SchedulerState.DISPATCHING;
            return;
        }
        boolean anyQueued = !highFireEventQueue.isEmpty()
                || !moderateFireEventQueue.isEmpty()
                || !lowFireEventQueue.isEmpty();
        if (activeMissionCount == 0) {
            currentState = anyQueued
                    ? SchedulerState.DISPATCHING : SchedulerState.IDLE;
        } else {
            currentState = anyQueued
                    ? SchedulerState.DISPATCHING : SchedulerState.MONITORING;
        }
    }

    // =========== ACCESSORS FOR GUI & TESTING =========

    /**
     * Single-lock snapshot of all GUI-needed data.
     * Replaces three separate synchronized calls so the EDT holds the
     * Scheduler lock for one short window instead of three, reducing
     * contention with the UDP dispatch loop.
     */
    public static class GuiSnapshot {
        public final Map<Integer, DroneInfo> drones;
        public final Map<Integer, Integer>   firesPerZone;
        public final int[]                   fireCounts;   // [high, moderate, low]

        GuiSnapshot(Map<Integer, DroneInfo> drones,
                    Map<Integer, Integer> firesPerZone,
                    int[] fireCounts) {
            this.drones      = drones;
            this.firesPerZone = firesPerZone;
            this.fireCounts  = fireCounts;
        }
    }

    public synchronized GuiSnapshot getGuiSnapshot() {
        Map<Integer, DroneInfo> dronesCopy = new HashMap<>(droneRegistry);

        Map<Integer, Integer> fires = new HashMap<>();
        for (FireEvent e : highFireEventQueue)
            fires.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (FireEvent e : moderateFireEventQueue)
            fires.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (FireEvent e : lowFireEventQueue)
            fires.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (Map.Entry<Integer, Integer> entry : assignedWaterPerZone.entrySet())
            fires.merge(entry.getKey(), entry.getValue(), Integer::sum);

        int[] counts = {
                highFireEventQueue.size(),
                moderateFireEventQueue.size(),
                lowFireEventQueue.size()
        };

        return new GuiSnapshot(dronesCopy, fires, counts);
    }

    public synchronized SchedulerState getCurrentState() {
        return currentState;
    }

    public synchronized Map<Integer, DroneInfo> getDroneRegistry() {
        return Collections.unmodifiableMap(droneRegistry);
    }

    public synchronized int[] getFireCountsBySeverity() {
        return new int[]{
                highFireEventQueue.size(),
                moderateFireEventQueue.size(),
                lowFireEventQueue.size()
        };
    }

    public synchronized Map<Integer, Integer> getActiveFiresPerZone() {
        Map<Integer, Integer> total = new HashMap<>();
        for (FireEvent e : highFireEventQueue)
            total.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (FireEvent e : moderateFireEventQueue)
            total.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (FireEvent e : lowFireEventQueue)
            total.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (Map.Entry<Integer, Integer> entry : assignedWaterPerZone.entrySet())
            total.merge(entry.getKey(), entry.getValue(), Integer::sum);
        return total;
    }


    private void sendReply(String message, InetAddress addr, int port)
            throws Exception {
        byte[] data = message.getBytes();
        socket.send(new DatagramPacket(data, data.length, addr, port));
    }

    public synchronized Map<Integer, Zone> getZones() {
        return Collections.unmodifiableMap(zones);
    }

    public int getZonesVersion() { return zonesVersion; }

    /**
     * Number of real-world metres represented by one grid cell.
     * A 30×30 cell grid covers a 900 m × 900 m area.
     */
    public static final int METERS_PER_CELL = 30;

    /**
     * Replaces the hardcoded zones with zones parsed from a CSV file.
     *
     * CSV format (header line then data rows):
     *   ZoneID, ZoneStart, ZoneEnd
     *   1, (0, 0), (450, 450)
     *
     * Coordinates are in metres and converted to grid cells by dividing by
     * METERS_PER_CELL (30 m per cell).
     *
     * Validation:
     *   - x2 > x1, y2 > y1, all values >= 0
     *   - Width and height must each be at least 90 m (3 grid cells)
     *   - No duplicate zone IDs
     *
     * @return list of validation/parse error messages; empty on success.
     */
    public synchronized List<String> loadZonesFromFile(String filePath) throws IOException {
        List<String> errors = new ArrayList<>();
        Map<Integer, Zone> newZones = new HashMap<>();

        // Extract every non-negative integer from each line regardless of delimiter/parens.
        java.util.regex.Pattern numPat = java.util.regex.Pattern.compile("\\d+");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                if (firstLine) { firstLine = false; continue; } // skip header

                // Collect numbers: [zoneId, x1, y1, x2, y2]
                java.util.regex.Matcher mat = numPat.matcher(line);
                List<Integer> nums = new ArrayList<>();
                while (mat.find()) nums.add(Integer.parseInt(mat.group()));

                if (nums.size() < 5) {
                    errors.add("Invalid line (expected ZoneID, (x1,y1), (x2,y2)): " + line.trim());
                    continue;
                }
                try {
                    int id       = nums.get(0);
                    int x1Metres = nums.get(1);
                    int y1Metres = nums.get(2);
                    int x2Metres = nums.get(3);
                    int y2Metres = nums.get(4);

                    if (x2Metres <= x1Metres || y2Metres <= y1Metres) {
                        errors.add("Zone " + id + ": end point must be greater than start point in both axes");
                        continue;
                    }
                    int minMetres = 3 * METERS_PER_CELL; // 90 m minimum per dimension
                    if ((x2Metres - x1Metres) < minMetres || (y2Metres - y1Metres) < minMetres) {
                        errors.add("Zone " + id + ": must be at least " + minMetres
                                + " m wide and tall (3 grid cells each)");
                        continue;
                    }
                    if (newZones.containsKey(id)) {
                        errors.add("Duplicate zone ID: " + id);
                        continue;
                    }

                    // End boundary is exclusive (shared edge between adjacent zones).
                    // Subtract 1 so adjacent zones never claim the same cell.
                    int xMin = x1Metres / METERS_PER_CELL;
                    int xMax = x2Metres / METERS_PER_CELL - 1;
                    int yMin = y1Metres / METERS_PER_CELL;
                    int yMax = y2Metres / METERS_PER_CELL - 1;
                    newZones.put(id, new Zone(id, xMin, xMax, yMin, yMax));
                } catch (NumberFormatException e) {
                    errors.add("Non-numeric value in line: " + line.trim());
                }
            }
        }

        if (!errors.isEmpty()) return errors;
        if (newZones.isEmpty()) {
            errors.add("No valid zones found in file.");
            return errors;
        }

        // ── Coverage validation ───────────────────────────────────────────────
        // Zones must tile the bounding rectangle exactly: no gaps, no overlaps.
        int bxMin = Integer.MAX_VALUE, bxMax = 0;
        int byMin = Integer.MAX_VALUE, byMax = 0;
        for (Zone z : newZones.values()) {
            bxMin = Math.min(bxMin, z.getXMin());  bxMax = Math.max(bxMax, z.getXMax());
            byMin = Math.min(byMin, z.getYMin());  byMax = Math.max(byMax, z.getYMax());
        }
        int gridW = bxMax - bxMin + 1;
        int gridH = byMax - byMin + 1;
        boolean[][] covered = new boolean[gridW][gridH];

        for (Zone z : newZones.values()) {
            for (int x = z.getXMin(); x <= z.getXMax(); x++) {
                for (int y = z.getYMin(); y <= z.getYMax(); y++) {
                    int cx = x - bxMin, cy = y - byMin;
                    if (covered[cx][cy]) {
                        errors.add(String.format(
                                "Zones overlap at cell (%d,%d) = (%dm,%dm)",
                                x, y, x * METERS_PER_CELL, y * METERS_PER_CELL));
                        return errors;
                    }
                    covered[cx][cy] = true;
                }
            }
        }
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                if (!covered[x][y]) {
                    int gx = x + bxMin, gy = y + byMin;
                    errors.add(String.format(
                            "Gap in zone coverage at cell (%d,%d) = (%dm,%dm)",
                            gx, gy, gx * METERS_PER_CELL, gy * METERS_PER_CELL));
                    return errors;
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        zones.clear();
        zones.putAll(newZones);
        zonesVersion++;
        System.out.println("Scheduler: Loaded " + zones.size() + " zones from " + filePath);
        return errors;
    }

    public void stop() {
        running = false;
        socket.close();
    }

    // =========== TEST HELPERS (package-private) ===========

    /** Directly registers a drone without going through UDP. */
    synchronized void registerDroneForTest(int droneId, int water) throws Exception {
        DroneInfo info = new DroneInfo(droneId, 0, 0, water,
                InetAddress.getByName("localhost"), 60000 + droneId, 100);
        droneRegistry.put(droneId, info);
    }

    /** Returns a copy of a drone's current record, or null if unknown. */
    synchronized DroneInfo getDroneInfo(int droneId) {
        return droneRegistry.get(droneId);
    }

    /** Number of fires held in the pending queue for the given zone. */
    synchronized int pendingFireCountForZone(int zoneId) {
        Deque<FireEvent> q = pendingFiresByZone.get(zoneId);
        return q == null ? 0 : q.size();
    }

    /** True if a fire for this zone is sitting in any priority dispatch queue. */
    synchronized boolean hasQueuedFireForZone(int zoneId) {
        for (FireEvent e : highFireEventQueue)     if (e.getZoneId() == zoneId) return true;
        for (FireEvent e : moderateFireEventQueue) if (e.getZoneId() == zoneId) return true;
        for (FireEvent e : lowFireEventQueue)      if (e.getZoneId() == zoneId) return true;
        return false;
    }
}