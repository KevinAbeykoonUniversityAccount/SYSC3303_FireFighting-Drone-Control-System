import java.util.*;

/**
 * The Scheduler class is the central program of the fire fighting
 * control system. It is responsible for receiving fire events,
 * scheduling drones to put out fires, and reporting back that
 * the fire has been put out.
 *
 * @author Kevin Abeykoon (101301971)
 * @author Aryan Kumar Singh (101299776)
 */
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
public class Scheduler {
    public enum SchedulerState {
        IDLE,               // No active fires, all drones idle
        DISPATCHING,        // Fires in queue, selecting drones
        MONITORING,         // At least one drone on a mission
        REFILLING,          // One or more drones are refilling
        FAULT_HANDLING      // Handling a drone fault
    }

    private Deque<FireEvent> lowFireEventQueue;
    private Deque<FireEvent> moderateFireEventQueue;
    private Deque<FireEvent> highFireEventQueue;
    private Map<Integer, DroneSubsystem> droneStates;
    private Map<Integer, Integer> assignedWaterPerZone = new HashMap<>();

    private final SimulationClock clock;

    // State machine fields
    private SchedulerState currentState = SchedulerState.IDLE;
    private int activeMissionCount = 0;          // drones currently on a mission
    private int refillingCount = 0;               // drones currently refilling

    /**
     * Constructor to create a variable amount of
     * drones, starting in the IDLE state.
     * @param numberOfDrones number of drones to create (unused, drones are registered separately)
     */
    public Scheduler(int numberOfDrones){
        droneStates = new HashMap<>();
        lowFireEventQueue = new LinkedList<>();
        moderateFireEventQueue = new LinkedList<>();
        highFireEventQueue = new LinkedList<>();
        this.clock = SimulationClock.getInstance();
    }

    /**
     * Register a drone with the scheduler
     */
    public synchronized void registerDrone(DroneSubsystem drone) {
        droneStates.put(drone.getDroneId(), drone);
        System.out.println("Scheduler: Drone " + drone.getDroneId() + " registered");
    }

    /**
     * The Fire Subsystem uses this method to send a fire event to the scheduler.
     *
     * @param event fire event sent by fire incident subsystem
     */
    public synchronized void receiveFireEvent(FireEvent event){
        System.out.println("Scheduler: Fire at zone " + event.getZoneId() + ".\n");
        if(event.getSeverity() == FireEvent.FireSeverity.HIGH){
            highFireEventQueue.add(event);
        }
        else if(event.getSeverity() == FireEvent.FireSeverity.MODERATE){
            moderateFireEventQueue.add(event);
        }
        else{
            lowFireEventQueue.add(event);
        }

        // If we were idle, now we have work to do
        if (currentState == SchedulerState.IDLE) {
            currentState = SchedulerState.DISPATCHING;
        }

        notifyAll(); // Wake up drone threads that are waiting
    }

    private FireEvent retrieveHighestPriorityEvent(){
        if(!highFireEventQueue.isEmpty()){
            return highFireEventQueue.pollFirst();
        }
        else if(!moderateFireEventQueue.isEmpty()){
            return moderateFireEventQueue.pollFirst();
        }
        else if(!lowFireEventQueue.isEmpty()){
            return lowFireEventQueue.pollFirst();
        }
        return null;
    }

    public synchronized void rescheduleUnfinishedFireEvent(FireEvent event){
        if(event.getSeverity() == FireEvent.FireSeverity.HIGH){
            highFireEventQueue.addFirst(event);
        }
        else if(event.getSeverity() == FireEvent.FireSeverity.MODERATE){
            moderateFireEventQueue.addFirst(event);
        }
        else{
            lowFireEventQueue.addFirst(event);
        }
        notifyAll();
    }

    /**
     * The drone waits for a mission to combat a fire incident if available,
     * then the scheduler assigns one from the queue
     *
     * @param droneId ID of drone
     * @return a FireEvent for the drone to execute, or null if none available
     */
    public synchronized FireEvent requestMission(int droneId) throws InterruptedException {
        DroneSubsystem drone = droneStates.get(droneId);

        // Check drone water
        if (drone.getWaterRemaining() <= 0) {
            drone.setState(DroneSubsystem.DroneState.REFILLING);
            refillingCount++;
            currentState = SchedulerState.REFILLING;
            return null;
        }

        // Wait for work if queue is empty
        FireEvent mission = retrieveHighestPriorityEvent();
        while (mission == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            mission = retrieveHighestPriorityEvent();
        }

        int droneCapacity = drone.getWaterRemaining();
        int waterNeeded = mission.getWaterRemaining();
        int waterToAssign = Math.min(droneCapacity, waterNeeded);
        int remainingWater = waterNeeded - waterToAssign;

        // Add assigned water to per‑zone tracking
        assignedWaterPerZone.merge(mission.getZoneId(), waterToAssign, Integer::sum);

        FireEvent droneMission;
        if (remainingWater > 0) {
            // Create a copy for the drone with exactly the assigned water
            droneMission = new FireEvent(mission, waterToAssign);
            // Reduce original event and put it back at the front of the queue
            mission.waterUsed(waterToAssign);
            rescheduleUnfinishedFireEvent(mission);
            System.out.printf("Scheduler [%s]: Drone %d assigned PARTIAL mission to Zone %d (Severity: %s, Water: %dL, Remaining: %dL)%n%n",
                    clock.getFormattedTime(), droneId, mission.getZoneId(),
                    mission.getSeverity(), waterToAssign, remainingWater);
        } else {
            // Full assignment – use the original event
            droneMission = mission;
            System.out.printf("Scheduler [%s]: Drone %d assigned FULL mission to Zone %d (Severity: %s, Water: %dL)%n%n",
                    clock.getFormattedTime(), droneId, mission.getZoneId(),
                    mission.getSeverity(), waterToAssign);
        }

        activeMissionCount++;

        // Update scheduler state based on remaining work
        if (remainingWater > 0) {
            // There is still a fire in the queue, so we remain in DISPATCHING
            currentState = SchedulerState.DISPATCHING;
        } else {
            // No pending fires in the queue? Check queues
            if (highFireEventQueue.isEmpty() && moderateFireEventQueue.isEmpty() && lowFireEventQueue.isEmpty()) {
                // All fires have been assigned, now monitoring active missions
                currentState = SchedulerState.MONITORING;
            } else {
                currentState = SchedulerState.DISPATCHING;
            }
        }

        return droneMission;
    }

    /**
     * The drone calls this method to indicate the drone has finished
     * its mission at the fire event location.
     *
     * @param droneId ID of drone
     * @param zoneId ID of zone
     * @param waterUsed amount of water used
     */
    public synchronized void missionCompleted(int droneId, int zoneId, int waterUsed) {
        DroneSubsystem drone = droneStates.get(droneId);
        if (drone != null) {
            drone.setState(DroneSubsystem.DroneState.IDLE);
        }

        System.out.printf("Scheduler [%s]: Drone %d completed mission at zone %d (used %dL water)%n",
                clock.getFormattedTime(), droneId, zoneId, waterUsed);

        // Remove or reduce the assigned water for this zone
        assignedWaterPerZone.computeIfPresent(zoneId, (k, v) -> (v - waterUsed <= 0) ? null : v - waterUsed);

        activeMissionCount--;

        // Determine new state
        boolean anyQueued = !highFireEventQueue.isEmpty() || !moderateFireEventQueue.isEmpty() || !lowFireEventQueue.isEmpty();

        if (activeMissionCount == 0) {
            if (anyQueued) {
                currentState = SchedulerState.DISPATCHING;
            } else {
                currentState = SchedulerState.IDLE;
            }
        } else {
            // Still active missions
            currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.MONITORING;
        }

        notifyAll();
    }

    /**
     * Drone reports it needs to refill
     *
     * @param droneId The id of the drone that has gone to refill
     */
    public synchronized void droneRefilling(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " going for water refill");
        refillingCount++;
        currentState = SchedulerState.REFILLING;
    }

    /**
     * Drone reports refill complete
     *
     * @param droneId The id of the drone that completed the refill
     */
    public synchronized void droneRefillComplete(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " refill complete, ready for missions");
        refillingCount--;
        if (refillingCount == 0) {
            // If no more refilling drones, go back to appropriate state
            boolean anyQueued = !highFireEventQueue.isEmpty() || !moderateFireEventQueue.isEmpty() || !lowFireEventQueue.isEmpty();
            if (activeMissionCount == 0) {
                currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.IDLE;
            } else {
                currentState = anyQueued ? SchedulerState.DISPATCHING : SchedulerState.MONITORING;
            }
        }
        notifyAll();
    }

    /**
     * Alerts the fire subsystem that the fire has been extinguished.
     *
     * @param zoneId ID of zone
     */
    private void notifyFireSubsystem(int zoneId){
        System.out.println("Scheduler: Fire extinguished at zone " + zoneId + ".");
    }

    // --- Getters for GUI ---

    public synchronized Map<Integer, DroneSubsystem> getDroneStates() {
        return Collections.unmodifiableMap(droneStates);
    }

    public synchronized int[] getFireCountsBySeverity() {
        int high = highFireEventQueue != null ? highFireEventQueue.size() : 0;
        int moderate = moderateFireEventQueue != null ? moderateFireEventQueue.size() : 0;
        int low = lowFireEventQueue != null ? lowFireEventQueue.size() : 0;

        // Add drones currently on a mission
        for (DroneSubsystem drone : droneStates.values()) {
            FireEvent mission = drone.getCurrentMission();
            if (mission != null) {
                switch (mission.getSeverity()) {
                    case HIGH:    high++; break;
                    case MODERATE: moderate++; break;
                    case LOW:     low++; break;
                }
            }
        }
        return new int[]{high, moderate, low};
    }

    public synchronized Map<Integer, Integer> getActiveFiresPerZone() {
        Map<Integer, Integer> total = new HashMap<>();
        // Add water from queues
        for (FireEvent e : highFireEventQueue)
            total.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (FireEvent e : moderateFireEventQueue)
            total.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        for (FireEvent e : lowFireEventQueue)
            total.merge(e.getZoneId(), e.getWaterRemaining(), Integer::sum);
        // Add water currently assigned to drones
        for (Map.Entry<Integer, Integer> e : assignedWaterPerZone.entrySet()) {
            total.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return total;
    }

    // Optional: get current scheduler state for debugging
    public synchronized SchedulerState getCurrentState() {
        return currentState;
    }
}
