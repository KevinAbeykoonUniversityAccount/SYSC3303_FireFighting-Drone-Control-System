import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;

/**
 * Listens on its own UDP port for a loadFile command from the GUI/Scheduler
 * side, then reads the incident CSV and dispatches events to the Scheduler.
 *
 * Start this process first (alongside SchedulerMain), then trigger it by
 * sending: loadFile|<absolute-path-to-csv>
 *
 * Input file format (4 columns):
 *   Time, EventType, ZoneOrDroneID, Severity
 *
 * FIRE rows  → receiveFireEvent  to Scheduler; ZoneOrDroneID is zone ID.
 * Fault rows → injectFaultEvent  to Scheduler; ZoneOrDroneID is drone ID.
 *
 * @author Rayyan Kashif (101274266)
 * @author Aryan Kumar Singh (101299776)
 */
public class FireIncidentSubsystem implements Runnable {

    /** Port this subsystem listens on for loadFile commands. */
    public static final int PORT = 6001;

    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_MS  = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int CLOCK_SPEED = 60;

    private final DatagramSocket sendSocket;   // ephemeral, used to talk to Scheduler
    private final DatagramSocket listenSocket; // bound to PORT, receives commands
    private final InetAddress    schedulerAddr;
    private final int            schedulerPort;
    private final String         inputFileName;
    private InetAddress loggerAddress;

    public FireIncidentSubsystem(String schedulerHost, int schedulerPort, String inputFileName) throws Exception {
        this.schedulerAddr = InetAddress.getByName(schedulerHost);
        this.schedulerPort = schedulerPort;
        this.inputFileName = inputFileName;
        this.sendSocket = new DatagramSocket();
        this.sendSocket.setSoTimeout(TIMEOUT_MS);
        this.listenSocket  = new DatagramSocket(PORT);
        this.loggerAddress = InetAddress.getLocalHost();
    }

    public FireIncidentSubsystem(String schedulerHost, int schedulerPort, // CHANGED
                                 String inputFileName, String loggerHost) throws Exception {
        this.schedulerAddr = InetAddress.getByName(schedulerHost);
        this.schedulerPort = schedulerPort;
        this.inputFileName = inputFileName;
        this.sendSocket = new DatagramSocket();
        this.sendSocket.setSoTimeout(TIMEOUT_MS);
        this.listenSocket  = new DatagramSocket(PORT);
        this.loggerAddress = InetAddress.getByName(loggerHost);
    }

    // ==== UDP helpers (talk to Scheduler) ====

    private String sendAndReceive(String message) throws Exception {
        byte[]         data    = message.getBytes();
        DatagramPacket sendPkt = new DatagramPacket(data, data.length,
                schedulerAddr, schedulerPort);
        byte[]         buf     = new byte[BUFFER_SIZE];
        DatagramPacket recvPkt = new DatagramPacket(buf, buf.length);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            sendSocket.send(sendPkt);
            try {
                sendSocket.receive(recvPkt);
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

  public void log(String msg) {
        byte[] event = msg.getBytes();
        try {
            sendSocket.send(new DatagramPacket(event, event.length, loggerAddress, EventLogger.DEFAULT_PORT));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
      
    // ==== Main loop: wait for loadFile commands ====

    @Override
    public void run() {
        log("FireSubsystem,STARTED");
        System.out.println("FireIncidentSubsystem: Listening on port " + PORT
                + " for loadFile commands...");
        byte[] buf = new byte[BUFFER_SIZE];

        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                listenSocket.receive(pkt);
                String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();

                if (msg.startsWith("loadFile|")) {
                    String filePath = msg.substring("loadFile|".length()).trim();
                    System.out.println("FireIncidentSubsystem: Received loadFile -> " + filePath);
                    Thread worker = new Thread(() -> processFile(filePath), "FireIncident-Worker");
                    worker.setDaemon(true);
                    worker.start();
                } else {
                    System.err.println("FireIncidentSubsystem: Unknown command: " + msg);
                }
            } catch (Exception e) {
                System.err.println("FireIncidentSubsystem listen error: " + e.getMessage());
            }
        }
        //log("FireSubsystem,ENDED");
    }

    // ==== File processing (previously the body of run()) ====

    private void processFile(String inputFileName) {
        System.out.println("FireIncidentSubsystem: Processing file: " + inputFileName);
        log("FireSubsystem,PROCESSING_FILE," + inputFileName);

        try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
            String  line;
            boolean clockStarted = false;
            boolean isFirstLine  = true;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                if (isFirstLine) { isFirstLine = false; continue; }  // skip header

                try {
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

                    // Support two column layouts:
                    //   Layout A (original): Time, EventType, ZoneOrDroneID, Severity[, ...]
                    //   Layout B (current):  Time, ZoneOrDroneID, EventType, Severity[, ...]
                    // Detect by checking whether parts[1] parses as an integer (Layout B) or not (Layout A).
                    String eventType;
                    int    zoneOrDroneId;
                    String severity;

                    boolean layoutB = parts[1].matches("\\d+");
                    if (layoutB) {
                        zoneOrDroneId = Integer.parseInt(parts[1]);
                        eventType     = parts[2].toUpperCase();
                        severity      = parts.length > 3 ? parts[3] : "";
                    } else {
                        eventType     = parts[1].toUpperCase();
                        zoneOrDroneId = Integer.parseInt(parts[2]);
                        severity      = parts.length > 3 ? parts[3] : "";
                    }

                    if (eventType.equals("FIRE")) {
                        FireEvent event = new FireEvent(zoneOrDroneId, "FIRE", severity, eventTimeSeconds);
                        System.out.printf("FireIncidentSubsystem: Sending Fire Event: %s%n", event);

                        sendAndReceive("receiveFireEvent|"
                                + event.getZoneId()         + "|"
                                + event.getEventType()       + "|"
                                + event.getSeverity().name() + "|"
                                + event.getSecondsFromStart());
                    } else {
                        FaultType faultType = FaultType.from(eventType);

                        System.out.printf(
                                "FireIncidentSubsystem: Sending Fault Event: %s -> Drone %d%n",
                                faultType, zoneOrDroneId);

                        sendAndReceive("injectFaultEvent|" + zoneOrDroneId + "|" + faultType.name());
                    }

                } catch (Exception e) {
                    System.err.println("FireIncidentSubsystem Error Parsing Line: " + line);
                    e.printStackTrace();
                }
            }

            System.out.println("FireIncidentSubsystem: All events from " + inputFileName + " dispatched.");
            log("FireSubsystem,FILE_PROCESSED," + inputFileName);

        } catch (IOException e) {
            System.err.println("FireIncidentSubsystem File Error: " + e.getMessage());
        }
    }
}
