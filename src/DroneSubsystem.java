import java.net.*;

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
    public enum DroneState {
        IDLE,
        ONROUTE,
        EXTINGUISHING,
        REFILLING,
        FAULTED,
        DECOMMISSIONED
    }

    private static final double NOZZLE_OPEN_TIME  = 0.75;
    private static final double NOZZLE_CLOSE_TIME = 0.75;
    private static final double FLOW_RATE         = 3.77;
    private static final int    MAX_CAPACITY      = 15;

    // CHANGED: socket fields instead of Scheduler reference
    private static final int    BUFFER_SIZE    = 1024;
    private static final int    TIMEOUT_MS     = 5000;
    private static final int    MAX_RETRIES    = 3;

    private int droneId;
    private DroneState droneState;
    private FireEvent currentMission;
    private int zoneId;
    private int xGridLocation;
    private int yGridLocation;
    private int waterRemaining;

    private final DatagramSocket socket;         // CHANGED
    private final InetAddress    schedulerAddr;  // CHANGED
    private final int            schedulerPort;  // CHANGED
    private final SimulationClock clock;

    public DroneSubsystem(int droneId, String schedulerHost, int schedulerPort) // CHANGED
            throws Exception {
        this.droneId       = droneId;
        xGridLocation      = 0;
        yGridLocation      = 0;
        zoneId             = 0;
        droneState         = DroneState.IDLE;
        waterRemaining     = MAX_CAPACITY;
        this.schedulerAddr = InetAddress.getByName(schedulerHost);
        this.schedulerPort = schedulerPort;
        this.socket        = new DatagramSocket();
        this.socket.setSoTimeout(TIMEOUT_MS);
        this.clock         = SimulationClock.getInstance();
    }

    public int        getX()              { return this.xGridLocation; }
    public int        getY()              { return this.yGridLocation; }
    public int        getWaterRemaining() { return this.waterRemaining; }
    public DroneState getDroneState()     { return this.droneState; }
    public Integer    getDroneId()        { return this.droneId; }
    public FireEvent  getCurrentMission() { return currentMission; }
    public void       setCurrentMission(FireEvent e) { this.currentMission = e; }
    public void       setState(DroneState s) { this.droneState = s; }

    // ── Zone helpers (unchanged) ──────────────────────────────────────────

    private int getXFromZone(int zoneId) {
        switch (zoneId) {
            case 1: return 7;  case 2: return 22;
            case 3: return 7;  case 4: return 22;
            default: return 7;
        }
    }

    private int getYFromZone(int zoneId) {
        switch (zoneId) {
            case 1: return 7;  case 2: return 7;
            case 3: return 22; case 4: return 22;
            default: return 7;
        }
    }

    // ── UDP helpers (CHANGED: replaces direct scheduler calls) ───────────

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

    /** Send with no reply expected. */
    private void sendOnly(String message) {
        try {
            byte[] data = message.getBytes();
            socket.send(new DatagramPacket(data, data.length, schedulerAddr, schedulerPort));
        } catch (Exception e) {
            System.err.println("Drone " + droneId + " send error: " + e.getMessage());
        }
    }

    // ── Movement ──────────────────────────────────────────────────────────

    public void moveDrone(int targetX, int targetY) throws InterruptedException {
        System.out.printf("Drone %d: Starting movement to (%d, %d) at simulation time %s%n",
                droneId, targetX, targetY, clock.getFormattedTime());

        while (!((targetX == xGridLocation) && (targetY == yGridLocation))) {
            if (targetX > xGridLocation) xGridLocation++;
            if (targetX < xGridLocation) xGridLocation--;
            if (targetY > yGridLocation) yGridLocation++;
            if (targetY < yGridLocation) yGridLocation--;

            clock.sleepForSimulationSeconds(1);

            // CHANGED: was nothing, now sends position to scheduler
            sendOnly("locationUpdate|" + droneId + "|"
                    + xGridLocation + "|" + yGridLocation + "|" + droneState.name());
        }

        System.out.printf("Drone %d: Arrived at destination (%d, %d) at simulation time %s%n",
                droneId, targetX, targetY, clock.getFormattedTime());
    }

    // ── Extinguish (unchanged) ────────────────────────────────────────────

    public int extinguishFire(int waterNeeded) throws InterruptedException {
        int waterToDrop = Math.min(waterRemaining, waterNeeded);

        if (waterToDrop <= 0) {
            System.out.printf("Drone %d: No water to drop%n", droneId);
            return 0;
        }

        System.out.printf("Drone %d: Starting extinguishing sequence for %dL water%n",
                droneId, waterToDrop);
        System.out.printf("Drone %d: Opening nozzle (%.2fs)%n", droneId, NOZZLE_OPEN_TIME);
        clock.sleepForSimulationSeconds((long)(NOZZLE_OPEN_TIME));

        double flowTime = waterToDrop / FLOW_RATE;
        System.out.printf("Drone %d: Dropping water at %.2f L/s for %.2fs%n",
                droneId, FLOW_RATE, flowTime);
        clock.sleepForSimulationSeconds((long)(flowTime));

        System.out.printf("Drone %d: Closing nozzle (%.2fs)%n", droneId, NOZZLE_CLOSE_TIME);
        clock.sleepForSimulationSeconds((long)(NOZZLE_CLOSE_TIME));

        System.out.printf("Drone %d: Extinguishing complete%n", droneId);
        this.waterRemaining -= waterToDrop;
        return waterToDrop;
    }

    // ── Refill ────────────────────────────────────────────────────────────

    private void goForRefill() throws InterruptedException {
        setState(DroneState.REFILLING);
        sendOnly("droneRefilling|" + droneId); // CHANGED: was scheduler.droneRefilling(droneId)
        moveDrone(0, 0);
        refillWater();
        sendOnly("droneRefillComplete|" + droneId); // CHANGED: was scheduler.droneRefillComplete(droneId)
        setState(DroneState.IDLE);
    }

    public void refillWater() throws InterruptedException {
        System.out.printf("Drone %d: Refilling water tank at simulation time %s%n",
                droneId, clock.getFormattedTime());
        clock.sleepForSimulationSeconds(5);
        System.out.printf("Drone %d: Done refilling water tank at simulation time %s%n",
                droneId, clock.getFormattedTime());
        this.waterRemaining = 15;
        setState(DroneState.IDLE);
    }

    // ── State machine ─────────────────────────────────────────────────────

    public void performAction() {
        try {
            switch (droneState) {
                case IDLE:
                    FireEvent mission = requestMission();
                    if (mission != null) {
                        this.currentMission = mission;
                        setState(DroneState.ONROUTE);
                    } else {
                        setState(DroneState.REFILLING); // GOTO_REFILL response
                    }
                    break;

                case ONROUTE:
                    if (currentMission != null) {
                        int targetX = getXFromZone(currentMission.getZoneId());
                        int targetY = getYFromZone(currentMission.getZoneId());
                        System.out.printf("Drone %d: Moving to Zone %d at (%d, %d)%n",
                                droneId, currentMission.getZoneId(), targetX, targetY);
                        moveDrone(targetX, targetY);
                        setState(DroneState.EXTINGUISHING);
                    } else {
                        setState(DroneState.IDLE);
                    }
                    break;

                case EXTINGUISHING:
                    int waterNeeded = currentMission.getWaterRemaining();
                    int waterUsed   = extinguishFire(waterNeeded);
                    // CHANGED: was scheduler.missionCompleted(...)
                    sendOnly("missionCompleted|" + droneId + "|"
                            + currentMission.getZoneId() + "|" + waterUsed);
                    currentMission = null;
                    droneState     = waterRemaining <= 0 ? DroneState.REFILLING : DroneState.IDLE;
                    break;

                case REFILLING:
                    goForRefill();
                    break;

                case FAULTED:
                    System.out.printf("Drone %d is faulted. Waiting for recovery...%n", droneId);
                    Thread.sleep(1000);
                    droneState = DroneState.IDLE;
                    break;

                case DECOMMISSIONED:
                    System.out.printf("Drone %d is decommissioned.%n", droneId);
                    break;
            }

            Thread.sleep(10);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends requestMission to the scheduler and blocks until a mission,
     * GOTO_REFILL, or an error is returned.
     * CHANGED: was scheduler.requestMission(droneId)
     */
    private FireEvent requestMission() throws InterruptedException {
        while (true) {
            try {
                String response = sendAndReceive(
                        "requestMission|" + droneId + "|"
                                + xGridLocation + "|" + yGridLocation + "|" + waterRemaining);

                String[] parts = response.split("\\|");
                switch (parts[0]) {
                    case "MISSION": {
                        // MISSION|zoneId|eventType|severity|waterAssigned|secondsFromStart
                        FireEvent base = new FireEvent(
                                Integer.parseInt(parts[1]),
                                parts[2],
                                parts[3],
                                Integer.parseInt(parts[5]));
                        return new FireEvent(base, Integer.parseInt(parts[4]));
                    }
                    case "GOTO_REFILL":
                        return null;
                    case "WAIT":
                        Thread.sleep(500);
                        break;
                    default:
                        System.err.println("Drone " + droneId + ": unexpected: " + response);
                        Thread.sleep(200);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Drone " + droneId + ": timeout on requestMission, retrying...");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                System.err.println("Drone " + droneId + " requestMission error: " + e.getMessage());
                Thread.sleep(500);
            }
        }
    }

    @Override
    public void run() {
        System.out.println("Drone " + droneId + " starting operations...");
        while (droneState != DroneState.DECOMMISSIONED) {
            performAction();
        }
        socket.close();
    }
}