import java.net.*;
import java.util.*;

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
public class





Scheduler implements Runnable {

    // ========= CONSTANTS =======
    public static final int PORT = 6000;
    private static final int BUFFER_SIZE = 1024;


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

    // ======= COUNTERS & STATE ============
    private final SimulationClock clock;
    private SchedulerState currentState = SchedulerState.IDLE;
    private int activeMissionCount = 0;
    private int refillingCount = 0;


    // =========== NETWORKING =======
    private final DatagramSocket socket;
    private volatile boolean running = true;


    public Scheduler() throws SocketException {
        highFireEventQueue = new LinkedList<>();
        moderateFireEventQueue = new LinkedList<>();
        lowFireEventQueue = new LinkedList<>();
        droneRegistry = new HashMap<>();
        assignedWaterPerZone = new HashMap<>();
        zones = new HashMap<>();
        clock = SimulationClock.getInstance();
        socket = new DatagramSocket(PORT);

        zones.put(1, new Zone(1, 0, 14, 0, 14));
        zones.put(2, new Zone(2, 15, 29, 0, 14));
        zones.put(3, new Zone(3, 0, 14, 15, 29));
        zones.put(4, new Zone(4, 15, 29, 15, 29));

        System.out.println("Scheduler: Listening on UDP port " + PORT);
    }


    @Override
    public void run() {
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
    }

    /**
     * Routes an incoming UDP message to the correct existing method.
     * Format: methodName|arg1|arg2|...
     */
    private synchronized void dispatch(String message, InetAddress addr, int port)
            throws Exception {
        String[] parts = message.split("\\|");
        switch (parts[0]) {

            case "registerDrone": {
                // registerDrone|droneId|x|y|water|listenPort
                int droneId = Integer.parseInt(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int water = Integer.parseInt(parts[4]);
                int listenPort = Integer.parseInt(parts[5]);

                // Remove any stale entry for this droneId from a previous run
                droneRegistry.remove(droneId);

                DroneInfo info = new DroneInfo(droneId, x, y, water, addr, listenPort);
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
                    info.x = Integer.parseInt(parts[2]);
                    info.y = Integer.parseInt(parts[3]);
                    info.state = parts[4];
                }
                sendReply("ACK", addr, port);
                break;
            }

            // Drone returns an interrupted mission for re-queuing
            case "rescheduleFireEvent": {
                // rescheduleFireEvent|zoneId|eventType|severity|waterRemaining|secondsFromStart
                FireEvent event = new FireEvent(
                        Integer.parseInt(parts[1]),
                        parts[2],
                        parts[3],
                        Integer.parseInt(parts[5]));

                // The drone reports how much water the fire still needs
                int waterStillNeeded = Integer.parseInt(parts[4]);
                int originalWater = event.getWaterRemaining();
                if (originalWater > waterStillNeeded) {
                    event.waterUsed(originalWater - waterStillNeeded);
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

            default:
                System.err.println("Scheduler: unknown message: " + parts[0]);
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
                // Partial assignment — put the remainder back at the front
                droneMission = new FireEvent(mission, waterToAssign);
                mission.waterUsed(waterToAssign);
                rescheduleUnfinishedFireEvent(mission);
                System.out.printf(
                        "Scheduler [%s]: Drone %d → Zone %d PARTIAL"
                                + " (%dL assigned, %dL remain)%n",
                        clock.getFormattedTime(), droneId,
                        mission.getZoneId(), waterToAssign, remainingWater);
            } else {
                droneMission = mission;
                System.out.printf(
                        "Scheduler [%s]: Drone %d → Zone %d FULL (%dL)%n",
                        clock.getFormattedTime(), droneId,
                        mission.getZoneId(), waterToAssign);
            }

            pushMissionToDrone(drone, droneMission);
            updateSchedulerState(remainingWater);
        }
    }


    /**
     * Sends ASSIGN_MISSION directly to the drone's registered listen port.
     * <p>
     * Message: ASSIGN_MISSION|zoneId|eventType|severity|waterAssigned|secondsFromStart
     */
    private void pushMissionToDrone(DroneInfo drone, FireEvent mission) {
        try {
            String msg = "ASSIGN_MISSION|"
                    + drone.droneId             + "|"   // ← add this
                    + mission.getZoneId()        + "|"
                    + mission.getEventType()     + "|"
                    + mission.getSeverity().name() + "|"
                    + mission.getWaterRemaining() + "|"
                    + mission.getSecondsFromStart();
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
        System.out.printf("Scheduler [%s]: Fire at Zone %d (severity=%s)%n",
                clock.getFormattedTime(), event.getZoneId(), event.getSeverity());
        enqueue(event);
        if (currentState == SchedulerState.IDLE) {
            currentState = SchedulerState.DISPATCHING;
        }
        tryDispatch();
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
        System.out.printf("Scheduler [%s]: Drone %d completed mission"
                        + " at Zone %d (used %dL)%n",
                clock.getFormattedTime(), droneId, zoneId, waterUsed);

        // Reduce or remove the committed water entry for this zone
        assignedWaterPerZone.computeIfPresent(zoneId,
                (k, v) -> (v - waterUsed <= 0) ? null : v - waterUsed);

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
    }

    public synchronized void droneRefilling(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " is refilling");
        DroneInfo info = droneRegistry.get(droneId);
        if (info != null) info.state = "REFILLING";
        refillingCount++;
        currentState = SchedulerState.REFILLING;
    }

    public synchronized void droneRefillComplete(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " refill complete — IDLE");
        DroneInfo info = droneRegistry.get(droneId);
        if (info != null) {
            info.state = "IDLE";
            info.waterRemaining = 15;
        }
        refillingCount = Math.max(0, refillingCount - 1);
        updateSchedulerState(0);
        tryDispatch();
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

    public void stop() {
        running = false;
        socket.close();
    }
}