import java.awt.*;
/**
 * The FireEvent class is the object that the sysytem uses
 * communicate information about the fire. The fire
 * information updates in real-time as the fires are dealt
 * with.
 *
 * @author Aryan Kumar Singh (101299776)
 * @author Kevin Abeykoon (101301971)
 */

public class FireEvent{
    public enum FireSeverity {
        LOW,
        MODERATE,
        HIGH
    }


    public static final int LOW_SEVERE_WATER = 5;
    public static final int MEDIUM_SEVERE_WATER = 10;
    public static final int HIGH_SEVERE_WATER = 15;

    private final int zoneId;
    private String eventType;
    private FireSeverity severity;
    private final int initialWaterRequired;
    private int waterRemaining;
    private int waterRequired;
    private int secondsFromStart;
    private long fireIncidentStartTime; // Real-time when simulation started
    private FaultType faultType = FaultType.NONE;


    public FireEvent(int zoneId, String eventType, String severity, int secondsFromStart, FaultType faultType) {
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.secondsFromStart = secondsFromStart;
        this.faultType = faultType;

        switch(severity.toUpperCase()){
            case "LOW":
                this.severity = FireSeverity.LOW;
                this.initialWaterRequired = LOW_SEVERE_WATER;
                break;

            case "MODERATE":
                this.severity = FireSeverity.MODERATE;
                this.initialWaterRequired = MEDIUM_SEVERE_WATER;
                break;

            case "HIGH":
                this.severity = FireSeverity.HIGH;
                this.initialWaterRequired = HIGH_SEVERE_WATER;
                break;

            default:
                initialWaterRequired = 0;
                break;
        }

        waterRemaining = initialWaterRequired;
    }

    /**
     * Copy constructor for creating a new FireEvent with a specific assigned water amount.
     */
    public FireEvent(FireEvent original, int assignedWater) {
        this.zoneId = original.zoneId;
        this.eventType = original.eventType;
        this.severity = original.severity;
        this.initialWaterRequired = assignedWater;   // The drone's portion
        this.waterRemaining = assignedWater;
        this.secondsFromStart = original.secondsFromStart;
        this.fireIncidentStartTime = original.fireIncidentStartTime;
        this.faultType            = original.faultType;
    }

    public int getZoneId() {
        return zoneId;
    }

    public String getEventType() {
        return eventType;
    }

    public FireSeverity getSeverity() {
        return severity;
    }

    public FaultType getFaultType() { return faultType; }

    /** Clears the fault so a rescheduled event does not re-trigger a fault on the next drone. */
    public void clearFault() { this.faultType = FaultType.NONE; }

    public void setWaterRequired(int waterRequired) {
        this.waterRequired = waterRequired;
    }

    public int getWaterRequired() {
        return waterRequired;
    }

    public int getSecondsFromStart() {
        return secondsFromStart;
    }

    public int getWaterRemaining() { return waterRemaining;
    }

    public void waterUsed(int waterUsed) {
        waterRemaining -= waterUsed;
    }

    public boolean isExtinguished() {
        return waterRemaining <= 0;
    }

    public long getFireStartTime() {
        return fireIncidentStartTime;
    }


    @Override
    public String toString() {
        return "Time: " + secondsFromStart
                + "s | Zone: " + zoneId
                + " | Type: " + eventType
                + " | Severity: " + severity
                + " | Water Needed: " + waterRemaining;
    }
}
