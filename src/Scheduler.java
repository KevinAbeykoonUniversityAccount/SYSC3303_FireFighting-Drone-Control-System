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
public class Scheduler implements Runnable {

    public static final int PORT = 6000;
    private static final int BUFFER_SIZE = 1024;

    public enum SchedulerState {
        IDLE, DISPATCHING, MONITORING, REFILLING, FAULT_HANDLING
    }

    private Deque<FireEvent> lowFireEventQueue;
    private Deque<FireEvent> moderateFireEventQueue;
    private Deque<FireEvent> highFireEventQueue;
    private Map<Integer, DroneInfo> droneStates;
    private Map<Integer, Integer> assignedWaterPerZone = new HashMap<>();
    private Map<Integer, Zone> zones = new HashMap<>();

    private final SimulationClock clock;

    private SchedulerState currentState = SchedulerState.IDLE;
    private int activeMissionCount = 0;
    private int refillingCount = 0;

    private DatagramSocket socket;
    private volatile boolean running = true;

    public Scheduler() throws SocketException {
        droneStates = new HashMap<>();
        lowFireEventQueue = new LinkedList<>();
        moderateFireEventQueue = new LinkedList<>();
        highFireEventQueue = new LinkedList<>();
        this.clock = SimulationClock.getInstance();
        this.socket = new DatagramSocket(PORT);
        System.out.println("Scheduler: Listening on UDP port " + PORT);

        zones.put(1, new Zone(1, 0, 14, 0, 14 ));
        zones.put(2,new Zone(2, 15, 29, 0, 14 ));
        zones.put(3,new Zone(3, 0, 14, 15, 29 ));
        zones.put(4,new Zone(4, 15, 29, 15, 29 ));
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

            case "requestMission": {
                // requestMission|droneId|x|y|water
                int droneId = Integer.parseInt(parts[1]);
                int x       = Integer.parseInt(parts[2]);
                int y       = Integer.parseInt(parts[3]);
                int water   = Integer.parseInt(parts[4]);

                // Register or refresh drone info
                droneStates.computeIfAbsent(droneId,
                        id -> new DroneInfo(id, x, y, water, addr, port));
                DroneInfo info = droneStates.get(droneId);
                info.x = x; info.y = y; info.waterRemaining = water;
                info.address = addr; info.port = port;

                String response = buildMissionResponse(droneId);
                sendReply(response, addr, port);
                break;
            }

            case "missionCompleted": {
                // missionCompleted|droneId|zoneId|waterUsed
                missionCompleted(
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]));
                break;
            }

            case "droneRefilling": {
                droneRefilling(Integer.parseInt(parts[1]));
                break;
            }

            case "droneRefillComplete": {
                droneRefillComplete(Integer.parseInt(parts[1]));
                break;
            }

            case "locationUpdate": {
                // locationUpdate|droneId|x|y|state
                int droneId = Integer.parseInt(parts[1]);
                DroneInfo info = droneStates.get(droneId);
                if (info != null) {
                    info.x = Integer.parseInt(parts[2]);
                    info.y = Integer.parseInt(parts[3]);
                    info.state = parts[4];
                }
                break;
            }

            case "getTime": {
                sendReply(String.valueOf(clock.getSimulationTimeSeconds()), addr, port);
                break;
            }

            default:
                System.err.println("Scheduler: unknown message: " + parts[0]);
        }
    }


    /**
     * public void assignMission() {
     *         FireEvent mission = retrieveHighestPriorityEvent();
     *
     *         if (mission != null) {
     *             //.out.println("Assigning");
     *             Zone missionZone = zones.get(mission.getZoneId());
     *             int ZX = missionZone.getCenterX();
     *             int ZY = missionZone.getCenterY();
     *
     *             int targetDrone = getClosestDrone(getDronesWithWater(getDronesBelowSeverity(droneStates, mission.getSeverity()), mission.getWaterRequired()), ZX, ZY);
     *
     *             if (targetDrone == -1) {
     *                 //System.out.println("No Drone Available");
     *                 rescheduleUnfinishedFireEvent(mission);
     *             } else {
     *                 System.out.println("Assigned to drone " + targetDrone);
     *                 DroneSubsystem drone = droneStates.get(targetDrone);
     *                 drone.incomingMission(mission);
     *                 //drone.handleEvent(DroneSubsystem.droneEvents.NEW_MISSION);
     *             }
     *         }
     *     }
     */

    /**
     * Builds the reply string for a requestMission call.
     * Contains the same logic that was previously inside requestMission().
     */
    private String buildMissionResponse(int droneId) {
        DroneInfo drone = droneStates.get(droneId);

        if (drone.waterRemaining <= 0) {
            return "GOTO_REFILL";
        }

        FireEvent mission = retrieveHighestPriorityEvent();
        if (mission == null) {
            return "WAIT";
        }

        int waterToAssign  = Math.min(drone.waterRemaining, mission.getWaterRemaining());
        int remainingWater = mission.getWaterRemaining() - waterToAssign;

        assignedWaterPerZone.merge(mission.getZoneId(), waterToAssign, Integer::sum);

        FireEvent droneMission;
        if (remainingWater > 0) {
            droneMission = new FireEvent(mission, waterToAssign);
            mission.waterUsed(waterToAssign);
            rescheduleUnfinishedFireEvent(mission);
            System.out.printf("Scheduler [%s]: Drone %d assigned PARTIAL mission to Zone %d " +
                            "(Severity: %s, Water: %dL, Remaining: %dL)%n%n",
                    clock.getFormattedTime(), droneId, mission.getZoneId(),
                    mission.getSeverity(), waterToAssign, remainingWater);
        } else {
            droneMission = mission;
            System.out.printf("Scheduler [%s]: Drone %d assigned FULL mission to Zone %d " +
                            "(Severity: %s, Water: %dL)%n%n",
                    clock.getFormattedTime(), droneId, mission.getZoneId(),
                    mission.getSeverity(), waterToAssign);
        }
    }

    public Map<Integer, DroneSubsystem> getDronesBelowSeverity(Map<Integer, DroneSubsystem> drones, FireEvent.FireSeverity severity) {
        Map<Integer, DroneSubsystem> dronesWithLowSeverity = new HashMap<>();

        for (DroneSubsystem drone : drones.values()) {
            if (drone.getCurrentMission() == null) {
                dronesWithLowSeverity.put(drone.getDroneId(), drone);
            }
            else {
                switch (severity) {
                    case HIGH:
                        if (drone.getCurrentMission().getSeverity() == FireEvent.FireSeverity.HIGH)
                            dronesWithLowSeverity.put(drone.getDroneId(), drone);
                    case MODERATE:
                        if (drone.getCurrentMission().getSeverity() == FireEvent.FireSeverity.MODERATE)
                            dronesWithLowSeverity.put(drone.getDroneId(), drone);
                    case LOW:
                        if (drone.getCurrentMission().getSeverity() == FireEvent.FireSeverity.LOW)
                            dronesWithLowSeverity.put(drone.getDroneId(), drone);
                }
            }
        }

        return dronesWithLowSeverity;
    }

    public Map<Integer, DroneSubsystem> getDronesWithWater(Map<Integer, DroneSubsystem> drones, int water) {
        Map<Integer, DroneSubsystem> dronesWithWater = new HashMap<>();

        for (DroneSubsystem drone : drones.values()) {
            if (drone.getWaterRemaining() >= water) dronesWithWater.put(drone.getDroneId(), drone);
        }

        return dronesWithWater;
    }

        activeMissionCount++;
        updateSchedulerState(remainingWater);

        // MISSION|zoneId|eventType|severity|waterAssigned|secondsFromStart
        return "MISSION|"
                + droneMission.getZoneId()         + "|"
                + droneMission.getEventType()       + "|"
                + droneMission.getSeverity().name() + "|"
                + droneMission.getWaterRemaining()  + "|"
                + droneMission.getSecondsFromStart();
    }

    private void sendReply(String message, InetAddress addr, int port) throws Exception {
        byte[] data = message.getBytes();
        socket.send(new DatagramPacket(data, data.length, addr, port));
    }

    public void stop() {
        running = false;
        socket.close();
    }
    public int getClosestDrone(Map<Integer, DroneSubsystem> drones, int targetX, int targetY) {
        int closestDrone = -1;
        for (DroneSubsystem drone : drones.values()) {
            int x2 = drone.getX();
            int y2 = drone.getY();

    // =========================================================
    // All methods below are UNCHANGED from Iteration 2
    // =========================================================

    public synchronized void receiveFireEvent(FireEvent event) {
        System.out.println("Scheduler: Fire at zone " + event.getZoneId() + ".\n");
        if (event.getSeverity() == FireEvent.FireSeverity.HIGH) {
            highFireEventQueue.add(event);
        } else if (event.getSeverity() == FireEvent.FireSeverity.MODERATE) {
            moderateFireEventQueue.add(event);
        } else {
            lowFireEventQueue.add(event);
        }
        if (currentState == SchedulerState.IDLE) {
            currentState = SchedulerState.DISPATCHING;
        }
    }

    private FireEvent retrieveHighestPriorityEvent() {
        if (!highFireEventQueue.isEmpty())     return highFireEventQueue.pollFirst();
        if (!moderateFireEventQueue.isEmpty()) return moderateFireEventQueue.pollFirst();
        if (!lowFireEventQueue.isEmpty())      return lowFireEventQueue.pollFirst();
        return null;
    }

    public synchronized void rescheduleUnfinishedFireEvent(FireEvent event) {
        if (event.getSeverity() == FireEvent.FireSeverity.HIGH) {
            highFireEventQueue.addFirst(event);
        } else if (event.getSeverity() == FireEvent.FireSeverity.MODERATE) {
            moderateFireEventQueue.addFirst(event);
        } else {
            lowFireEventQueue.addFirst(event);
            double potentialDistance = distance(x2, y2, targetX, targetY);

            if (!drone.hasTarget() || distance(x2, y2, drone.getTargetX(), drone.getTargetY()) > potentialDistance) {
                if (closestDrone == -1) closestDrone = drone.getDroneId();

                int x1 = drones.get(closestDrone).getX();
                int y1 = drones.get(closestDrone).getY();

                if (distance(x1, y1, targetX, targetY) > potentialDistance) {
                    closestDrone = drone.getDroneId();
                }
            }
        }

        return closestDrone;
    }

    public double distance(int x1, int y1, int x2, int y2) {
        return Math.sqrt((x2 - x1)^2 + (y2 - y1)^2);
    }

//    /**
//     * The drone waits for a mission to combat a fire incident if available,
//     * then the scheduler assigns one from the queue
//     *
//     * @param droneId ID of drone
//     * @return a FireEvent for the drone to execute, or null if none available
//     */
//    public synchronized FireEvent requestMission(int droneId) throws InterruptedException {
//        DroneSubsystem drone = droneStates.get(droneId);
//
//        // Check drone water
//        if (drone.getWaterRemaining() <= 0) {
//            drone.setState(DroneSubsystem.DroneState.REFILLING);
//            refillingCount++;
//            currentState = SchedulerState.REFILLING;
//            return null;
//        }
//
//        // Wait for work if queue is empty
//        FireEvent mission = retrieveHighestPriorityEvent();
//        while (mission == null) {
//            try {
//                wait();
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                return null;
//            }
//            mission = retrieveHighestPriorityEvent();
//        }
//
//        int droneCapacity = drone.getWaterRemaining();
//        int waterNeeded = mission.getWaterRemaining();
//        int waterToAssign = Math.min(droneCapacity, waterNeeded);
//        int remainingWater = waterNeeded - waterToAssign;
//
//        // Add assigned water to per‑zone tracking
//        assignedWaterPerZone.merge(mission.getZoneId(), waterToAssign, Integer::sum);
//
//        FireEvent droneMission;
//        if (remainingWater > 0) {
//            // Create a copy for the drone with exactly the assigned water
//            droneMission = new FireEvent(mission, waterToAssign);
//            // Reduce original event and put it back at the front of the queue
//            mission.waterUsed(waterToAssign);
//            rescheduleUnfinishedFireEvent(mission);
//            System.out.printf("Scheduler [%s]: Drone %d assigned PARTIAL mission to Zone %d (Severity: %s, Water: %dL, Remaining: %dL)%n%n",
//                    clock.getFormattedTime(), droneId, mission.getZoneId(),
//                    mission.getSeverity(), waterToAssign, remainingWater);
//        } else {
//            // Full assignment – use the original event
//            droneMission = mission;
//            System.out.printf("Scheduler [%s]: Drone %d assigned FULL mission to Zone %d (Severity: %s, Water: %dL)%n%n",
//                    clock.getFormattedTime(), droneId, mission.getZoneId(),
//                    mission.getSeverity(), waterToAssign);
//        }
//
//        activeMissionCount++;
//
//        // Update scheduler state based on remaining work
//        if (remainingWater > 0) {
//            // There is still a fire in the queue, so we remain in DISPATCHING
//            currentState = SchedulerState.DISPATCHING;
//        } else {
//            // No pending fires in the queue? Check queues
//            if (highFireEventQueue.isEmpty() && moderateFireEventQueue.isEmpty() && lowFireEventQueue.isEmpty()) {
//                // All fires have been assigned, now monitoring active missions
//                currentState = SchedulerState.MONITORING;
//            } else {
//                currentState = SchedulerState.DISPATCHING;
//            }
//        }
//
//        return droneMission;
//    }


    /**
     * The drone calls this method to indicate the drone has finished
     * its mission at the fire event location.
     *
     * @param droneId ID of drone
     * @param zoneId ID of zone
     */
    public synchronized void missionCompleted(int droneId, int zoneId) {
        DroneSubsystem drone = droneStates.get(droneId);

        System.out.printf("Scheduler [%s]: Drone %d completed mission at zone %d%n",
                clock.getFormattedTime(), droneId, zoneId);

        assignedWaterPerZone.computeIfPresent(zoneId,
                (k, v) -> (v - waterUsed <= 0) ? null : v - waterUsed);
        // Remove or reduce the assigned water for this zone
       // assignedWaterPerZone.computeIfPresent(zoneId, (k, v) -> (v - waterUsed <= 0) ? null : v - waterUsed);

        activeMissionCount--;

        boolean anyQueued = !highFireEventQueue.isEmpty()
                || !moderateFireEventQueue.isEmpty()
                || !lowFireEventQueue.isEmpty();
        if (activeMissionCount == 0) {
            currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.IDLE;
        } else {
            currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.MONITORING;
        }
    }

    public synchronized void droneRefilling(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " going for water refill");
        refillingCount++;
        currentState = SchedulerState.REFILLING;
    }

    public synchronized void droneRefillComplete(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " refill complete, ready for missions");
        DroneInfo drone = droneStates.get(droneId);
        if (drone != null) { drone.state = "IDLE"; drone.waterRemaining = 15; }
        refillingCount--;
        if (refillingCount == 0) {
            boolean anyQueued = !highFireEventQueue.isEmpty()
                    || !moderateFireEventQueue.isEmpty()
                    || !lowFireEventQueue.isEmpty();
            if (activeMissionCount == 0) {
                currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.IDLE;
            } else {
                currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.MONITORING;
            }
        }
    }

    private void updateSchedulerState(int remainingWater) {
        if (remainingWater > 0) {
            currentState = SchedulerState.DISPATCHING;
        } else {
            boolean anyQueued = !highFireEventQueue.isEmpty()
                    || !moderateFireEventQueue.isEmpty()
                    || !lowFireEventQueue.isEmpty();
            currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.MONITORING;
        }
    }

    public synchronized SchedulerState getCurrentState() { return currentState; }

    public synchronized Map<Integer, DroneInfo> getDroneStates() {
        return Collections.unmodifiableMap(droneStates);
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
        for (Map.Entry<Integer, Integer> e : assignedWaterPerZone.entrySet())
            total.merge(e.getKey(), e.getValue(), Integer::sum);
        return total;
    }

    // Get current scheduler state for debugging
    public synchronized SchedulerState getCurrentState() {
        return currentState;
    }

    public void run() {
        while (true) {
            synchronized (this) {
                while (highFireEventQueue.isEmpty() && moderateFireEventQueue.isEmpty() && lowFireEventQueue.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            assignMission();
        }
    }
}