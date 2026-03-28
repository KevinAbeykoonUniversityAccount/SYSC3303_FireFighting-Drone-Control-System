/**
 * Fault types that can be injected via the input file.
 *
 * NONE         — normal mission, no fault injected
 * DRONE_STUCK  — soft fault: drone freezes mid-flight, recovers after a delay,
 *                mission is re-queued for another drone
 * NOZZLE_FAULT — hard fault: nozzle jams during extinguishing, drone is
 *                permanently decommissioned, mission is re-queued
 *
 * Input file column (5th):
 *   01:05:00, 3, FIRE, HIGH, NONE
 *   01:07:30, 1, FIRE, LOW,  DRONE_STUCK
 *   01:10:00, 2, FIRE, HIGH, NOZZLE_FAULT
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