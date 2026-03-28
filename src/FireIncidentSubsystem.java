import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;

/**
 * Reads an input file and creates fire events. If an event is scheduled at
 * a certain time, it waits until the simulation time reaches the scheduled time
 * before sending the event. Passes the fire events to the scheduler.
 *
 * Iteration 3 changes (marked with // CHANGED):
 *   - Holds a DatagramSocket + scheduler address instead of a Scheduler reference
 *   - scheduler.receiveFireEvent(event) replaced with a direct UDP send
 *   - Waits for the correct time by polling the scheduler's clock over UDP
 *
 * @author Rayyan Kashif (101274266)
 * @author Aryan Kumar Singh (101299776)
 */
public class FireIncidentSubsystem implements Runnable {

    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_MS  = 5000;
    private static final int MAX_RETRIES = 3;
    private static final int CLOCK_SPEED = 60;

    // CHANGED: socket fields instead of Scheduler reference
    private final DatagramSocket socket;
    private final InetAddress    schedulerAddr;
    private final int            schedulerPort;
    private final String         inputFileName;

    public FireIncidentSubsystem(String schedulerHost, int schedulerPort, // CHANGED
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


    /**
     * Asks the Scheduler for its current simulation time in seconds.
     * This is how FireIncidentSubsystem stays in sync across processes.
     */
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
        System.out.println("Starting FireIncidentSubsystem - Reading: " + inputFileName + "...\n\n");

        try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
            String line;
            boolean clockStarted = false;
            boolean isFirstLine  = true;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                if (isFirstLine) { isFirstLine = false; continue; }

                try {
                    String[] parts = line.split("[,\\s]+");

                    String   timeStr   = parts[0].trim();
                    String[] timeParts = timeStr.split(":");
                    int hours   = Integer.parseInt(timeParts[0]);
                    int minutes = Integer.parseInt(timeParts[1]);
                    int seconds = Integer.parseInt(timeParts[2]);
                    int eventTimeSeconds = hours * 3600 + minutes * 60 + seconds;


                    // Start the Scheduler's clock at the first event's timestamp
                    // so no real time is wasted between program startups
                    if (!clockStarted) {
                        sendAndReceive("startClock|" + eventTimeSeconds + "|" + CLOCK_SPEED);
                        clockStarted = true;
                    }

                    // Wait until the Scheduler's clock reaches this event's time
                    while (getSchedulerTime() < eventTimeSeconds) {
                        Thread.sleep(200);
                    }

                    int    zoneId    = Integer.parseInt(parts[1].trim());
                    String eventType = parts[2].trim();
                    String severity  = parts[3].trim();
                    FaultType fault  = parts.length > 4 ? FaultType.from(parts[4].trim()) : FaultType.NONE;

                    FireEvent event = new FireEvent(zoneId, eventType, severity, eventTimeSeconds, fault);

                    System.out.printf("FireIncidentSubsystem: Sending Event: %s%n", event);

                    // CHANGED: was scheduler.receiveFireEvent(event)
                    sendAndReceive("receiveFireEvent|"
                            + event.getZoneId()            + "|"
                            + event.getEventType()          + "|"
                            + event.getSeverity().name()    + "|"
                            + event.getSecondsFromStart()   + "|"
                            + event.getFaultType().name());

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