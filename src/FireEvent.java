import java.awt.*;

public class FireEvent{
    public enum FireSeverity {
        LOW,
        MODERATE,
        HIGH
    }

    private final int zoneId;
    private String eventType;
    private FireSeverity severity;
    private final int initialWaterRequired;
    private int waterRemaining;
    private int waterRequired;
    private int secondsFromStart;
    private long fireIncidentStartTime; // Real-time when simulation started


    public FireEvent(int zoneId, String eventType, String severity, int secondsFromStart) {
        this.zoneId = zoneId;
        this.eventType = eventType;
        this.secondsFromStart = secondsFromStart;

        switch(severity.toUpperCase()){
            case "LOW":
                this.severity = FireSeverity.LOW;
                this.initialWaterRequired = 10;
                break;

            case "MODERATE":
                this.severity = FireSeverity.MODERATE;
                this.initialWaterRequired = 20;
                break;

            case "HIGH":
                this.severity = FireSeverity.HIGH;
                this.initialWaterRequired = 30;
                break;

            default:
                initialWaterRequired = 0;
                break;
        }

        waterRemaining = initialWaterRequired;
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
