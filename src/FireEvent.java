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
                this.waterRequired = 10;
                break;

            case "MODERATE":
                this.severity = FireSeverity.MODERATE;
                this.waterRequired = 20;
                break;

            case "HIGH":
                this.severity = FireSeverity.HIGH;
                this.waterRequired = 30;
                break;

            default:
                break;
        }
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

    public long getFireStartTime() {
        return fireIncidentStartTime;
    }


    @Override
    public String toString() {
        return "Time: " + secondsFromStart + "s | Zone: " + zoneId + " | Type: " + eventType + " | Severity: " + severity;
    }
}
