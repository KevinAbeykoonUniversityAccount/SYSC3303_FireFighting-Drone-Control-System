/**
 * Data transfer object representing a fire incident or drone request.
 * @param zoneId The ID of the zone
 * @param eventType The type of event (FIRE_DETECTED/DRONE_REQUEST)
 * @param severity The severity of the fire (HIGH/MODERATE/LOW)
 * @param secondsFromStart //The timestamp
 */
public record FireEvent(int zoneId, String eventType, String severity, int secondsFromStart) {

    @Override
    public String toString() {
        return "Time: " + secondsFromStart + "s | Zone: " + zoneId + " | Type: " + eventType + " | Severity: " + severity;
    }
}
