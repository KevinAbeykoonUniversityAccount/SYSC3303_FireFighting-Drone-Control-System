public class DroneState {
    public enum State {
        IDLE,
        ONROUTE,
        FIRMWARE

        // EXTINGUISHING, RETURNING would be good states as mentioned in the pdf
    }

    private int droneId;
    private int zoneId;
    private int xGridLocation;
    private int yGridLocation;
    private int waterRemaining;  // in Litres

    State droneState;

    public DroneState(){
        xGridLocation = 0;
        yGridLocation = 0;
        zoneId = 0;
        droneState = State.IDLE;
        waterRemaining = 15;
    }

    public DroneState(int xGridLocation, int yGridLocation, int zoneId, State droneState){
        this.xGridLocation = xGridLocation;
        this.yGridLocation = yGridLocation;
        this.zoneId = zoneId;
        this.droneState = droneState;
        this.waterRemaining = 15;
    }

}
