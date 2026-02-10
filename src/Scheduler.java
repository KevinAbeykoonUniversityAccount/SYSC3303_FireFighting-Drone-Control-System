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

        for(int i = 0; i < numberOfDrones; i++){
            droneStates.put(i, new DroneSubsystem(i, this));
        }

        this.clock = SimulationClock.getInstance();
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
     * Sorts the fire incident algorithm based on which fire has the highest priority at head
     *
     * @return Sorted queue with next job placed at front
     */
    /*public synchronized Queue<FireEvent> sortFireEventsQueue(){
        // Convert queue to list for sorting
        List<FireEvent> events = new ArrayList<>(fireEventQueue);

        // Sort using custom comparator
        events.sort(new Comparator<FireEvent>() {
            @Override
            public int compare(FireEvent e1, FireEvent e2) {
                // 1. Compare by severity (HIGH > MODERATE > LOW)
                int severityCompare = Integer.compare(
                        getSeverityPriority(e2.getSeverity()),
                        getSeverityPriority(e1.getSeverity())
                );

                if (severityCompare != 0) {
                    return severityCompare; // Different severity, sort by severity
                }

                // 2. Same severity: compare by waiting time (longer waiting = higher priority)
                long waitTime1 = clock.getSimulationTimeSeconds() - e1.getFireStartTime();
                long waitTime2 = clock.getSimulationTimeSeconds() - e2.getFireStartTime();

                return Long.compare(waitTime2, waitTime1); // Longer waiting first
            }

            private int getSeverityPriority(FireEvent.FireSeverity severity) {
                switch (severity) {
                    case HIGH: return 3;
                    case MODERATE: return 2;
                    case LOW: return 1;
                    default: return 0;
                }
            }
        });

        // Clear and re-add sorted events
        fireEventQueue.clear();
        fireEventQueue.addAll(events);

        return this.fireEventQueue;
    } */

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
            drone.moveDrone(100, 100);  //Assuming refilling base is 0, 0
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

        // Sort the queue before assigning missions
        // sortFireEventsQueue();
        // FireEvent mission = fireEventQueue.poll();

        int droneCapacity = drone.getWaterRemaining();
        int waterNeeded = mission.getWaterRemaining();

        if (droneCapacity <= 0) {
            return null;
        }

        // Calculate how much water this drone can handle
        int waterToAssign = Math.min(droneCapacity, waterNeeded);

        // Create a COPY of the event with REDUCED water requirement
        int remainingWater = waterNeeded - waterToAssign;

        if (remainingWater > 0) {
            // Reschedule event to the front of the appropriate queue
            mission.waterUsed(waterToAssign);
            rescheduleUnfinishedFireEvent(mission);

            System.out.printf("Scheduler [%s]: Drone %d assigned PARTIAL mission to Zone %d (Severity: %s, Water: %dL, Remaining: %dL)%n%n",
                    clock.getFormattedTime(), droneId, mission.getZoneId(),
                    mission.getSeverity(), waterToAssign, remainingWater);
        } else {
            // No water remaining, just poll the original
            System.out.printf("Scheduler [%s]: Drone %d assigned FULL mission to Zone %d (Severity: %s, Water: %dL)%n%n",
                    clock.getFormattedTime(), droneId, mission.getZoneId(),
                    mission.getSeverity(), waterToAssign);
        }


        // Create mission for drone with the water it will use
       /* FireEvent droneMission = new FireEvent(
                originalMission.getZoneId(),
                originalMission.getEventType(),
                originalMission.getSeverity().toString(),
                (int)originalMission.getFireStartTime()
        );
        droneMission.setWaterRequired(waterToAssign);*/

        return mission; // Return mission for drone
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

        /*for (FireEvent event : fireEventQueue) {
            if (event.getZoneId() == zoneId && event.getWaterRequired() > 0) {
                zoneStillHasFire = true;
                System.out.printf("Scheduler: Zone %d still needs %dL more water%n",
                        zoneId, event.getWaterRequired());
                break;
            }
        }*/

        if (!zoneStillHasFire) {
            notifyFireSubsystem(zoneId);
        }

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


}
