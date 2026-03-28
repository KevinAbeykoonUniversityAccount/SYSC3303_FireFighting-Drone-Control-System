/**
 * Represents a single physical drone.
 *
 * Seperated from the subsystem, it represents a machine with no knowledge of UDP or
 * networking. When it needs to report something to the Scheduler (position
 * update, mission complete, etc.) it calls the DroneCallback that was
 * injected at construction time. DroneSubsystem implements that interface
 * and translates the calls into UDP packets.
 *
 * @author Abdullah Khan   (101305235)
 * @author Aryan Kumar Singh (101299776)
 * @author Kevin Abeykoon  (101301971)
 */
public class DroneMachine extends Thread {
    public enum droneEvents {
        NEW_MISSION,
        ARRIVED,
        COMPLETED_MISSION,
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

    private static final double NOZZLE_OPEN_TIME  = 0.75;  // seconds
    private static final double NOZZLE_CLOSE_TIME = 0.75;  // seconds
    private static final double FLOW_RATE         = 3.77;  // litres / second
    private static final int    MAX_CAPACITY      = 15;    // litres

    private final int droneId;
    private DroneState droneState;

    private volatile FireEvent incomingMission;
    private FireEvent          currentMission;

    private int     xGridLocation;
    private int     yGridLocation;
    private int     targetX;
    private int     targetY;
    private boolean hasTarget          = false;
    private boolean missionInterrupted = false;
    private int     waterRemaining;

    private final DroneCallback callback;
    private final SimulationClock clock;


    /**
     * @param droneId  unique drone identifier
     * @param callback receives outbound notifications (implemented by DroneSubsystem)
     */
    public DroneMachine(int droneId, DroneCallback callback) {
        this.droneId         = droneId;
        this.callback        = callback;
        this.xGridLocation   = 0;
        this.yGridLocation   = 0;
        this.droneState      = DroneState.IDLE;
        this.waterRemaining  = MAX_CAPACITY;
        this.clock           = SimulationClock.getInstance();
        this.incomingMission = null;
        this.currentMission  = null;
    }

    // Getters and Setters
    public int        getDroneId()        { return droneId; }
    public int        getX()              { return xGridLocation; }
    public int        getY()              { return yGridLocation; }
    public int        getWaterRemaining() { return waterRemaining; }
    public DroneState getDroneState()     { return droneState; }
    public FireEvent  getCurrentMission() { return currentMission; }

    public synchronized void setState(DroneState s) {
        this.droneState = s;
    }


    /**
     * DroneSubsystem calls this when the Scheduler pushes a new assignment.
     * Wakes the run() loop if the drone is waiting idle.
     * Sets missionInterrupted if the drone is currently moving.
     */
    public synchronized void receiveMissionPush(FireEvent event) {
        this.incomingMission = event;
        if (droneState == DroneState.ONROUTE) {
            missionInterrupted = true;
        }
        notifyAll();
    }

    private void updateMission() {
        this.currentMission  = this.incomingMission;
        this.incomingMission = null;
    }

    // ============ Zone coordinate helpers ============

    /**
     * Returns the x coordinate of the fire incident based off zone
     *
     * @param zoneId The zone of target incident
     * @return The x coordinate of the fire incident
     */
    private int getXFromZone(int zoneId) {
        switch (zoneId) {
            case 1: return 7;
            case 2: return 22;
            case 3: return 7;
            case 4: return 22;
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
        switch (zoneId) {
            case 1: return 7;
            case 2: return 7;
            case 3: return 22;
            case 4: return 22;
            default: return 7;
        }
    }


    public void setMissionCoordinates(int x, int y) {
        this.targetX   = x;
        this.targetY   = y;
        this.hasTarget = true;
    }

    /**
     * Moves one cell per simulated second toward the current target.
     * Reports position via callback after every step.
     * Returns early (without calling handleEvent) if missionInterrupted.
     * Calls handleEvent(ARRIVED) on reaching the destination.
     */
    public void moveDrone() throws InterruptedException {
        while (xGridLocation != targetX || yGridLocation != targetY) {
            if (missionInterrupted) {
                missionInterrupted = false;
                return;
            }

            if      (targetX > xGridLocation) xGridLocation++;
            else if (targetX < xGridLocation) xGridLocation--;
            if      (targetY > yGridLocation) yGridLocation++;
            else if (targetY < yGridLocation) yGridLocation--;

            sleep(1000);

            // No UDP here — ask DroneSubsystem to send it
            callback.onLocationUpdate(droneId, xGridLocation, yGridLocation,
                    droneState.name());
        }

        hasTarget = false;
        System.out.printf("Drone %d: Arrived at (%d, %d)%n",
                droneId, xGridLocation, yGridLocation);
        handleEvent(droneEvents.ARRIVED);
    }


    public int extinguishFire(int waterNeeded) throws InterruptedException {
        int waterToDrop = Math.min(waterRemaining, waterNeeded);
        if (waterToDrop <= 0) {
            System.out.printf("Drone %d: No water available%n", droneId);
            return 0;
        }

        System.out.printf("Drone %d: Extinguishing — dropping %dL [%s]%n",
                droneId, waterToDrop, clock.getFormattedTime());

        sleep((long)(NOZZLE_OPEN_TIME  * 1000));
        sleep((long)(waterToDrop / FLOW_RATE * 1000));
        sleep((long)(NOZZLE_CLOSE_TIME * 1000));

        waterRemaining -= waterToDrop;
        System.out.printf("Drone %d: Done. Water remaining: %dL%n",
                droneId, waterRemaining);
        return waterToDrop;
    }


    public void refillWater() throws InterruptedException {
        System.out.printf("Drone %d: Refilling [%s]%n",
                droneId, clock.getFormattedTime());
        sleep(5000);
        waterRemaining = MAX_CAPACITY;
        System.out.printf("Drone %d: Refill complete (%dL) [%s]%n",
                droneId, waterRemaining, clock.getFormattedTime());
    }


    public void handleEvent(droneEvents event) {
        try {
            switch (event) {

                case NEW_MISSION: {
                    // If already en-route, tell DroneSubsystem to reschedule
                    // the mission we are abandoning
                    if (droneState == DroneState.ONROUTE && currentMission != null) {
                        callback.onRescheduleFireEvent(droneId, currentMission);
                    }

                    setState(DroneState.ONROUTE);
                    updateMission();

                    int mx = getXFromZone(currentMission.getZoneId());
                    int my = getYFromZone(currentMission.getZoneId());
                    setMissionCoordinates(mx, my);

                    System.out.printf("Drone %d: En-route to Zone %d at (%d,%d)%n",
                            droneId, currentMission.getZoneId(), mx, my);

                    moveDrone();
                    break;
                }

                case ARRIVED: {
                    if (droneState == DroneState.ONROUTE) {
                        handleEvent(droneEvents.COMPLETED_MISSION);
                    } else {
                        // Back at base after RETURNING
                        setState(DroneState.REFILLING);
                        callback.onDroneRefilling(droneId);
                        refillWater();
                        callback.onDroneRefillComplete(droneId);
                        setState(DroneState.IDLE);
                    }
                    break;
                }

                case COMPLETED_MISSION: {
                    setState(DroneState.EXTINGUISHING);
                    int waterUsed = extinguishFire(currentMission.getWaterRemaining());
                    callback.onMissionCompleted(droneId,
                            currentMission.getZoneId(), waterUsed);
                    currentMission = null;

                    if (waterRemaining <= 0) {
                        setState(DroneState.RETURNING);
                        setMissionCoordinates(0, 0);
                        System.out.printf("Drone %d: Tank empty — returning to base%n",
                                droneId);
                        moveDrone();
                    } else {
                        setState(DroneState.IDLE);
                    }
                    break;
                }

                case FAULT: {
                    setState(DroneState.FAULTED);
                    System.out.printf("Drone %d: FAULTED — recovering%n", droneId);
                    sleep(1000);
                    setState(DroneState.IDLE);
                    break;
                }

                case DECOMMISSION: {
                    setState(DroneState.DECOMMISSIONED);
                    System.out.printf("Drone %d: Decommissioned%n", droneId);
                    break;
                }

                default:
                    break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    @Override
    public void run() {
        System.out.printf("Drone %d: Starting%n", droneId);

        while (droneState != DroneState.DECOMMISSIONED) {
            // Wait for DroneSubsystem to push a mission via receiveMissionPush()
            synchronized (this) {
                while (incomingMission == null && droneState == DroneState.IDLE) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            if (incomingMission != null) {
                handleEvent(droneEvents.NEW_MISSION);
            }
        }

        System.out.printf("Drone %d: Shut down%n", droneId);
    }
}
