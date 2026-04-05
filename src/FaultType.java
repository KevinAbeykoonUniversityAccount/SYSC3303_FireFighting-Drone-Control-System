/**
 * Fault types that can be injected via the input file.
 *
 * NONE         — normal mission, no fault
 * DRONE_STUCK  — soft fault: drone freezes mid-action, recovers after a delay,
 *                then continues. Mission is NOT rescheduled.
 * NOZZLE_FAULT — hard fault: nozzle jams, drone is permanently decommissioned,
 *                active mission is re-queued for another drone.
 *
 * Input file format (fault rows target a specific drone by ID):
 *   Time,Event Type,Drone ID,Severity
 *   01:25:00,DRONE_STUCK,1,
 *   01:45:00,NOZZLE_FAULT,2,
 *
 * When the FireIncidentSubsystem reads a fault row it sends an injectFaultEvent
 * message to the Scheduler, which forwards INJECT_FAULT to the target drone.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public enum FaultType {
    NONE,
    DRONE_STUCK,
    NOZZLE_FAULT;

    public static FaultType from(String s) {
        if (s == null || s.isBlank()) return NONE;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
