import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

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

    }

    /**
     * The Fire Subsystem uses this method to send
     * a fire event to the scheduler.
     *
     * @param event fire event
     */
    public synchronized void receiveFireEvent(FireEvent event){
        // MUST HAVE The firesubsystem convert some input like a text or csv file
        // and craft a corresponding FireEvent obj or datastructure that will be communicated to Scheduler

        System.out.println("Scheduler: Fire at zone " + event.zoneId() + ".");
        fireEventQueue.add(event);

        notifyAll(); // Wake up drone threads that are waiting
    }

    /**
     * Sorts the fire incident algorithm based on which fire has the highest priority at head
     */
    public synchronized Queue<FireEvent> sortFireIncidents(){
        return this.fireEventQueue;
    }

    /**
     * The drone calls this method to ask for work
     * @param droneId ID of drone
     */
    public synchronized void requestMission(int droneId){
        DroneSubsystem drone = droneStates.get(droneId);
        //Check drone water
        if (drone.getWaterRemaining() <= 0) {
            drone.setState(DroneSubsystem.DroneState.REFILLING);
            drone.moveDrone(100, 100);  //Assuming refilling base is 0, 0
        }
        else {
            //Check the drones associated mission
            //If no mission, put it in a wait queue until a mission is available for it
            //
            //
            // Im assuming the drone class will ensure that the scheduler is not invoked
            // when they are currently servicing a job but when they have completed a specific task?

            while (fireEventQueue.isEmpty()) {
                try {
                    wait(); // Drone waits for work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    //return null;
                }
            }

            // Returns the element at the head of the Queue. (not organized by scheduling algorithm yet)
            // MAYBE DO A SORT BEFORE WE POLL FOR THE FIRST QUEUE ELEMENT FOR DRONE

            if(drone.getDroneState() == DroneSubsystem.DroneState.IDLE){
                FireEvent mission = fireEventQueue.poll();
                //droneStates.put(droneId, new DroneState(i, this));// need to change to make it ONROUTE
                System.out.println("Scheduler: Drone " + droneId + " dispatched to zone " + mission.zoneId() + "."); // getter instead of public attribute of zones

                //return mission;
            }

            //return null;
            //
            //

            //If mission, check coords of drone
            //If not at site, tell it to move
            drone.moveDrone(1, 1);  //Placeholder Numbers

            //If at site, tell it to extinguish
            drone.extinguishFire(5); //Number based on associated event
        }

    }

    /**
     * The drone calls this method to indicate the drone has finished
     * its mission at the fire event location.
     *
     * @param droneId ID of drone
     * @param zoneId ID of zone
     */
    public synchronized void missionCompleted(int droneId, int zoneId){
        droneStates.put(droneId, new DroneSubsystem(droneId, this)); // need to change to make it IDLE
        System.out.println("Scheduler: Drone " + droneId + " completed mission at zone " + zoneId + ".");
        notifyFireSubsystem(zoneId);

        // Notify waiting drones looking for work
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


}