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
 */
public class Scheduler {
    private Queue<FireEvent> fireEventQueue; // Queue of fire events
    private Map<Integer, DroneState> droneStates;  // List of drones and their states

    /**
     * Constructor to create a variable amount of
     * drones, starting in the IDLE state.
     * @param numberOfDrones number of drones to create
     */
    public Scheduler(int numberOfDrones){
        droneStates = new HashMap<>();
        fireEventQueue = new LinkedList<>();

        for(int i = 0; i < numberOfDrones; i++){
            droneStates.put(i, new DroneState());
        }

    }

    /**
     * The Fire Subsystem uses this method to send
     * a fire event to the scheduler.
     *
     * @param event fire event
     */
    public synchronized void receiveFireEvent(FireEvent event){
        System.out.println("Scheduler: Fire at zone " + event.zoneId + ".");
        fireEventQueue.add(event);

    }

    /**
     * The drone calls this method to ask for work
      * @param droneId ID of drone
     */
    public synchronized FireEvent requestMission(int droneId){
        if(droneStates.get(droneId).droneState == DroneState.state.IDLE && !fireEventQueue.isEmpty()){
            FireEvent mission = fireEventQueue.poll();
            droneStates.put(droneId, new DroneState());// need to change to make it ONROUTE
            System.out.println("Scheduler: Drone " + droneId + " dispatched to zone " + mission.zoneId + ".");
            return mission;
        }

        return null;
    }

    /**
     * The drone calls this method to indicate the drone has finished
     * its mission at the fire event location.
     *
     * @param droneId ID of drone
     * @param zoneId ID of zone
     */
    public synchronized void missionCompleted(int droneId, int zoneId){
        droneStates.put(droneId, new DroneState()); // need to change to make it IDLE
        System.out.println("Scheduler: Drone " + droneId + " completed mission at zone " + zoneId + ".");
        notifyFireSubsystem(zoneId);
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
