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
    private DroneState droneState;

    private FireEvent currentMission;
    private int zoneId;
    private int xGridLocation;
    private int yGridLocation;
    private int waterRemaining;  // in Litres

    private Scheduler scheduler;
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
     * Returns the x coordinate of the fire incident based off zone
     *
     * @param zoneId The zone of target incident
     * @return The x coordinate of the fire incident
     */
    private int getXFromZone(int zoneId) {
        // In iteration 1, the zones are hard coded areas, so return the center x coordinate of that zone.
        switch (zoneId){
            case 1: return 7;    // Cells 0-14, center ~cell 7
            case 2: return 22;   // Cells 15-29, center ~cell 22
            case 3: return 7;    // Cells 0-14, center ~cell 7
            case 4: return 22;   // Cells 15-29, center ~cell 22
            default: return 7;

        }
    }

    /**
     * Returns the y coordinate of the fire incident based off zone
     *
     * @param zoneId The zone of target fire incident
     * @return The y coordinate of the fire incident
     */
    private int getYFromZone(int zoneId) {
        // In iteration 1, the zones are hard coded areas, so return the center x coordinate of that zone.
        switch (zoneId){
            case 1: return 7;    // Center of Zone 1 (cells 0-14)
            case 2: return 7;    // Center of Zone 2 (cells 0-14)
            case 3: return 22;   // Center of Zone 3 (cells 15-29)
            case 4: return 22;   // Center of Zone 4 (cells 15-29)
            default: return 7;
        }
    }

    /**
     * Moves the drone to the specified location
     *
     * @param targetX X coordinate of the destination
     * @param targetY Y coordinate of the destination
     * @throws InterruptedException
     */
    public void moveDrone(int targetX, int targetY) throws InterruptedException {
        long startTime = clock.getSimulationTimeSeconds();
        System.out.printf("Drone %d: Starting movement to (%d, %d) at simulation time %s%n",
                droneId, targetX, targetY, clock.getFormattedTime());

        setState(DroneState.ONROUTE);

        while (!((targetX == xGridLocation) && (targetY == yGridLocation))) {
            // Move one cell at a time
            if (targetX > xGridLocation) xGridLocation++;
            if (targetX < xGridLocation) xGridLocation--;
            if (targetY > yGridLocation) yGridLocation++;
            if (targetY < yGridLocation) yGridLocation--;

            // Sleep for 1 simulation second for this movement
            clock.sleepForSimulationSeconds(1);

            //System.out.printf("Drone %d: Moved to (%d, %d) at simulation time %s%n", droneId, xGridLocation, yGridLocation, clock.getFormattedTime());
        }

        System.out.printf("Drone %d: Arrived at destination (%d, %d) at simulation time %s%n",
                droneId, targetX, targetY, clock.getFormattedTime());
        setState(DroneState.IDLE);
    }

    /**
     *
     * @param waterNeeded The water needed for the fire incident assigned to drone
     * @return The water utilized in extinguishing the fire.
     * @throws InterruptedException
     */
    public int extinguishFire(int waterNeeded) throws InterruptedException {
        int waterUsed = Math.min(waterRemaining, waterNeeded);

        System.out.printf("Drone %d: Starting extinguishing (using %dL water) at simulation time %s%n",
                droneId, waterUsed, clock.getFormattedTime());

        setState(DroneState.EXTINGUISHING);

        // Sleep for waterUsed simulation seconds
        clock.sleepForSimulationSeconds(waterUsed);

        System.out.printf("Drone %d: Done extinguishing at simulation time %s%n",
                droneId, clock.getFormattedTime());

        this.waterRemaining -= waterUsed;
        setState(DroneState.IDLE);
        return waterUsed;
    }

    /**
     * Move drone to origin point (0,0) to refill on empty tank
     *
     * @throws InterruptedException
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

    /**
     * Refill the drone with water up to maximum capacity
     *
     * @throws InterruptedException
     */
    public void refillWater() throws InterruptedException {
        System.out.printf("Drone %d: Refilling water tank at simulation time %s%n",
                droneId, clock.getFormattedTime());

        // Refilling takes 5 simulation seconds
        clock.sleepForSimulationSeconds(5);

        System.out.printf("Drone %d: Done refilling water tank at simulation time %s%n",
                droneId, clock.getFormattedTime());
        this.waterRemaining = 15;
        setState(DroneState.IDLE);
    }

    /**
     * Set the state of the drone
     *
     * @param droneState The new current state the drone is in
     */
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
                    Thread.sleep(1000);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
