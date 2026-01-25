public class FireEvent {
    int zoneId;
    char severity;
    int secondsFromStart; //later can be changed to local time or something

    public FireEvent(int zoneId, char severity, int secondsFromStart){
        this.zoneId = zoneId;
        this.severity = severity;
        this.secondsFromStart = secondsFromStart;
    }

}
