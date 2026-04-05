/**
 * Callback interface that decouples DroneMachine (pure state machine)
 * from DroneSubsystem (UDP layer).
 *
 * DroneMachine calls these methods whenever it reports to the Scheduler.
 * DroneSubsystem implements them and sends the UDP packet.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public interface DroneCallback {

    /** Called every cell movement so the Scheduler can track position. */
    void onLocationUpdate(int droneId, int x, int y, String state);

    /** Called every cell movement so the Scheduler can track battery life. */
    void onBatteryUpdate(int droneId, int battery);

    /** Called when the drone has finished extinguishing at a zone. */
    void onMissionCompleted(int droneId, int zoneId, int waterUsed);

    /**
     * Called when a new mission interrupts an existing one.
     * The abandoned mission is passed back so the Scheduler can re-queue it.
     */
    void onRescheduleFireEvent(int droneId, FireEvent abandonedMission);

    /** Called when the drone is about to move back to base to refill. */
    void onDroneRefilling(int droneId);

    /** Called when the drone has finished refilling and is IDLE again. */
    void onDroneRefillComplete(int droneId);

    /**
     * Hard fault only — nozzle jammed, drone is permanently shutting down.
     * Scheduler re-queues the active mission and sends DECOMMISSION|droneId.
     */
    void onHardFault(int droneId);

    /**
     * Called after a soft fault recovery so the Scheduler can mark the
     * drone IDLE and call tryDispatch() again.
     */
    void onDroneRecovered(int droneId);
}
