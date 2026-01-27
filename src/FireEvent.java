public class FireEvent {
    int zoneId;
    char severity;  // String?
    int secondsFromStart; //later can be changed to local time or something
    // boolean extinguished ? what should happen when notifying the drone subsystem

    public FireEvent(int zoneId, char severity, int secondsFromStart){
        this.zoneId = zoneId;
        this.severity = severity;
        this.secondsFromStart = secondsFromStart;
    }

}
