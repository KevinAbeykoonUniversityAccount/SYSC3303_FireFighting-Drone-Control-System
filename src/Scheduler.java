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
    private Queue<FireEvent> fireEventQueue; // Queue of fire events
    private Map<Integer, DroneSubsystem> droneStates;  // List of drones and their states

    private final SimulationClock clock;


    /**
     * Constructor to create a variable amount of
     * drones, starting in the IDLE state.
     * @param numberOfDrones number of drones to create
     */
    public Scheduler(int numberOfDrones){
        droneStates = new HashMap<>();
        fireEventQueue = new LinkedList<>();

        for(int i = 0; i < numberOfDrones; i++){
            droneStates.put(i, new DroneSubsystem(i, this));
        }

        this.clock = SimulationClock.getInstance();
    }

    /**
     * The Fire Subsystem uses this method to send
     * a fire event to the scheduler.
     *
     * @param event fire event
     */
    public synchronized void receiveFireEvent(FireEvent event){
        System.out.println("Scheduler: Fire at zone " + event.getZoneId() + ".\n");
        fireEventQueue.add(event);

        notifyAll(); // Wake up drone threads that are waiting
    }

    /**
     * Sorts the fire incident algorithm based on which fire has the highest priority at head
     */
    public synchronized Queue<FireEvent> sortFireEventsQueue(){
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
    }

    /**
     * The drone calls this method to ask for work
     * @param droneId ID of drone
     */
    public synchronized FireEvent requestMission(int droneId){
        DroneSubsystem drone = droneStates.get(droneId);
        // Check drone water
        if (drone.getWaterRemaining() <= 0) {
            drone.setState(DroneSubsystem.DroneState.REFILLING);
            drone.moveDrone(100, 100);  //Assuming refilling base is 0, 0
        }

        // Wait for work if queue is empty
        while (fireEventQueue.isEmpty()) {
            try {
                wait(); // Drone waits for work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        // Sort the queue before assigning missions
        sortFireEventsQueue();
        FireEvent mission = fireEventQueue.poll();

        if (mission != null) {
            System.out.printf("Scheduler: Drone %d assigned to Zone %d (Severity: %s, Water needed: %dL)%n \n",
                    droneId, mission.getZoneId(), mission.getSeverity(), mission.getWaterRequired());
        }

        return mission; // Drone handles the mission itself

    }

    /**
     * The drone calls this method to indicate the drone has finished
     * its mission at the fire event location.
     *
     * @param droneId ID of drone
     * @param zoneId ID of zone
     */
    public synchronized void missionCompleted(int droneId, int zoneId, int waterUsed){
        droneStates.put(droneId, new DroneSubsystem(droneId, this)); // need to change to make it IDLE
        System.out.printf("Scheduler: Drone %d completed mission at zone %d (used %dL water)%n",
                droneId, zoneId, waterUsed);
        notifyFireSubsystem(zoneId);

        // Notify waiting drones looking for work
        notifyAll();
    }


    /**
     * Drone reports going for refill
     */
    public synchronized void droneRefilling(int droneId) {
        System.out.println("Scheduler: Drone " + droneId + " going for water refill");
    }

    /**
     * Drone reports refill complete
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