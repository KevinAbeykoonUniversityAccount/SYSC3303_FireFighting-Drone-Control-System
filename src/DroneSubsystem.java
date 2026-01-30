public class DroneSubsystem extends Thread {
    public enum DroneState {
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
    private DroneState droneState;

    private SimulationClock clock; // centralized clock

    public DroneSubsystem(int droneId, Scheduler scheduler){
        this.droneId = droneId;
        xGridLocation = 0;
        yGridLocation = 0;
        zoneId = 0;
        droneState = DroneState.IDLE;
        waterRemaining = 15;
        this.scheduler = scheduler;
    }

    public DroneSubsystem(int droneId, int xGridLocation, int yGridLocation, int zoneId, DroneState droneState, Scheduler scheduler){
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
    public DroneState getDroneState() {return this.droneState;}


    // Update moveDrone to use simulation time
    public void moveDrone(int targetX, int targetY) {
        System.out.printf("Drone %d: Starting movement at simulation time %d%n",
                droneId, clock.getSimulationTimeSeconds());

        while (!((targetX == xGridLocation) && (targetY == yGridLocation))) {
            if (targetX > xGridLocation) xGridLocation++;
            if (targetX < xGridLocation) xGridLocation--;
            if (targetY > yGridLocation) yGridLocation++;
            if (targetY < yGridLocation) yGridLocation--;

            // Simulate time passing for movement
            try {
                // Each grid movement takes 1 second of simulation time
                Thread.sleep(1000); // Real 1 second = 1 simulation second

                System.out.printf("Drone %d: Moved to (%d, %d) at simulation time %d%n",
                        droneId, xGridLocation, yGridLocation, clock.getSimulationTimeSeconds());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        setState(DroneState.IDLE);
    }

    public int extinguishFire(int waterNeeded) {
        int waterUsed = waterRemaining > waterNeeded ? waterNeeded : waterRemaining;
        
        System.out.printf("Drone %d: Extinguishing Fire\n", droneId);
        //Sleep for a bit
        System.out.printf("Drone %d: Done Extinguishing\n", droneId);
        setState(DroneState.IDLE);
        this.waterRemaining -= waterUsed;
        return waterUsed;
    }

    public void refillWater() {
        System.out.printf("Drone %d: Refilling water tank\n", droneId);
        //Sleep for a bit
        System.out.printf("Drone %d: Done refilling water tank\n", droneId);
        this.waterRemaining = 15;
        setState(DroneState.IDLE);
    }

    public void setState(DroneState droneState) {this.droneState = droneState;}

    public void run() {
        while (droneState != DroneState.DECOMISSIONED) {
            scheduler.requestMission(droneId);
            scheduler.missionCompleted(droneId, zoneId);
        }
    }
}
