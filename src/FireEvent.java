import java.awt.*;
/**
 * The FireEvent class is the object that the system uses to
 * communicate information about a fire. The fire information
 * updates in real-time as the fires are dealt with.
 *
 * Faults are no longer attached to fire events. They are
 * independent timed events in the input file that target a
 * specific drone by ID (see FaultType / fire_events.csv).
 *
 * @author Aryan Kumar Singh (101299776)
 * @author Kevin Abeykoon (101301971)
 */

public class FireEvent {
    public enum FireSeverity {
        LOW,
        MODERATE,
        HIGH
    }

    public static final int LOW_SEVERE_WATER    = 5;
    public static final int MEDIUM_SEVERE_WATER = 10;
    public static final int HIGH_SEVERE_WATER   = 15;

    private final int zoneId;
    private String eventType;
    private FireSeverity severity;
    private final int initialWaterRequired;
    private int waterRemaining;
    private int waterRequired;
    private int secondsFromStart;
    private long fireIncidentStartTime;

    public FireEvent(int zoneId, String eventType, String severity, int secondsFromStart) {
        this.zoneId           = zoneId;
        this.eventType        = eventType;
        this.secondsFromStart = secondsFromStart;

        switch (severity.toUpperCase()) {
            case "LOW":
                this.severity           = FireSeverity.LOW;
                this.initialWaterRequired = LOW_SEVERE_WATER;
                break;
            case "MODERATE":
                this.severity           = FireSeverity.MODERATE;
                this.initialWaterRequired = MEDIUM_SEVERE_WATER;
                break;
            case "HIGH":
                this.severity           = FireSeverity.HIGH;
                this.initialWaterRequired = HIGH_SEVERE_WATER;
                break;
            default:
                this.severity           = FireSeverity.LOW;
                this.initialWaterRequired = 0;
                break;
        }

        waterRemaining = initialWaterRequired;
    }

    /**
     * Copy constructor for creating a new FireEvent with a specific assigned water amount.
     */
    public FireEvent(FireEvent original, int assignedWater) {
        this.zoneId               = original.zoneId;
        this.eventType            = original.eventType;
        this.severity             = original.severity;
        this.initialWaterRequired = assignedWater;
        this.waterRemaining       = assignedWater;
        this.secondsFromStart     = original.secondsFromStart;
        this.fireIncidentStartTime = original.fireIncidentStartTime;
    }

    public int          getZoneId()           { return zoneId; }
    public String       getEventType()        { return eventType; }
    public FireSeverity getSeverity()         { return severity; }
    public int          getSecondsFromStart() { return secondsFromStart; }
    public int          getWaterRemaining()   { return waterRemaining; }
    public int          getWaterRequired()    { return waterRequired; }
    public long         getFireStartTime()    { return fireIncidentStartTime; }

    public void setWaterRequired(int waterRequired) {
        this.waterRequired = waterRequired;
    }

    public void waterUsed(int waterUsed) {
        waterRemaining -= waterUsed;
    }

    public boolean isExtinguished() {
        return waterRemaining <= 0;
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
