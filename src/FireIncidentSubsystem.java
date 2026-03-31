import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;

/**
 * Reads the input CSV and dispatches events to the Scheduler over UDP.
 *
 * Input file format (4 columns):
 *   Time, EventType, ZoneOrDroneID, Severity
 *
 * FIRE rows    → receiveFireEvent   sent to Scheduler; ZoneOrDroneID is zone ID.
 * Fault rows   → injectFaultEvent   sent to Scheduler; ZoneOrDroneID is drone ID.
 *
 * Fault event types: DRONE_STUCK, NOZZLE_FAULT
 *
 * Example:
 *   01:00:00,FIRE,1,HIGH
 *   01:25:00,DRONE_STUCK,1,
 *   01:45:00,NOZZLE_FAULT,2,
 *
 * @author Rayyan Kashif (101274266)
 * @author Aryan Kumar Singh (101299776)
 */
public class FireIncidentSubsystem implements Runnable {

    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_MS  = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int CLOCK_SPEED = 60;

    private final DatagramSocket socket;
    private final InetAddress    schedulerAddr;
    private final int            schedulerPort;
    private final String         inputFileName;

    public FireIncidentSubsystem(String schedulerHost, int schedulerPort,
                                 String inputFileName) throws Exception {
        this.schedulerAddr = InetAddress.getByName(schedulerHost);
        this.schedulerPort = schedulerPort;
        this.inputFileName = inputFileName;
        this.socket        = new DatagramSocket();
        this.socket.setSoTimeout(TIMEOUT_MS);
    }

    // ==== UDP helpers ====

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
                System.err.printf("FireIncidentSubsystem: timeout (attempt %d/%d)%n",
                        attempt, MAX_RETRIES);
            }
        }
        throw new Exception("No response after " + MAX_RETRIES + " attempts");
    }

    private long getSchedulerTime() {
        try {
            return Long.parseLong(sendAndReceive("getTime"));
        } catch (Exception e) {
            System.err.println("FireIncidentSubsystem: could not get scheduler time");
            return 0;
        }
    }

    @Override
    public void run() {
        System.out.println("Starting FireIncidentSubsystem - Reading: " + inputFileName + "...\n");

        try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
            String  line;
            boolean clockStarted = false;
            boolean isFirstLine  = true;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                if (isFirstLine) { isFirstLine = false; continue; }  // skip header

                try {
                    // Split on comma; allow optional whitespace around each token
                    String[] parts = line.split(",", -1);
                    for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();

                    // Column 0: Time
                    String[] timeParts = parts[0].split(":");
                    int hours   = Integer.parseInt(timeParts[0]);
                    int minutes = Integer.parseInt(timeParts[1]);
                    int seconds = Integer.parseInt(timeParts[2]);
                    int eventTimeSeconds = hours * 3600 + minutes * 60 + seconds;

                    // Start the Scheduler's clock on the first event
                    if (!clockStarted) {
                        sendAndReceive("startClock|" + eventTimeSeconds + "|" + CLOCK_SPEED);
                        clockStarted = true;
                    }

                    // Wait until simulation time reaches this event
                    while (getSchedulerTime() < eventTimeSeconds) {
                        Thread.sleep(200);
                    }

                    // Column 1: EventType  (FIRE | DRONE_STUCK | NOZZLE_FAULT)
                    String eventType = parts[1].toUpperCase();

                    if (eventType.equals("FIRE")) {
                        // Column 2: Zone ID   Column 3: Severity
                        int    zoneId   = Integer.parseInt(parts[2]);
                        String severity = parts[3];

                        FireEvent event = new FireEvent(zoneId, "FIRE", severity, eventTimeSeconds);
                        System.out.printf("FireIncidentSubsystem: Sending Fire Event: %s%n", event);

                        sendAndReceive("receiveFireEvent|"
                                + event.getZoneId()          + "|"
                                + event.getEventType()        + "|"
                                + event.getSeverity().name()  + "|"
                                + event.getSecondsFromStart());

                    } else {
                        // Fault event — Column 2 is the target drone ID
                        int       droneId   = Integer.parseInt(parts[2]);
                        FaultType faultType = FaultType.from(eventType);

                        System.out.printf(
                                "FireIncidentSubsystem: Sending Fault Event: %s -> Drone %d%n",
                                faultType, droneId);

                        sendAndReceive("injectFaultEvent|" + droneId + "|" + faultType.name());
                    }

                } catch (Exception e) {
                    System.err.println("FireIncidentSubsystem Error Parsing Line: " + line);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("FireIncidentSubsystem File Error: " + e.getMessage());
        }

        socket.close();
    }
}
