import java.net.*;

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
        DECOMMISSION,

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

    //Constants
    private static final int    BUFFER_SIZE       = 1024;
    private static final double NOZZLE_OPEN_TIME  = 0.75;  // seconds
    private static final double NOZZLE_CLOSE_TIME = 0.75;  // seconds
    private static final double FLOW_RATE         = 3.77;  // litres / second
    private static final int    MAX_CAPACITY      = 15;    // litres
    private static final int    MAX_RETRIES       = 3;

    // Drone State
    private final int droneId;
    private volatile DroneState droneState;

    private volatile FireEvent incomingMission;
    private FireEvent currentMission;

    private int     xGridLocation;
    private int     yGridLocation;
    private int     targetX;
    private int     targetY;
    private boolean hasTarget          = false;
    private boolean missionInterrupted = false;
    private int     waterRemaining;
    private int     zoneId;

    // Clock
    private SimulationClock clock; // centralized clock

    // Networking
    private final DatagramSocket socket;  // bound to listenPort; used for both send and receive
    private final InetAddress schedulerAddr;
    private final int         schedulerPort;


    /**
     * @param droneId        unique drone identifier
     * @param schedulerHost  hostname / IP of the Scheduler process
     * @param schedulerPort  Scheduler's main UDP listen port
     */
    public DroneSubsystem(int droneId,
                          String schedulerHost, int schedulerPort) throws Exception {
        this.droneId         = droneId;
        this.xGridLocation   = 0;
        this.yGridLocation   = 0;
        this.droneState      = DroneState.IDLE;
        this.waterRemaining  = MAX_CAPACITY;
        this.waterRemaining  = MAX_CAPACITY;
        this.schedulerAddr   = InetAddress.getByName(schedulerHost);
        this.schedulerPort   = schedulerPort;
        this.socket          = new DatagramSocket(0);

        // during thread.sleep, wake up every 200ms to check new incoming missions
        this.socket.setSoTimeout(200);
        this.clock           = SimulationClock.getInstance();
        this.incomingMission = null;
        this.currentMission  = null;


    }

    // Getters and Setters
    public int        getX()              { return this.xGridLocation; }
    public int        getY()              { return this.yGridLocation; }
    public int        getWaterRemaining() { return this.waterRemaining; }
    public DroneState getDroneState()     { return this.droneState; }
    public Integer    getDroneId()        { return this.droneId; }
    public FireEvent  getCurrentMission() { return currentMission; }
    public void       setCurrentMission(FireEvent e) { this.currentMission = e; }
    public void       setState(DroneState s) { this.droneState = s; }


    /**
     * Called by the background listener when the Scheduler pushes a new
     * assignment.  Stores the event and wakes the main run() thread.
     * If the drone is currently en-route, sets missionInterrupted so that
     * moveDrone() exits its movement loop cleanly.
     */
    public synchronized void receiveMissionPush(FireEvent event) {
        this.incomingMission = event;
        if (droneState == DroneState.ONROUTE) {
            missionInterrupted = true;
        }
        notifyAll();
    }

    public void updateMission() {
        this.currentMission = this.incomingMission;
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

    // ========== UDP helpers ==============

    /** Send a message and wait for a reply. Retries on timeout. */
    private String sendAndReceive(String message) throws Exception {
        byte[]         data    = message.getBytes();
        DatagramPacket sendPkt = new DatagramPacket(data, data.length,
                schedulerAddr, schedulerPort);
        byte[]         buf     = new byte[BUFFER_SIZE];
        DatagramPacket recvPkt = new DatagramPacket(buf, buf.length);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            socket.send(sendPkt);
            try {
                socket.receive(recvPkt);
                return new String(recvPkt.getData(), 0, recvPkt.getLength()).trim();
            } catch (SocketTimeoutException e) {
                System.err.printf("Drone %d: timeout (attempt %d/%d) for '%s'%n",
                        droneId, attempt, MAX_RETRIES, message.split("\\|")[0]);
            }
        }
        throw new Exception("No response after " + MAX_RETRIES + " attempts");
    }

    /** Sends a message to the Scheduler; no reply is expected. */
    private void sendOnly(String message) {
        try {
            byte[] data = message.getBytes();
            socket.send(new DatagramPacket(
                    data, data.length, schedulerAddr, schedulerPort));
        } catch (Exception e) {
            System.err.println("Drone " + droneId + " send error: " + e.getMessage());
        }
    }


    // ============= Movement ============

    /** Sets the movement target. Must be called before moveDrone(). */
    public void setMissionCoordinates(int x, int y) {
        this.targetX   = x;
        this.targetY   = y;
        this.hasTarget = true;
    }


    /**
     * Moves the drone one grid cell per simulated second toward the target
     * set by setMissionCoordinates().  Reports position to the Scheduler
     * after every step.
     *
     * Returns early without calling handleEvent if missionInterrupted is
     * raised by the listener thread.  The run() loop detects the pending
     * incomingMission and re-enters the state machine for the new mission.
     *
     * Calls handleEvent(ARRIVED) when the destination is reached.
     */
    public void moveDrone() throws InterruptedException {
        while (xGridLocation != targetX || yGridLocation != targetY) {
            if (missionInterrupted || droneState == DroneState.FAULTED) {
                System.out.printf("Drone %d: movement stopped due to fault%n", droneId);
                return;
            }
            if (droneState == DroneState.DECOMMISSIONED) return;

            if      (targetX > xGridLocation) xGridLocation++;
            else if (targetX < xGridLocation) xGridLocation--;

            if      (targetY > yGridLocation) yGridLocation++;
            else if (targetY < yGridLocation) yGridLocation--;

            // one simulated second per cell
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                System.out.printf("Drone %d: movement interrupted%n", droneId);
                return;
            }

            sendOnly("locationUpdate|" + droneId + "|"
                    + xGridLocation + "|" + yGridLocation + "|"
                    + droneState.name());
        }

        hasTarget = false;
        System.out.printf("Drone %d: Arrived at (%d, %d)%n",
                droneId, xGridLocation, yGridLocation);
        if (droneState != DroneState.DECOMMISSIONED) {
            handleEvent(droneEvents.ARRIVED);
        }
    }



    // ============= FIRE EXTINGUISHING =============
    /**
     * Simulates nozzle-open → water-drop → nozzle-close.
     *
     * @param waterNeeded litres required to fully extinguish
     * @return litres actually dropped (capped at waterRemaining)
     * @throws InterruptedException
     */
    public int extinguishFire(int waterNeeded) throws InterruptedException {
        int waterToDrop = Math.min(waterRemaining, waterNeeded);
        if (waterToDrop <= 0) {
            System.out.printf("Drone %d: No water available%n", droneId);
            return 0;
        }

        if (missionInterrupted || droneState == DroneState.FAULTED) return 0;
        if (droneState == DroneState.DECOMMISSIONED) return 0;

        System.out.printf("Drone %d: Extinguishing — dropping %dL [sim time %s]%n",
                droneId, waterToDrop, clock.getFormattedTime());

        try {
            sleep((long)(NOZZLE_OPEN_TIME * 1000));
        } catch (InterruptedException e) {
            return 0;
        }

        if (missionInterrupted || droneState == DroneState.FAULTED) return 0;
        if (droneState == DroneState.DECOMMISSIONED) return 0;

        try {
            sleep((long)(waterToDrop / FLOW_RATE * 1000));
        } catch (InterruptedException e) {
            return 0;
        }

        if (missionInterrupted || droneState == DroneState.FAULTED) return 0;
        if (droneState == DroneState.DECOMMISSIONED) return 0;


        try {
            sleep((long)(NOZZLE_CLOSE_TIME * 1000));
        } catch (InterruptedException e) {
            return 0;
        }

        waterRemaining -= waterToDrop;
        System.out.printf("Drone %d: Extinguishing done. Water remaining: %dL%n",
                droneId, waterRemaining);
        return waterToDrop;
    }




    // =========== REFILL ===============
    /**
     * Simulates a 5-second refill at base and restores the tank to MAX_CAPACITY.
     */
    public void refillWater() throws InterruptedException {
        System.out.printf("Drone %d: Refilling at base [sim time %s]%n",
                droneId, clock.getFormattedTime());
        try {
            sleep(5000);
        } catch (InterruptedException e) {
            return;
        }
        waterRemaining = MAX_CAPACITY;
        System.out.printf("Drone %d: Refill complete (%dL) [sim time %s]%n",
                droneId, waterRemaining, clock.getFormattedTime());
    }



    // ========== STATE MACHINE ===========
    public void handleEvent(droneEvents event) {
        if (droneState == DroneState.FAULTED && event != droneEvents.DECOMMISSION) {
            return;
        }

        try {
            switch (event) {

                // ── Scheduler pushed a new mission ─────────────────────────
                case NEW_MISSION: {
                    missionInterrupted = false;

                    // If already en-route, reschedule the abandoned mission
                    if (droneState == DroneState.ONROUTE && currentMission != null && !missionInterrupted) {                        sendOnly("rescheduleFireEvent|"
                                + currentMission.getZoneId()         + "|"
                                + currentMission.getEventType()       + "|"
                                + currentMission.getSeverity().name() + "|"
                                + currentMission.getWaterRemaining()  + "|"
                                + currentMission.getSecondsFromStart());
                    }

                    setState(DroneState.ONROUTE);
                    updateMission();  // incomingMission → currentMission

                    int mx = getXFromZone(currentMission.getZoneId());
                    int my = getYFromZone(currentMission.getZoneId());
                    setMissionCoordinates(mx, my);

                    System.out.printf("Drone %d: En-route to Zone %d at (%d, %d)%n",
                            droneId, currentMission.getZoneId(), mx, my);

                    moveDrone();
                    // On normal arrival: moveDrone → handleEvent(ARRIVED) → ...
                    // On interrupt:      moveDrone returns early;
                    //                    run() loop re-enters NEW_MISSION.
                    break;
                }

                // ── Drone reached its current destination ──────────────────
                case ARRIVED: {
                    if (droneState == DroneState.ONROUTE) {
                        // At the fire zone — start extinguishing
                        handleEvent(droneEvents.COMPLETED_MISSION);
                    } else {
                        // Back at base (state == RETURNING) — refill then idle
                        setState(DroneState.REFILLING);
                        sendOnly("droneRefilling|" + droneId);
                        refillWater();
                        sendOnly("droneRefillComplete|" + droneId);
                        setState(DroneState.IDLE);
                    }
                    break;
                }

                // ── Extinguish fire and report to Scheduler ─────────────
                case COMPLETED_MISSION: {
                    setState(DroneState.EXTINGUISHING);
                    int waterUsed = extinguishFire(currentMission.getWaterRemaining());
                    sendOnly("missionCompleted|" + droneId + "|"
                            + currentMission.getZoneId() + "|" + waterUsed);
                    currentMission = null;

                    if (waterRemaining <= 0) {
                        // Tank empty — head back to base to refill
                        setState(DroneState.RETURNING);
                        setMissionCoordinates(0, 0);
                        System.out.printf(
                                "Drone %d: Tank empty — returning to base%n", droneId);
                        moveDrone();
                        // moveDrone → handleEvent(ARRIVED) → refill → IDLE
                    } else {
                        setState(DroneState.IDLE);
                    }

                    break;
                }

                // ── Fault / recovery ───────────────────────────────────────

                case FAULT: {
                    System.out.printf("Drone %d: FAULTED — interrupting%n", droneId);

                    setState(DroneState.FAULTED);
                    missionInterrupted = true;
                    sendOnly("droneFaulted|" + droneId);

                    // Recovery in separate thread
                    new Thread(() -> {
                        try {
                            Thread.sleep(10000); // recovery time
                            synchronized (DroneSubsystem.this) {
                                missionInterrupted = false;
                                setState(DroneState.IDLE);
                                sendOnly("droneRecovered|" + droneId);
                                notifyAll();
                                System.out.printf("Drone %d: Recovered from soft fault%n", droneId);
                            }
                        } catch (InterruptedException ignored) {}
                    }, "drone-" + droneId + "-recovery").start();

                    break;
                }

                // ── Decommission ───────────────────────────────────────────
                case DECOMMISSION: {
                    setState(DroneState.DECOMMISSIONED);

                    System.out.printf("Drone %d: Decommissioned%n", droneId);
                    this.interrupt();

                    synchronized (this) {
                        notifyAll();
                    }
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

        // registerWithScheduler() (inlined)
        sendOnly("registerDrone|" + droneId + "|" + xGridLocation + "|"
                + yGridLocation + "|" + waterRemaining + "|"
                + socket.getLocalPort());

        // startListenerThread() + handleSchedulerPush()
        // Only needed because moveDrone() blocks the main thread with sleep(),
        // thus the thread listens for any received ASSIGN_MISSION / DECOMMISSION while
        // the drone moves.
        Thread listener = new Thread(() -> {
            byte[] buf = new byte[BUFFER_SIZE];
            while (droneState != DroneState.DECOMMISSIONED) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
                    String[] parts = msg.split("\\|");
                    switch (parts[0]) {
                        case "ASSIGN_MISSION": {
                            FireEvent base = new FireEvent(
                                    Integer.parseInt(parts[1]), parts[2], parts[3],
                                    Integer.parseInt(parts[5]));
                            FireEvent mission = new FireEvent(base, Integer.parseInt(parts[4]));
                            receiveMissionPush(mission);
                            break;
                        }
                        case "DECOMMISSION":
                            handleEvent(droneEvents.DECOMMISSION);
                            break;
                        case "FAULT":
                            handleEvent(droneEvents.FAULT);
                            break;
                        default:
                            System.err.println("Drone " + droneId
                                    + ": unrecognised push: " + parts[0]);
                            break;

                    }
                } catch (SocketException e) {
                    break;  // socket closed on shutdown
                } catch (Exception e) {
                    System.err.println("Drone " + droneId + " listener: " + e.getMessage());
                }
            }
        }, "drone-" + droneId + "-listener");
        listener.setDaemon(true);
        listener.start();

        // Main thread drives the state machine — same structure as the original
        while (droneState != DroneState.DECOMMISSIONED) {
            synchronized (this) {
                while (incomingMission == null && droneState == DroneState.IDLE) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }

            if (incomingMission != null) {
                handleEvent(droneEvents.NEW_MISSION);
            }
        }

        socket.close();
        System.out.printf("Drone %d: Shut down%n", droneId);
    }


}