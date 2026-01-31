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
    private FireEvent currentMission;
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
        this.clock = SimulationClock.getInstance();
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

    public void setMissionCoordinates(int xPos, int yPos){
        this.xGridLocation = xPos;
        this.yGridLocation = yPos;
    }

    public int getX() {return this.xGridLocation;}
    public int getY() {return this.yGridLocation;}
    public int getWaterRemaining() {return this.waterRemaining;}
    public DroneState getDroneState() {return this.droneState;}


    /**
     * Convert zone ID to coordinates (simplified)
     */
    private int getXFromZone(int zoneId) {
        // In iteration 1, the zones are hard coded areas, so return the center x coordinate of that zone.
        switch (zoneId){
            case 1:
                return 5;
            case 2:
                return 10;
            case 3:
                return 5;
            case 4:
                return 10;
            default:
                return 5;
        }
    }

    private int getYFromZone(int zoneId) {
        // In iteration 1, the zones are hard coded areas, so return the center x coordinate of that zone.
        switch (zoneId){
            case 1:
                return 5;
            case 2:
                return 10;
            case 3:
                return 5;
            case 4:
                return 10;
            default:
                return 5;
        }
    }

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
                Thread.sleep(clock.scaleSimulatedToReal(1000)); // Real 1 second = 1 simulation second

                System.out.printf("Drone %d: Moved to (%d, %d) at simulation time %d%n",
                        droneId, xGridLocation, yGridLocation, clock.getSimulationTimeSeconds());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        setState(DroneState.IDLE);
    }

    public int extinguishFire(int waterNeeded) throws InterruptedException {
        int waterUsed = Math.min(waterRemaining, waterNeeded);

        System.out.printf("Drone %d: Extinguishing fire (using %dL water)%n",
                droneId, waterUsed);

        // Simulate extinguishing time: Change to our statistics, just doing 1L per second INCORRECT
        Thread.sleep(clock.scaleSimulatedToReal(waterUsed * 1000L));

        System.out.printf("Drone %d: Done extinguishing%n", droneId);
        this.waterRemaining -= waterUsed;
        return waterUsed;
    }


    /**
     * Go to refill station and refill
     */
    private void goForRefill() throws InterruptedException {
        setState(DroneState.REFILLING);
        scheduler.droneRefilling(droneId);

        // Move to refill station (assuming at 0,0)
        moveDrone(0, 0);

        // Refill water
        refillWater();

        // Report refill complete
        scheduler.droneRefillComplete(droneId);
        setState(DroneState.IDLE);
    }

    public void refillWater() {
        System.out.printf("Drone %d: Refilling water tank\n", droneId);
        //Sleep for a bit
        System.out.printf("Drone %d: Done refilling water tank\n", droneId);
        this.waterRemaining = 15;
        setState(DroneState.IDLE);
    }

    public void setState(DroneState droneState) {this.droneState = droneState;}

    @Override
    public void run() {
        System.out.println("Drone " + droneId + " starting operations...");

        while (droneState != DroneState.DECOMISSIONED) {
            try {
                // 1. Request mission from scheduler, will wait until one is available
                FireEvent mission = scheduler.requestMission(droneId);

                if (mission != null) {
                    currentMission = mission;

                    // 2. Move to fire location (convert zoneId to coordinates)
                    int targetX = getXFromZone(mission.getZoneId());
                    int targetY = getYFromZone(mission.getZoneId());

                    setState(DroneState.ONROUTE);
                    System.out.printf("Drone %d: Moving to Zone %d at (%d, %d)%n",
                            droneId, mission.getZoneId(), targetX, targetY);

                    moveDrone(targetX, targetY);

                    // The drone then extinguishes the fire once reaching the location
                    setState(DroneState.EXTINGUISHING);
                    int waterNeeded = Math.min(mission.getWaterRequired(), 15); // Max drone capacity
                    int waterUsed = extinguishFire(waterNeeded);

                    // 4. Report completion
                    scheduler.missionCompleted(droneId, mission.getZoneId(), waterUsed);
                    currentMission = null;

                    // 5. Check if drone needs refill
                    if (waterRemaining <= 0) { // Empty tank
                        System.out.println("Drone " + droneId + " water low, going to refill");
                        goForRefill();
                    }
                } else {
                    // No mission available, wait a bit
                    Thread.sleep(clock.scaleSimulatedToReal(1000));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

}
