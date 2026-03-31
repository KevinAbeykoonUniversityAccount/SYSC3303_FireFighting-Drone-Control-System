import java.net.*;
import java.util.*;

/**
 * Manages all drones for this process and owns the single UDP socket.
 *
 * @author Abdullah Khan   (101305235)
 * @author Aryan Kumar Singh (101299776)
 * @author Kevin Abeykoon  (101301971)
 */
public class DroneSubsystem extends Thread implements DroneCallback {

    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_MS  = 5000;
    private static final int MAX_RETRIES = 3;

    /** One socket shared by all drones in this process. */
    private final DatagramSocket socket;
    private final InetAddress    schedulerAddr;
    private final int            schedulerPort;


    /** droneId → DroneMachine instance. */
    private final Map<Integer, DroneMachine> drones = new HashMap<>();

    /**
     * Creates a DroneMachine for every ID in the list, then binds a single
     * UDP socket that all of them share for outbound sends and for receiving
     * Scheduler pushes.
     *
     * @param ids           drone IDs to manage in this process
     * @param schedulerHost hostname / IP of the Scheduler process
     * @param schedulerPort Scheduler's UDP listen port
     */
    public DroneSubsystem(List<Integer> ids,
                          String schedulerHost,
                          int schedulerPort) throws Exception {
        this.schedulerAddr = InetAddress.getByName(schedulerHost);
        this.schedulerPort = schedulerPort;
        this.socket        = new DatagramSocket(0);  // OS assigns a free port
        this.socket.setSoTimeout(200);

        // Create each drone — pass 'this' as the callback so DroneMachine
        // can report events without touching the socket directly
        for (int id : ids) {
            drones.put(id, new DroneMachine(id, this));
        }
    }

    // ==== UDP helpers ====

    /** Fire-and-forget 
     * . */
    private void sendOnly(String message) {
        try {
            byte[] data = message.getBytes();
            socket.send(new DatagramPacket(
                    data, data.length, schedulerAddr, schedulerPort));
        } catch (Exception e) {
            System.err.println("DroneSubsystem send error: " + e.getMessage());
        }
    }

    /**
     * Send with retry until an ACK is received.
     * Uses a temporary socket so it does not race with the receive loop.
     */
    private String sendAndReceive(String message) throws Exception {
        try (DatagramSocket tmp = new DatagramSocket()) {
            tmp.setSoTimeout(TIMEOUT_MS);
            byte[] data = message.getBytes();
            DatagramPacket sendPkt = new DatagramPacket(data, data.length,
                    schedulerAddr, schedulerPort);
                    
            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket recvPkt = new DatagramPacket(buf, buf.length);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                tmp.send(sendPkt);
                try {
                    tmp.receive(recvPkt);
                    return new String(recvPkt.getData(), 0,
                            recvPkt.getLength()).trim();
                } catch (SocketTimeoutException e) {
                    System.err.printf("DroneSubsystem: timeout (attempt %d/%d) for '%s'%n",
                            attempt, MAX_RETRIES, message.split("\\|")[0]);
                }
            }
            throw new Exception("No response after " + MAX_RETRIES + " attempts");
        }
    }

    // ==== DroneCallback implementation ====
    // These are called by DroneMachine and translated into UDP sends.

    @Override
    public void onLocationUpdate(int droneId, int x, int y, String state) {
        sendOnly("locationUpdate|" + droneId + "|" + x + "|" + y + "|" + state);
    }

    @Override
    public void onMissionCompleted(int droneId, int zoneId, int waterUsed) {
        try {
            sendAndReceive("missionCompleted|" + droneId + "|"
                    + zoneId + "|" + waterUsed);
        } catch (Exception e) {
            System.err.println("DroneSubsystem: missionCompleted failed: "
                    + e.getMessage());
        }
    }

    @Override
    public void onRescheduleFireEvent(int droneId, FireEvent abandonedMission) {
        try {
            sendAndReceive("rescheduleFireEvent|"
                    + abandonedMission.getZoneId()         + "|"
                    + abandonedMission.getEventType()       + "|"
                    + abandonedMission.getSeverity().name() + "|"
                    + abandonedMission.getWaterRemaining()  + "|"
                    + abandonedMission.getSecondsFromStart());
        } catch (Exception e) {
            System.err.println("DroneSubsystem: rescheduleFireEvent failed: "
                    + e.getMessage());
        }
    }

    @Override
    public void onDroneRefilling(int droneId) {
        sendOnly("droneRefilling|" + droneId);
    }

    @Override
    public void onDroneRefillComplete(int droneId) {
        try {
            sendAndReceive("droneRefillComplete|" + droneId);
        } catch (Exception e) {
            System.err.println("DroneSubsystem: droneRefillComplete failed: "
                    + e.getMessage());
        }
    }

    // ==== Fault Lifecycle ====

    /**
     * Hard fault only — nozzle jammed.
     * Soft faults are handled entirely inside DroneMachine; no callback needed.
     * Scheduler re-queues the mission and sends DECOMMISSION|droneId back.
     */
    @Override
    public void onHardFault(int droneId) {
        try {
            sendAndReceive("droneHardFault|" + droneId);
        } catch (Exception e) {
            System.err.println("DroneSubsystem: droneHardFault failed: "
                    + e.getMessage());
        }
    }

    /**
     * Drone recovered from a soft fault — tell Scheduler it is IDLE again.
     */
    @Override
    public void onDroneRecovered(int droneId) {
        sendOnly("droneRecovered|" + droneId);
    }

    // ==== Startup registration ====

    /**
     * Registers every drone with the Scheduler.
     * All drones share this subsystem's port — the Scheduler includes the
     * droneId in every ASSIGN_MISSION push so we can route it here.
     *
     * Message: registerDrone|droneId|x|y|water|listenPort
     */
    private void registerAllDrones() {
        int listenPort = socket.getLocalPort();
        for (DroneMachine drone : drones.values()) {
            try {
                sendAndReceive("registerDrone|" + drone.getDroneId() + "|"
                        + drone.getX() + "|" + drone.getY() + "|"
                        + drone.getWaterRemaining() + "|" + listenPort);
                System.out.printf("DroneSubsystem: Drone %d registered (port %d)%n",
                        drone.getDroneId(), listenPort);
            } catch (Exception e) {
                System.err.printf("DroneSubsystem: failed to register Drone %d: %s%n",
                        drone.getDroneId(), e.getMessage());
            }
        }
    }

    // ==== Inbound packet routing ====

    /**
     * Parses an incoming Scheduler packet and routes it to the right drone.
     *
     * Supported messages:
     *   ASSIGN_MISSION|droneId|zoneId|eventType|severity|waterAssigned|secondsFromStart
     *   DECOMMISSION|droneId
     */
    private void handleIncoming(String msg) {
        String[] parts = msg.split("\\|");
        switch (parts[0]) {

            case "ASSIGN_MISSION": {
                // [1]=droneId [2]=zoneId [3]=eventType [4]=severity [5]=water [6]=seconds
                int droneId = Integer.parseInt(parts[1]);
                DroneMachine drone = drones.get(droneId);
                if (drone == null) {
                    System.err.println("DroneSubsystem: unknown droneId " + droneId);
                    return;
                }
                FireEvent base    = new FireEvent(
                        Integer.parseInt(parts[2]),
                        parts[3],
                        parts[4],
                        Integer.parseInt(parts[6]));
                FireEvent mission = new FireEvent(base, Integer.parseInt(parts[5]));
                System.out.printf("DroneSubsystem: Routing to Drone %d → Zone %d%n",
                        droneId, mission.getZoneId());
                drone.receiveMissionPush(mission);
                break;
            }

            case "INJECT_FAULT": {
                // Injects a fault into an already-active drone mid-action.
                // The sleepInterruptibly tick picks it up within 200ms.
                // [1]=droneId [2]=faultType
                int droneId = Integer.parseInt(parts[1]);
                DroneMachine drone = drones.get(droneId);
                if (drone == null) {
                    System.err.println("DroneSubsystem: unknown droneId " + droneId);
                    return;
                }
                FaultType fault = parts.length > 2
                        ? FaultType.from(parts[2]) : FaultType.NONE;
                System.out.printf(
                        "DroneSubsystem: Injecting %s fault into Drone %d mid-action%n",
                        fault, droneId);
                drone.injectFault(fault);
                break;
            }

            case "DECOMMISSION": {
                int droneId = Integer.parseInt(parts[1]);
                DroneMachine drone = drones.get(droneId);
                if (drone != null) {
                    drone.handleEvent(DroneMachine.droneEvents.DECOMMISSION);
                }
                break;
            }

            default:
                System.err.println("DroneSubsystem: ignored unexpected message: " + parts[0]);
        }
    }

    @Override
    public void run() {
        System.out.println("DroneSubsystem: Starting");

        // Start each drone's state machine on its own thread
        for (DroneMachine drone : drones.values()) {
            drone.setName("Drone-" + drone.getDroneId());
            drone.start();
        }

        // Register all drones with the Scheduler
        registerAllDrones();

        // Receive loop — routes Scheduler pushes to the right DroneMachine
        byte[] buf = new byte[BUFFER_SIZE];
        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
                handleIncoming(msg);
            } catch (SocketTimeoutException e) {
                // 200ms timeout — check if all drones are decommissioned
                boolean allDone = drones.values().stream()
                        .allMatch(d -> d.getDroneState()
                                == DroneMachine.DroneState.DECOMMISSIONED);
                if (allDone) break;
            } catch (SocketException e) {
                break;  // socket closed
            } catch (Exception e) {
                System.err.println("DroneSubsystem receive error: " + e.getMessage());
            }
        }

        socket.close();
        System.out.println("DroneSubsystem: Shut down");
    }
}
