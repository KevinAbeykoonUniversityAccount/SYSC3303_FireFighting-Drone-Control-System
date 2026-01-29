public class DroneState extends Thread {
    public enum State {
        IDLE,
        ONROUTE,
        EXTINGUISHING,
        REFILLING,
        FAULTED,
        DECOMISSIONED
    }

    private int droneId;
    private int zoneId;
    private int xGridLocation;
    private int yGridLocation;
    private int waterRemaining;  // in Litres
    private Scheduler scheduler;
    private State droneState;

    public DroneState(int droneId, Scheduler scheduler){
        this.droneId = droneId;
        xGridLocation = 0;
        yGridLocation = 0;
        zoneId = 0;
        droneState = State.IDLE;
        waterRemaining = 15;
        this.scheduler = scheduler;
    }

    public DroneState(int droneId, int xGridLocation, int yGridLocation, int zoneId, State droneState, Scheduler scheduler){
        this.droneId = droneId;
        this.xGridLocation = xGridLocation;
        this.yGridLocation = yGridLocation;
        this.zoneId = zoneId;
        this.droneState = droneState;
        this.waterRemaining = 15;
        this.scheduler = scheduler;
    }

    public int getX() {return this.xGridLocation;}
    public int getY() {return this.yGridLocation;}
    public int getWaterRemaining() {return this.waterRemaining;}
    public State getDroneState() {return this.droneState;}

    public void moveDrone(int targetX, int targetY) {
        while (!((targetX == xGridLocation) && (targetY == yGridLocation))) {
            if (targetX > xGridLocation) xGridLocation++;
            if (targetX < xGridLocation) xGridLocation--;
            if (targetY > yGridLocation) yGridLocation++;
            if (targetY < yGridLocation) yGridLocation--;
            //Update Location on map
            //Sleep for a bit
        }
        setState(State.IDLE);
    }

    public int extinguishFire(int waterNeeded) {
        int waterUsed = waterRemaining > waterNeeded ? waterNeeded : waterRemaining;
        
        System.out.printf("Drone %d: Extinguishing Fire\n", droneId);
        //Sleep for a bit
        System.out.printf("Drone %d: Done Extinguishing\n", droneId);
        setState(State.IDLE);
        this.waterRemaining -= waterUsed;
        return waterUsed;
    }

    public void refillWater() {
        System.out.printf("Drone %d: Refilling water tank\n", droneId);
        //Sleep for a bit
        System.out.printf("Drone %d: Done refilling water tank\n", droneId);
        this.waterRemaining = 15;
        setState(State.IDLE);
    }

    public void setState(State droneState) {this.droneState = droneState;}

    public void run() {
        while (droneState != State.DECOMISSIONED) {
            scheduler.requestMission(droneId);
            scheduler.missionCompleted(droneId, zoneId);
        }
    }
}
