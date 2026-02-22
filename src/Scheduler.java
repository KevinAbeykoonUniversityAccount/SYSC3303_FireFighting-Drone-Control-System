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
public class Scheduler {
    private Deque<FireEvent> lowFireEventQueue; // Queue of fire events
    private Deque<FireEvent> moderateFireEventQueue; // Queue of fire events
    private Deque<FireEvent> highFireEventQueue; // Queue of fire events
    private Map<Integer, DroneSubsystem> droneStates;  // List of drones and their states

    private Map<Integer, Integer> assignedWaterPerZone = new HashMap<>();

    private final SimulationClock clock;


    /**
     * Constructor to create a variable amount of
     * drones, starting in the IDLE state.
     * @param numberOfDrones number of drones to create
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
     *
     * @param drone registered to this fire event scheduling system
     */
    public synchronized void registerDrone(DroneSubsystem drone) {
        droneStates.put(drone.getDroneId(), drone);
        System.out.println("Scheduler: Drone " + drone.getDroneId() + " registered");
    }



    /**
     * The Fire Subsystem uses this method to send
     * a fire event to the scheduler.
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

        notifyAll(); // Wake up drone threads that are waiting
    }

    /**
     * Returns the fire incident with the current highest priority to assign to a drone
     *
     * @return fire with highest priority
     */
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

    /**
     * Adds fire incidents who were not fully extingusihed back into their respective
     * severity queue.
     *
     * @return fire event that needs to be readded to queue
     */
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
     */
    public synchronized FireEvent requestMission(int droneId) throws InterruptedException {
        DroneSubsystem drone = droneStates.get(droneId);

        // Check drone water
        if (drone.getWaterRemaining() <= 0) {
            drone.setState(DroneSubsystem.DroneState.REFILLING);
            // Drone will handle refilling; we don't assign mission now
            return null;
        }

        // Wait for work if queue is empty
        FireEvent mission = retrieveHighestPriorityEvent();
        while (mission == null) {
            try {
                wait(); // Drone waits for work
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
            // Full assignment – use the original event (it will be removed from queue)
            droneMission = mission;
            System.out.printf("Scheduler [%s]: Drone %d assigned FULL mission to Zone %d (Severity: %s, Water: %dL)%n%n",
                    clock.getFormattedTime(), droneId, mission.getZoneId(),
                    mission.getSeverity(), waterToAssign);
        }

        return droneMission;
    }

    /**
     * The drone calls this method to indicate the drone has finished
     * its mission at the fire event location.
     *
     * @param droneId ID of drone
     * @param zoneId ID of zone
     */
    public synchronized void missionCompleted(int droneId, int zoneId, int waterUsed) {
        // Update drone state to IDLE (don't recreate the drone)
        DroneSubsystem drone = droneStates.get(droneId);
        if (drone != null) {
            drone.setState(DroneSubsystem.DroneState.IDLE);
        }

        System.out.printf("Scheduler [%s]: Drone %d completed mission at zone %d (used %dL water)%n",
                clock.getFormattedTime(), droneId, zoneId, waterUsed);

        // Check if this zone still has fire events in queue
        boolean zoneStillHasFire = false;

        if (!zoneStillHasFire) {
            notifyFireSubsystem(zoneId);
        }

        // Remove or reduce the assigned water for this zone
        assignedWaterPerZone.computeIfPresent(zoneId, (k, v) -> (v - waterUsed <= 0) ? null : v - waterUsed);

        // Notify waiting drones looking for work
        notifyAll();
    }

    /**
     * Drone reports it needs to refill
     *
     * @param droneId The id of the drone that has gone to refill
     */
    public synchronized void droneRefilling(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " going for water refill");
    }

    /**
     * Drone reports refill complete
     *
     * @param droneId The id of the drone that completed the refill
     */
    public synchronized void droneRefillComplete(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " refill complete, ready for missions");
        notifyAll(); // Wake up if it was waiting
    }


    /**
     * Alerts the fire subsystem that the fire has been extinguished.
     *
     * @param zoneId ID of zone
     */
    private void notifyFireSubsystem(int zoneId){
        System.out.println("Scheduler: Fire extinguished at zone " + zoneId + ".");
    }



    /**
     * Returns an unmodifiable view of the drone states map.
     */
    public synchronized Map<Integer, DroneSubsystem> getDroneStates() {
        return Collections.unmodifiableMap(droneStates);
    }

    /**
     * Returns the number of active fires by severity: [high, moderate, low]
     */
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

    /**
     * Returns a map from zone ID to the total remaining water needed
     */
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
}
