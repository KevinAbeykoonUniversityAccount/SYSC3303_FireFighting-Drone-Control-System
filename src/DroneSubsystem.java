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

    private static final int    BUFFER_SIZE    = 1024;
    private static final int    TIMEOUT_MS     = 5000;
    private static final int    MAX_RETRIES    = 3;

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
        this.incomingMission = null;
        this.currentMission = null;

        //this.scheduler = scheduler;
    }

    public int        getX()              { return this.xGridLocation; }
    public int        getY()              { return this.yGridLocation; }
    public int        getWaterRemaining() { return this.waterRemaining; }
    public DroneState getDroneState()     { return this.droneState; }
    public Integer    getDroneId()        { return this.droneId; }
    public FireEvent  getCurrentMission() { return currentMission; }
    public void       setCurrentMission(FireEvent e) { this.currentMission = e; }
    public void       setState(DroneState s) { this.droneState = s; }

    public void setMissionCoordinates(int xPos, int yPos) {
        this.targetX = xPos;
        this.targetY = yPos;
        this.hasTarget = true;
        System.out.println("New coord: " + xPos + " " + yPos);
    }
    // ── Zone helpers (unchanged) ──────────────────────────────────────────

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

            // Sends position to scheduler
            sendOnly("locationUpdate|" + droneId + "|"
                    + xGridLocation + "|" + yGridLocation + "|" + droneState.name());
        }
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

    // ── Refill ────────────────────────────────────────────────────────────

    private void goForRefill() throws InterruptedException {
        setState(DroneState.REFILLING);
        sendOnly("droneRefilling|" + droneId); // CHANGED: was scheduler.droneRefilling(droneId)
        moveDrone(0, 0);
        refillWater();
        sendOnly("droneRefillComplete|" + droneId); // CHANGED: was scheduler.droneRefillComplete(droneId)
        setState(DroneState.IDLE);
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
        System.out.printf("Drone %d: Refilling water tank at simulation time %s%n",
                droneId, clock.getFormattedTime());

        // Refilling takes 5 simulation seconds
        sleep(5000);
        //clock.sleepForSimulationSeconds(5);
        System.out.printf("Drone %d: Done refilling water tank at simulation time %s%n",
                droneId, clock.getFormattedTime());
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

                case ONROUTE:
                    if (currentMission != null) {
                        int targetX = getXFromZone(currentMission.getZoneId());
                        int targetY = getYFromZone(currentMission.getZoneId());
                        System.out.printf("Drone %d: Moving to Zone %d at (%d, %d)%n",
                                droneId, currentMission.getZoneId(), targetX, targetY);
                        moveDrone(targetX, targetY);
            case ARRIVED:
                System.out.println(droneState);
                switch (droneState) {
                    case ONROUTE:
                        setState(DroneState.EXTINGUISHING);
                    } else {
                        setState(DroneState.IDLE);
                    }
                    break;

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

                case EXTINGUISHING:
                    int waterNeeded = currentMission.getWaterRemaining();
                    int waterUsed   = extinguishFire(waterNeeded);
                    // CHANGED: was scheduler.missionCompleted(...)
                    sendOnly("missionCompleted|" + droneId + "|"
                            + currentMission.getZoneId() + "|" + waterUsed);
                    currentMission = null;
                    droneState     = waterRemaining <= 0 ? DroneState.REFILLING : DroneState.IDLE;
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

                case REFILLING:
                    goForRefill();
                    break;
            case FAULT:
                break;

                case FAULTED:
                    System.out.printf("Drone %d is faulted. Waiting for recovery...%n", droneId);
                    Thread.sleep(1000);
                    droneState = DroneState.IDLE;
                    break;
            case DECOMMISSION:
                droneState = DroneState.DECOMMISSIONED;
                break;

                case DECOMMISSIONED:
                    System.out.printf("Drone %d is decommissioned.%n", droneId);
                    break;

                default:
                    break;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
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

     default:
     break;
     }

     } catch (InterruptedException e) {
     Thread.currentThread().interrupt();
     }
     }*/

    /**
     * Sends requestMission to the scheduler and blocks until a mission,
     * GOTO_REFILL, or an error is returned.
     * CHANGED: was scheduler.requestMission(droneId)
     */
    /**private FireEvent requestMission() throws InterruptedException {
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
    }*/

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
        socket.close();
        System.out.println("Drone " + droneId + " decomissioned...");
    }
}