import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * The DroneSubsystem class runs on its own thread and represents a single
 * drone. When the drone is IDLE, it requests a mission from the Scheduler.
 * After waiting and receiving a mission, it moves to the location of the 
 * fire and starts to extinguish it. If the drone has exhausted its water 
 * supply, then it returns to base to refill. Otherwise it waits for its
 * next instruction.
 *
 * @author Abdullah Khan (101305235)
 * @author Aryan Kumar Singh (101299776)
 * @author Kevin Abeykoon (101301971)
 */

public class DroneSubsystem extends Thread {
    public enum droneEvents {
        NEW_MISSION,
        ARRIVED,
        COMPLETED_MISSION,
        REFUELED,
        FAULT,
        DECOMMISSION
    }

    public enum DroneState {
        IDLE,
        ONROUTE,
        EXTINGUISHING,
        RETURNING,
        REFILLING,
        FAULTED,
        DECOMMISSIONED
    }


    // Realistic drone parameters from Iteration 0
    private static final double NOZZLE_OPEN_TIME = 0.75; // seconds
    private static final double NOZZLE_CLOSE_TIME = 0.75; // seconds
    private static final double FLOW_RATE = 3.77; // litres per second
    private static final int MAX_CAPACITY = 15; // litres

    private int droneId;
    private DroneState droneState;
    private FireEvent incomingMission;
    private FireEvent currentMission;
    //private int zoneId;
    private int xGridLocation;
    private int yGridLocation;
    private int targetX;
    private int targetY;
    private int waterRemaining;  // in Litres

    private boolean hasTarget = false;
    private boolean missionInterrupted = false;

    private Scheduler scheduler;
    private SimulationClock clock; // centralized clock

    public DroneSubsystem(int droneId, Scheduler scheduler) {
        this.droneId = droneId;
        this.droneState = DroneState.IDLE;
        this.incomingMission = null;
        this.currentMission = null;
        this.xGridLocation = 0;
        this.yGridLocation = 0;
        this.waterRemaining = MAX_CAPACITY;
        this.scheduler = scheduler;
    }

//        public Drone(int droneId, Scheduler scheduler) {
//            this.droneId = droneId;
//            xGridLocation = 0;
//            yGridLocation = 0;
//            zoneId = 0;
//            droneState = DroneState.IDLE;
//            waterRemaining = MAX_CAPACITY;
//            this.scheduler = scheduler;
//            this.clock = SimulationClock.getInstance();
//        }
//
//        public Drone(int droneId, int xGridLocation, int yGridLocation, int zoneId, DroneState droneState, Scheduler scheduler) {
//            this.droneId = droneId;
//            this.xGridLocation = xGridLocation;
//            this.yGridLocation = yGridLocation;
//            this.zoneId = zoneId;
//            this.droneState = droneState;
//            this.waterRemaining = MAX_CAPACITY;
//            this.scheduler = scheduler;
//        }

    /**
     * Return the current x coordinate of the drone relative to the grid map
     *
     * @return current x position of drone
     */
    public int getX() {
        return this.xGridLocation;
    }

    /**
     * Return the current y coordinate of the drone relative to the grid map
     *
     * @return current y position of drone
     */
    public int getY() {
        return this.yGridLocation;
    }

    public int getTargetX() {
        return this.targetX;
    }

    public int getTargetY() {
        return this.targetY;
    }

    public boolean hasTarget() {
        return this.hasTarget;
    }

    /**
     * Return the water remaining in the drone's tank
     *
     * @return current water volume (litres) in drone tank
     */
    public int getWaterRemaining() {
        return this.waterRemaining;
    }

    /**
     * Return the current state of the drone
     *
     * @return state of the drone
     */
    public DroneState getDroneState() {
        return this.droneState;
    }

    /**
     * Return the drone's number identification
     *
     * @return id of drone
     */
    public Integer getDroneId() {
        return this.droneId;
    }

    /**
     * Get the current mission of the drone
     *
     * @return The fire the drone is assigned to go to
     */
    public FireEvent getCurrentMission() {
        return currentMission;
    }

    public void setMissionCoordinates(int xPos, int yPos) {
        this.targetX = xPos;
        this.targetY = yPos;
        this.hasTarget = true;
        System.out.println("New coord: " + xPos + " " + yPos);
    }

    public synchronized void incomingMission(FireEvent incomingMission) {
        this.incomingMission = incomingMission;
        if (droneState == DroneState.ONROUTE || droneState == DroneState.RETURNING) {
            missionInterrupted = true;
        }
        notifyAll();
    }

    public void updateMission() {
        this.currentMission = this.incomingMission;
        this.incomingMission = null;
    }

    /**
     * Returns the x coordinate of the fire incident based off zone
     *
     * @param zoneId The zone of target incident
     * @return The x coordinate of the fire incident
     */
    private int getXFromZone(int zoneId) {
        // In iteration 1, the zones are hard coded areas, so return the center x coordinate of that zone.
        switch (zoneId) {
            case 1:
                return 7;    // Cells 0-14, center ~cell 7
            case 2:
                return 22;   // Cells 15-29, center ~cell 22
            case 3:
                return 7;    // Cells 0-14, center ~cell 7
            case 4:
                return 22;   // Cells 15-29, center ~cell 22
            default:
                return 7;

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
        switch (zoneId) {
            case 1:
                return 7;    // Center of Zone 1 (cells 0-14)
            case 2:
                return 7;    // Center of Zone 2 (cells 0-14)
            case 3:
                return 22;   // Center of Zone 3 (cells 15-29)
            case 4:
                return 22;   // Center of Zone 4 (cells 15-29)
            default:
                return 7;
        }
    }

    /**
     * Moves the drone to the specified location
     *
     * @throws InterruptedException
     */
    public void moveDrone() throws InterruptedException {
        if (targetX == xGridLocation && targetY == yGridLocation) {
            this.hasTarget = false;
            System.out.printf("Drone %d: Arrived at destination (%d, %d)%n",
                    droneId, targetX, targetY);
            handleEvent(droneEvents.ARRIVED);
        }
        else if (missionInterrupted) {
            missionInterrupted = false;
            return;
        } else {
            // Move one cell at a time
            if (targetX > xGridLocation) xGridLocation++;
            if (targetX < xGridLocation) xGridLocation--;
            if (targetY > yGridLocation) yGridLocation++;
            if (targetY < yGridLocation) yGridLocation--;

            // Sleep for 1 simulation second for this movement
            sleep(1000);
            //clock.sleepForSimulationSeconds(1);

            moveDrone();
        }
        //System.out.printf("Drone %d: Moved to (%d, %d) at simulation time %s%n", droneId, xGridLocation, yGridLocation, clock.getFormattedTime());
    }


    /**
     *
     * @param waterNeeded The water needed for the fire incident assigned to drone
     * @return The water utilized in extinguishing the fire.
     * @throws InterruptedException
     */
    public int extinguishFire(int waterNeeded) throws InterruptedException {
        int waterToDrop = Math.min(waterRemaining, waterNeeded);

        if (waterToDrop <= 0) {
            System.out.printf("Drone %d: No water to drop%n", droneId);
            return 0;
        }

        System.out.printf("Drone %d: Starting extinguishing sequence for %dL water%n",
                droneId, waterToDrop);
        System.out.printf("Drone %d: Opening nozzle (%.2fs)%n", droneId, NOZZLE_OPEN_TIME);

        // Step 1: Open nozzle
        sleep((long) (NOZZLE_OPEN_TIME));
        //clock.sleepForSimulationSeconds((long) (NOZZLE_OPEN_TIME));

        // Step 2: Drop water at flow rate
        double flowTime = waterToDrop / FLOW_RATE;
        System.out.printf("Drone %d: Dropping water at %.2f L/s for %.2fs%n",
                droneId, FLOW_RATE, flowTime);

        // Simulate dropping water - we can break this into smaller chunks for realism
        long flowTimeMillis = (long) (flowTime * 1000);
        long startTime = System.currentTimeMillis();        // Simulate the water dropping process
        sleep((long) (flowTime));
        //clock.sleepForSimulationSeconds((long) (flowTime));

        // Step 3: Close nozzle
        System.out.printf("Drone %d: Closing nozzle (%.2fs)%n", droneId, NOZZLE_CLOSE_TIME);
        sleep((long) (NOZZLE_CLOSE_TIME));
        //clock.sleepForSimulationSeconds((long) (NOZZLE_CLOSE_TIME));

        double totalTime = NOZZLE_OPEN_TIME + flowTime + NOZZLE_CLOSE_TIME;
        System.out.printf("Drone %d: Extinguishing complete (total time: %.2fs)%n",
                droneId, totalTime);

        this.waterRemaining -= waterToDrop;
        return waterToDrop;
    }

//    /**
//     * Move drone to origin point (0,0) to refill on empty tank
//     *
//     * @throws InterruptedException
//     */
//    private void goForRefill() throws InterruptedException {
//        setState(DroneState.REFILLING);
//        scheduler.droneRefilling(droneId);
//
//        // Move to refill station (assuming at 0,0)
//        moveDrone(0, 0);
//
//        // Refill water
//        refillWater();
//
//        // Report refill complete
//        scheduler.droneRefillComplete(droneId);
//        setState(DroneState.IDLE);
//    }

    /**
     * Refill the drone with water up to maximum capacity
     *
     * @throws InterruptedException
     */
    public void refillWater() throws InterruptedException {
        System.out.printf("Drone %d: Refilling water tank%n",
                droneId);

        // Refilling takes 5 simulation seconds
        sleep(5000);
        //clock.sleepForSimulationSeconds(5);

        System.out.printf("Drone %d: Done refilling water tank%n",
                droneId);
        this.waterRemaining = MAX_CAPACITY;
        //setState(DroneState.IDLE);
    }

    /**
     * Set the state of the drone
     *
     * @param droneState The new current state the drone is in
     */
    public void setState(DroneState droneState) {
        this.droneState = droneState;
    }

    public void handleEvent(droneEvents event) {
        switch (event) {
            case NEW_MISSION:
                if (droneState == DroneState.ONROUTE) {
                    //missionInterrupted = true;
                    scheduler.rescheduleUnfinishedFireEvent(currentMission);
                }

                setState(DroneState.ONROUTE);

                updateMission();
                int[] missionCoord = {getXFromZone(currentMission.getZoneId()), getYFromZone(currentMission.getZoneId())};

                try {
                    System.out.printf("Drone %d: Starting movement to (%d, %d)%n",
                            droneId, missionCoord[0], missionCoord[1]);
                    setMissionCoordinates(missionCoord[0], missionCoord[1]);
                    moveDrone();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (incomingMission != null) {
                    handleEvent(droneEvents.NEW_MISSION);
                }
                break;

            case ARRIVED:
                System.out.println(droneState);
                switch (droneState) {
                    case ONROUTE:
                        setState(DroneState.EXTINGUISHING);

                        try {
                            extinguishFire(currentMission.getWaterRemaining());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        handleEvent(droneEvents.COMPLETED_MISSION);
                        break;

                    case RETURNING:
                        setState(DroneState.REFILLING);

                        try {
                            refillWater();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        handleEvent(droneEvents.REFUELED);
                        break;

                    default:
                        break;
                }
                break;

            case COMPLETED_MISSION:
                setState(DroneState.RETURNING);
                scheduler.missionCompleted(droneId, currentMission.getZoneId());
                currentMission = null;

                try {
                    System.out.printf("Drone %d: Starting movement to (%d, %d)%n",
                            droneId, 0, 0);
                    setMissionCoordinates(0, 0);
                    moveDrone();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (incomingMission != null) {
                    handleEvent(droneEvents.NEW_MISSION);
                }
                break;

            case REFUELED:
                setState(DroneState.IDLE);
                scheduler.droneRefillComplete(droneId);
                break;

            case FAULT:
                break;

            case DECOMMISSION:
                droneState = DroneState.DECOMMISSIONED;
                break;

            default:
                break;
        }
    }

//    public void performAction() {
//        try {
//            switch (droneState) {
//                case IDLE:
//                    // 1. Request mission from scheduler, will wait until one is available
//                    FireEvent mission = scheduler.requestMission(droneId);
//
//                    if (mission != null) {
//                        this.currentMission = mission;
//
//                        setState(DroneState.ONROUTE);
//                    }
//                    break;
//
//                case ONROUTE:
//                    // 2. Move to fire location (convert zoneId to coordinates)
//                    if (currentMission != null) {
//                        int targetX = getXFromZone(currentMission.getZoneId());
//                        int targetY = getYFromZone(currentMission.getZoneId());
//
//                        System.out.printf("Drone %d: Moving to Zone %d at (%d, %d)%n",
//                                droneId, currentMission.getZoneId(), targetX, targetY);
//
//                        moveDrone(targetX, targetY);
//
//                        // After moving, transition to extinguishing
//                        setState(DroneState.EXTINGUISHING);
//                    } else {
//                        // No mission, go back to idle
//                        setState(DroneState.IDLE);
//                    }
//                    break;
//
//                case EXTINGUISHING:
//                    // Drop agent on the fire
//                    int waterNeeded = currentMission.getWaterRemaining();
//                    int waterUsed = extinguishFire(waterNeeded);
//
//                    // Report completion to scheduler
//                    scheduler.missionCompleted(droneId, currentMission.getZoneId(), waterUsed);
//                    currentMission = null;
//
//                    // Decide next state based on remaining water
//                    if (waterRemaining <= 0) {
//                        droneState = DroneState.REFILLING;
//                    } else {
//                        droneState = DroneState.IDLE;
//                    }
//                    break;
//
//                case REFILLING:
//                    // Go to base and refill (method sets state to IDLE when done)
//                    goForRefill();
//                    break;
//
//                case FAULTED:
//                    // Placeholder for fault handling (to be expanded in later iterations)
//                    System.out.printf("Drone %d is faulted. Waiting for recovery...%n", droneId);
//                    Thread.sleep(1000);
//                    // For now, just go back to idle (actual fault logic will be added later)
//                    droneState = DroneState.IDLE;
//                    break;
//
//                case DECOMMISSIONED:
//                    System.out.printf("Drone %d is decommissioned.%n", droneId);
//                    break;
//            }
//
//            Thread.sleep(10);
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }

    @Override
    public void run() {
        System.out.println("Drone " + droneId + " starting operations...");

        while (droneState != DroneState.DECOMMISSIONED) {
            synchronized (this) {
                while (incomingMission == null && droneState == DroneState.IDLE) {
                    try { wait(); } catch (InterruptedException e) { return; }
                }
            }
            if (incomingMission != null) {
                handleEvent(droneEvents.NEW_MISSION); // runs on drone's own thread
                incomingMission = null;
            }
        }
        System.out.println("Drone " + droneId + " decomissioned...");
    }
}
