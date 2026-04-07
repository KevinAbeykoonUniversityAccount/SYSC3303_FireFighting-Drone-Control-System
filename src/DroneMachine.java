/**
 * Represents a single physical drone.
 *
 * Separated from the subsystem, it represents a machine with no knowledge of UDP or
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
        REFILLING_AND_RECHARGING,
        FAULTED,
        DECOMMISSIONED
    }


    private static final double NOZZLE_OPEN_TIME  = 0.75;  // simulation-seconds
    private static final double NOZZLE_CLOSE_TIME = 0.75;  // simulation-seconds
    private static final double FLOW_RATE         = 3.77;  // litres / simulation-second
    private static final int    MAX_CAPACITY      = 15;    // litres
    private static final long   FAULT_CHECK_MS    = 200;   // real-ms tick interval (fixed)
    private static final long   SOFT_FAULT_WAIT_SIM_S = 10; // soft-fault pause in simulation-seconds
    private static final int    FULL_BATTERY_LEVEL = 100;

    /** Converts simulation-seconds to real milliseconds using the current clock speed. */
    private long simToRealMs(double simSeconds) {
        int speed = clock.getClockSpeedMultiplier();
        return Math.max(50, (long)(simSeconds * 1000.0 / speed));
    }


    private final int droneId;
    private DroneState droneState;

    private volatile FireEvent incomingMission;
    private FireEvent currentMission;

    private volatile FaultType currentFaultType = FaultType.NONE;

    private int     xGridLocation;
    private int     yGridLocation;
    private int     targetX;
    private int     targetY;
    private boolean hasTarget          = false;
    private boolean missionInterrupted = false;
    private int     waterRemaining;

    private final DroneCallback callback;
    private final SimulationClock clock;

    private int     batteryLevel;
    private volatile boolean returnToBaseRequested = false;


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
        this.batteryLevel    = FULL_BATTERY_LEVEL;
    }

    /** Test-only constructor: starts the drone with a specific battery level. */
    DroneMachine(int droneId, DroneCallback callback, int initialBattery) {
        this(droneId, callback);
        this.batteryLevel = initialBattery;
    }

    // Getters and Setters
    public int        getDroneId()        { return droneId; }
    public int        getX()              { return xGridLocation; }
    public int        getY()              { return yGridLocation; }
    public int        getWaterRemaining() { return waterRemaining; }
    public DroneState getDroneState()     { return droneState; }
    public FireEvent  getCurrentMission() { return currentMission; }
    public int        getBatteryRemaining() { return batteryLevel; }
    public int        getBatteryCapacity() { return FULL_BATTERY_LEVEL; }



    public synchronized void setState(DroneState s) {
        this.droneState = s;
        notifyAll();  // wake run() loop if it is waiting (e.g. for DECOMMISSION to arrive)
        log("Drone " + droneId + ",STATE_CHANGE," + s);
    }

    /**
     * Reduces battery by the given amount and transitions to DECOMMISSIONED
     * if the level hits 0. Returns true if the drone is now dead.
     */
    private boolean drainBattery(int amount) {
        batteryLevel = Math.max(0, batteryLevel - amount);
        callback.onBatteryUpdate(droneId, batteryLevel);
        if (batteryLevel <= 0) {
            System.out.printf("Drone %d: Battery depleted — decommissioning%n", droneId);
            setState(DroneState.DECOMMISSIONED);
            callback.onHardFault(droneId);
            return true;
        }
        return false;
    }


    /**
     * DroneSubsystem calls this when the Scheduler pushes a new assignment.
     * Wakes the run() loop if the drone is waiting idle.
     * Sets missionInterrupted if the drone is currently moving.
     *
     * @param event the fire event to carry out
     */
    public synchronized void receiveMissionPush(FireEvent event) {
        this.incomingMission = event;
        if (droneState == DroneState.ONROUTE) {
            missionInterrupted = true;
        }
        notifyAll();
    }

    /**
     * Called when only a fault needs to be injected mid-action with no new
     * mission attached (e.g. Scheduler pushing a fault to a busy drone).
     *
     * @param faultType type of fault being injected
     */
    public synchronized void injectFault(FaultType faultType) {
        this.currentFaultType = faultType;
        notifyAll();
    }

    /** Called by DroneSubsystem when the Scheduler signals all missions are done. */
    public synchronized void requestReturnToBase() {
        returnToBaseRequested = true;
        notifyAll(); // wakes a waiting-idle drone; mid-mission drones pick it up after finishing
    }

    private void updateMission() {
        this.currentMission  = this.incomingMission;
        this.incomingMission = null;
    }

    // ============ Zone coordinate helpers ============


    /**
     * Sleeps for the requested duration in FAULT_CHECK_MS ticks.
     * Returns early as soon as currentFaultType != NONE or missionInterrupted
     * is set, allowing mid-action fault detection without blocking until the
     * full sleep duration expires.
     */
    private void sleepInterruptibly(long ms) throws InterruptedException {
        long remaining = ms;
        while (remaining > 0) {
            sleep(Math.min(FAULT_CHECK_MS, remaining));
            remaining -= FAULT_CHECK_MS;
            if (currentFaultType != FaultType.NONE) return;
            if (missionInterrupted)                 return;
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
            if (drainBattery(1)) return;

            if (missionInterrupted) {
                missionInterrupted = false;
                return;
            }

            if      (targetX > xGridLocation) xGridLocation++;
            else if (targetX < xGridLocation) xGridLocation--;
            if      (targetY > yGridLocation) yGridLocation++;
            else if (targetY < yGridLocation) yGridLocation--;

            sleepInterruptibly(simToRealMs(1));

            // Check for fault injected during this step
            if (currentFaultType != FaultType.NONE) {
                FaultType savedFault = currentFaultType;  // save before handleEvent consumes it
                DroneState stateBeforeFault = droneState; // ONROUTE or RETURNING
                handleEvent(droneEvents.FAULT);
                if (droneState == DroneState.DECOMMISSIONED) return;
                // Hard fault: state is FAULTED, waiting for async DECOMMISSION — stop moving
                if (savedFault == FaultType.NOZZLE_FAULT) return;
                // Soft fault recovered — restore the exact state active before the fault
                // (RETURNING drones must stay RETURNING, not flip to ONROUTE)
                setState(stateBeforeFault);
            }

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

        System.out.printf("Drone %d: Extinguishing — dropping %dL%n",
                droneId, waterToDrop);

        sleepInterruptibly(simToRealMs(NOZZLE_OPEN_TIME));
        if (currentFaultType != FaultType.NONE) return -1;

        sleepInterruptibly(simToRealMs(waterToDrop / FLOW_RATE));
        if (currentFaultType != FaultType.NONE) return -1;

        sleepInterruptibly(simToRealMs(NOZZLE_CLOSE_TIME));
        if (currentFaultType != FaultType.NONE) return -1;

        waterRemaining -= waterToDrop;
        System.out.printf("Drone %d: Done. Water remaining: %dL%n",
                droneId, waterRemaining);
        return waterToDrop;
    }


    public void refillWaterAndRechargeBattery() throws InterruptedException {
        System.out.printf("Drone %d: Refilling water and recharging battery[%s]%n",
                droneId, clock.getFormattedTime());
        sleep(simToRealMs(6));
        waterRemaining = MAX_CAPACITY;
        batteryLevel = FULL_BATTERY_LEVEL;
        System.out.printf("Drone %d: Refill and recharge complete (%dL) [%s]%n",
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

                    System.out.printf("Drone %d: En-route to Zone %d at (%d,%d)%n",
                            droneId, currentMission.getZoneId(), targetX, targetY);

                    moveDrone();
                    break;
                }

                case ARRIVED: {
                    if (droneState == DroneState.ONROUTE) {
                        handleEvent(droneEvents.COMPLETED_MISSION);
                    } else {
                        // Back at base after RETURNING
                        setState(DroneState.REFILLING_AND_RECHARGING);
                        callback.onDroneRefilling(droneId);
                        refillWaterAndRechargeBattery();
                        callback.onDroneRefillComplete(droneId);
                        setState(DroneState.IDLE);
                    }
                    break;
                }

                case COMPLETED_MISSION: {
                    setState(DroneState.EXTINGUISHING);

                    int waterUsed = extinguishFire(currentMission.getWaterRemaining());

                    // Fault fired during extinguishing — handle it now and clear it
                    if (currentFaultType != FaultType.NONE) {
                        FaultType savedFault = currentFaultType;
                        handleEvent(droneEvents.FAULT);  // consumes and clears currentFaultType
                        if (savedFault == FaultType.DRONE_STUCK) {
                            // Soft fault: recovered, mission abandoned — go back to IDLE
                            setState(DroneState.IDLE);
                            callback.onDroneRecovered(droneId);
                        }
                        // Hard fault: state is FAULTED, run() will wait for DECOMMISSION
                        currentMission = null;
                        break;
                    }

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
                    FaultType fault = currentFaultType;
                    currentFaultType = FaultType.NONE;  // consume immediately

                    if (fault == FaultType.DRONE_STUCK) {
                        // Soft fault — freeze in place, recover, caller continues
                        System.out.printf(
                                "Drone %d: SOFT FAULT — stuck at (%d,%d), waiting 10s%n",
                                droneId, xGridLocation, yGridLocation);
                        setState(DroneState.FAULTED);
                        callback.onLocationUpdate(droneId, xGridLocation,
                                yGridLocation, "FAULTED");

                        // Drain 5% battery evenly across the pause duration
                        final int FAULT_DRAIN_PERCENT = 5;
                        long tickMs     = FAULT_CHECK_MS;
                        long faultWaitMs = simToRealMs(SOFT_FAULT_WAIT_SIM_S);
                        long ticks       = Math.max(1, faultWaitMs / tickMs);
                        double drainPerTick = (double) FAULT_DRAIN_PERCENT / ticks;
                        double drainAccumulator = 0;
                        boolean batteryDead = false;
                        for (long t = 0; t < ticks; t++) {
                            sleep(tickMs);
                            drainAccumulator += drainPerTick;
                            if (drainAccumulator >= 1.0) {
                                int drop = (int) drainAccumulator;
                                drainAccumulator -= drop;
                                if (drainBattery(drop)) { batteryDead = true; break; }
                            }
                        }
                        if (batteryDead) return;

                        // Caller (moveDrone or COMPLETED_MISSION) restores state
                        System.out.printf("Drone %d: Recovered from soft fault (battery now %d%%)%n",
                                droneId, batteryLevel);

                    } else if (fault == FaultType.NOZZLE_FAULT) {
                        // Hard fault — permanently shut down
                        System.out.printf(
                                "Drone %d: HARD FAULT — nozzle jammed, decommissioning%n",
                                droneId);
                        //setState(DroneState.FAULTED);
                        setState(DroneState.DECOMMISSIONED);
                        callback.onHardFault(droneId);

                        // DECOMMISSION arrives from Scheduler asynchronously
                        // via DroneSubsystem → handleEvent(DECOMMISSION)
                    }
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

    public void log(String msg) {
        callback.log(msg);
    }


    @Override
    public void run() {
        System.out.printf("Drone %d: Starting%n", droneId);
        log("Drone " + droneId + ",STARTED");

        while (droneState != DroneState.DECOMMISSIONED) {
            // Wait for DroneSubsystem to push a mission via receiveMissionPush().
            // Also block here when FAULTED (hard fault) until DECOMMISSION arrives
            // from the Scheduler — setState(DECOMMISSIONED) calls notifyAll().
            synchronized (this) {
                while ((incomingMission == null && !returnToBaseRequested || droneState == DroneState.FAULTED)
                        && droneState != DroneState.DECOMMISSIONED) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            if (droneState == DroneState.DECOMMISSIONED) break;

            if (incomingMission != null) {
                handleEvent(droneEvents.NEW_MISSION);
            } else if (returnToBaseRequested && droneState == DroneState.IDLE) {
                returnToBaseRequested = false;
                if (xGridLocation != 0 || yGridLocation != 0) {
                    System.out.printf("Drone %d: All missions done — returning to base%n", droneId);
                    try {
                        setState(DroneState.RETURNING);
                        setMissionCoordinates(0, 0);
                        moveDrone();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        System.out.printf("Drone %d: Shut down%n", droneId);
        log("Drone " + droneId + ",ENDED");
    }
}
