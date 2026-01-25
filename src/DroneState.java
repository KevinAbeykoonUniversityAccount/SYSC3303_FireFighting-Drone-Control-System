public class DroneState {
    public enum state {
        IDLE,
        ONROUTE,
        FIRMWARE
    }
    int zoneId;
    int xGridLocation;
    int yGridLocation;

    state droneState;

    public DroneState(){
        xGridLocation = 0;
        yGridLocation = 0;
        zoneId = 0;
        droneState = state.IDLE;

    }
    public DroneState(int xGridLocation, int yGridLocation, int zoneId, DroneState.state droneState){
        this.xGridLocation = xGridLocation;
        this.yGridLocation = yGridLocation;
        this.zoneId = zoneId;
        this.droneState = droneState;

    }

}
