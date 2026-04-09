import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listens for UDP event packets from the drone fire-suppression simulation,
 * writes them to a log file, and prints performance metrics when the
 * simulation ends.
 *
 * <p>Each subsystem (Scheduler, FireSubsystem, DroneSubsystem) sends an
 * {@code ENDED} event when it shuts down. Once all three have signalled,
 * the logger stops receiving, computes metrics from the completed log, and
 * prints them to stdout.</p>
 *
 * <p>Implements {@link Runnable} so it can be run on a dedicated thread.</p>
 */
public class EventLogger implements Runnable{
    /**
     * Represents a single parsed event from the simulation.
     *
     * <p>Each event has a timestamp, the name of the entity that produced it,
     * an event code, and an optional array of extra data fields.</p>
     */
    private class EventLog {
        final long time;
        final String entity;
        final String code;
        final String[] data;

        /**
         * Constructs an EventLog with the given fields.
         *
         * @param time   epoch-millisecond timestamp
         * @param entity name of the producing entity
         * @param code   event code
         * @param data   zero or more additional data fields
         */
        public EventLog(long time, String entity, String code, String... data) {
            this.time = time;
            this.entity = entity;
            this.code = code;
            this.data = data;
        }

        /**
         * Returns the name of the entity that produced this event.
         *
         * @return entity name
         */
        public String getEntity() {
            return entity;
        }

        /**
         * Returns the event code for this log entry.
         *
         * @return event code string
         */
        public String getCode() {
            return code;
        }

        /**
         * Returns a formatted log string suitable for writing to {@code log.txt}.
         *
         * <p>Format: {@code Event log: [HH:MM:SS, entity, code, data...]}</p>
         *
         * @return formatted log line
         */
        public String toString() {
            String formattedTime = SimulationClock.formatTime(time);

            String logString = "Event log: [" + formattedTime + ", " + entity + ", " + code;

            for (String thing : data) {
                logString += ", " + thing;
            }

            logString += "]";

            return  logString;
        }
    }

    public final static int DEFAULT_PORT = 9000;

    private DatagramSocket reciever;
    private int port;

    private boolean schedulerRunning = true;
    private boolean fireSystemRunning = true;
    private boolean droneSystemRunning = true;

    /**
     * Creates an EventLogger bound to {@link #DEFAULT_PORT}.
     *
     * @throws SocketException      if the UDP socket cannot be created or bound
     * @throws UnknownHostException if the local host cannot be resolved
     */
    public EventLogger() throws SocketException, UnknownHostException {
        this.port = DEFAULT_PORT;
        this.reciever = new DatagramSocket(port);
    }

    /**
     * Blocks until a UDP packet arrives, then parses and returns it as an {@link EventLog}.
     *
     * <p>Expects comma-separated packets in the form {@code entity,code} or
     * {@code entity,code,data}. Returns {@code null} if an {@link IOException} occurs.</p>
     *
     * @return the parsed {@link EventLog}, or {@code null} on error
     */
    public EventLog recieve() {
        byte[] buffer = new byte[100];
        EventLog log;
        try {
            DatagramPacket event = new DatagramPacket(buffer, buffer.length);
            reciever.receive(event);
            String[] eventStr = new String(event.getData(), 0, event.getLength()).split(",");

            if (eventStr[0].equals("printMetrics")) {
                displayMetrics();
                return null;
            }

            if (eventStr.length == 3) log = new EventLog(Long.parseLong(eventStr[0]), eventStr[1], eventStr[2]);
            else if (eventStr.length == 4) log = new EventLog(Long.parseLong(eventStr[0]), eventStr[1], eventStr[2], eventStr[3]);
            else log = new EventLog(Long.parseLong(eventStr[0]), eventStr[1], eventStr[2], eventStr[3], eventStr[4]);
            return log;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Appends a single log line to {@code log.txt}.
     *
     * @param event the formatted log string to write
     */
    public void writeLog(String event) {
        if (event == null) return;

        try {
            FileWriter writer = new FileWriter("log.txt", true);
            writer.write(event + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Truncates {@code log.txt} to zero bytes, discarding any previous run's data.
     * Called at the start of each simulation run.
     */
    public void clearLogFile() {
        try {
            FileWriter writer = new FileWriter("log.txt");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks whether the given event signals that a subsystem has finished,
     * and clears the corresponding running flag if so.
     *
     * <p>The main loop in {@link #run()} continues only while at least one
     * running flag is {@code true}.</p>
     *
     * @param event the event to inspect
     */
    public void checkFlags(EventLog event) {
        if (event == null) return;
        if (event.getEntity().equals("Scheduler") && event.code.equals("ENDED")) schedulerRunning = false;
        if (event.getEntity().equals("FireSubsystem") && event.code.equals("ENDED")) fireSystemRunning = false;
        if (event.getEntity().equals("DroneSubsystem") && event.code.equals("ENDED")) droneSystemRunning = false;
    }

    /**
     * Reads the completed log file and prints all performance metrics to stdout.
     *
     * <p>Delegates to {@link #displayFireIncidentMetrics}, {@link #displayDroneMetrics},
     * and {@link #displayOverallMetrics}.</p>
     */
    public void displayMetrics() {
        List<String> logLines = readLogLines();
        List<EventLog> events = parseLogLines(logLines);

        System.out.println("\nPERFORMANCE METRICS:\n");
        displayFireIncidentMetrics(events);
        displayDroneMetrics(events);
        displayOverallMetrics(events);
    }

    /**
     * Reads all lines from {@code log.txt} and returns them as a list.
     *
     * @return list of raw log lines; empty if the file cannot be read
     */
    private List<String> readLogLines() {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("log.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        } catch (IOException e) { e.printStackTrace(); }
        return lines;
    }

    /**
     * Parses a list of raw log lines into {@link EventLog} objects.
     *
     * <p>Expects lines in the format:
     * {@code Event log: [HH:MM:SS, entity, code, data?]}
     * Lines that do not match this format are silently skipped.</p>
     *
     * @param lines raw lines read from the log file
     * @return ordered list of parsed {@link EventLog} entries
     */
    private List<EventLog> parseLogLines(List<String> lines) {
        List<EventLog> events = new ArrayList<>();
        for (String line : lines) {
            int start = line.indexOf('['), end = line.lastIndexOf(']');
            if (start < 0 || end < 0) continue;
            String[] parts = line.substring(start + 1, end).split(", ");
            if (parts.length < 3) continue;
            long ts = parseSimTime(parts[0]);
            if (ts < 0) continue;
            String[] extra = parts.length > 3
                    ? java.util.Arrays.copyOfRange(parts, 3, parts.length)
                    : new String[0];
            events.add(new EventLog(ts, parts[1], parts[2], extra));
        }
        return events;
    }

    /**
     * Parses a simulation timestamp in {@code HH:MM:SS} format into milliseconds
     * elapsed since time zero.
     *
     * @param timeStr timestamp string in {@code HH:MM:SS} format
     * @return elapsed milliseconds, or {@code -1} if the string cannot be parsed
     */
    private long parseSimTime(String timeStr) {
        try {
            String[] parts = timeStr.trim().split(":");
            if (parts.length != 3) return -1;
            long hours   = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            return ((hours * 60 + minutes) * 60 + seconds) * 1000L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Prints a per-incident table of fire response times and extinguish durations,
     * followed by averages across all incidents.
     *
     * <p>For each fire (identified by a {@code FIRE_DETECTED} event), this method computes:</p>
     * <ul>
     *   <li><b>Response time</b> — time from detection to the moment the <em>first</em> drone
     *       transitions to {@code EXTINGUISHING} for that zone. Recorded once and never
     *       updated even if that drone leaves and more drones are dispatched later.</li>
     *   <li><b>Extinguish duration</b> — time from detection to the matching
     *       {@code FIRE_EXTINGUISHED} event.</li>
     * </ul>
     *
     * <p>There is exactly one response time entry per {@code FIRE_DETECTED} event.</p>
     *
     * @param events ordered list of all parsed log events
     */
    private void displayFireIncidentMetrics(List<EventLog> events) {
        Map<String, List<Long>> detectedTimes = new HashMap<>();
        Map<String, List<Long>> responseTimes = new HashMap<>();

        List<Object[]> incidents = new ArrayList<>();

        for (EventLog e : events) {
            if (e.getCode().equals("FIRE_DETECTED") && e.data.length > 0) {
                String zone = e.data[0];
                detectedTimes.computeIfAbsent(zone, k -> new ArrayList<>()).add(e.time);
                responseTimes.computeIfAbsent(zone, k -> new ArrayList<>()).add(-1L);
            }

            if (e.getCode().equals("STATE_CHANGE") && e.data.length > 1
                    && e.data[0].equals("EXTINGUISHING")) {
                String zone = e.data[1];
                List<Long> detections = detectedTimes.get(zone);
                List<Long> responses  = responseTimes.get(zone);
                if (detections != null) {
                    for (int i = 0; i < responses.size(); i++) {
                        if (responses.get(i) == -1L) {
                            responses.set(i, e.time - detections.get(i));
                            break;
                        }
                    }
                }
            }

            if (e.getCode().equals("FIRE_EXTINGUISHED") && e.data.length > 0) {
                String zone = e.data[0];
                List<Long> detections = detectedTimes.get(zone);
                List<Long> responses  = responseTimes.get(zone);
                if (detections == null || detections.isEmpty()) continue;
                long detectedAt = detections.remove(0);
                long responseMs = responses.remove(0);
                long durationMs = e.time - detectedAt;
                incidents.add(new Object[]{zone, responseMs, durationMs});
            }
        }

        System.out.println("Fire Incident Metrics:");
        System.out.printf("%-10s %-16s %-18s%n", "Zone", "Response time", "Extinguish duration");

        for (Object[] inc : incidents) {
            String zone       = (String) inc[0];
            long   responseMs = (long)   inc[1];
            long   durationMs = (long)   inc[2];
            System.out.printf("%-10s %-16s %-18s%n",
                    zone,
                    responseMs >= 0 ? formatDuration(responseMs) : "N/A",
                    formatDuration(durationMs));
        }

        if (!incidents.isEmpty()) {
            double avgResponse = incidents.stream()
                    .mapToLong(r -> (long) r[1]).filter(r -> r >= 0).average().orElse(0);
            double avgExtinguish = incidents.stream()
                    .mapToLong(r -> (long) r[2]).average().orElse(0);
            System.out.printf("%nTotal fires:             %d%n", incidents.size());
            System.out.printf("Avg response time:       %s%n", formatDuration((long) avgResponse));
            System.out.printf("Avg extinguish duration: %s%n%n", formatDuration((long) avgExtinguish));
        }
    }

    /**
     * Prints a per-drone breakdown of mission count, total flight time, total idle time,
     * and recharge cycles, followed by the average idle time across all drones.
     *
     * <p>Drone names are discovered dynamically from the log — any entity whose name
     * starts with {@code "Drone "} is included. Flight time accumulates time spent in
     * the {@code ONROUTE} and {@code EXTINGUISHING} states; idle time accumulates time
     * spent in the {@code IDLE} state.</p>
     *
     * @param events ordered list of all parsed log events
     */
    private void displayDroneMetrics(List<EventLog> events) {
        List<String> drones = events.stream()
                .map(EventLog::getEntity)
                .filter(name -> name.startsWith("Drone "))
                .distinct()
                .sorted(java.util.Comparator.comparingInt(
                        s -> Integer.parseInt(s.substring(6))))
                .toList();

        long logStart = events.get(0).time;
        long logEnd   = events.get(events.size() - 1).time;

        System.out.println("Drone Performance Metrics:");
        System.out.printf("%-10s %-12s %-12s %-12s %-10s%n",
                "Drone", "Missions", "FlightTime", "IdleTime", "Recharges");

        long totalIdle = 0;
        for (String drone : drones) {
            List<EventLog> droneEvents = events.stream()
                    .filter(e -> e.getEntity().equals(drone) && e.getCode().equals("STATE_CHANGE"))
                    .toList();

            long flightTime = 0, idleTime = 0;
            int missions = 0, recharges = 0;
            long stateStart = logStart;
            String currentState = "IDLE";

            for (EventLog e : droneEvents) {
                long dur = e.time - stateStart;
                if (currentState.equals("ONROUTE") || currentState.equals("EXTINGUISHING")) flightTime += dur;
                if (currentState.equals("IDLE")) idleTime += dur;
                if (e.data[0].equals("ONROUTE")) missions++;
                if (e.data[0].equals("REFILLING_AND_RECHARGING")) recharges++;
                currentState = e.data[0];
                stateStart = e.time;
            }
            long remaining = logEnd - stateStart;
            if (currentState.equals("IDLE")) idleTime += remaining;

            totalIdle += idleTime;
            System.out.printf("%-10s %-12d %-12s %-12s %-10d%n",
                    drone, missions,
                    formatDuration(flightTime),
                    formatDuration(idleTime),
                    recharges);
        }

        if (!drones.isEmpty()) {
            System.out.printf("%nAvg drone idle time: %s%n%n", formatDuration(totalIdle / drones.size()));
        }
    }

    /**
     * Prints high-level simulation summary metrics including total runtime,
     * time from first fire detected to last fire extinguished, and overall
     * fire detection and extinguish counts.
     *
     * @param events ordered list of all parsed log events
     */
    private void displayOverallMetrics(List<EventLog> events) {
        long logStart = events.get(0).time;
        long logEnd   = events.get(events.size() - 1).time;

        long firstFire = events.stream()
                .filter(e -> e.getCode().equals("FIRE_DETECTED"))
                .mapToLong(e -> e.time).min().orElse(logStart);
        long lastExtinguish = events.stream()
                .filter(e -> e.getCode().equals("FIRE_EXTINGUISHED"))
                .mapToLong(e -> e.time).max().orElse(logEnd);

        long totalFires = events.stream().filter(e -> e.getCode().equals("FIRE_DETECTED")).count();
        long totalExtinguished = events.stream().filter(e -> e.getCode().equals("FIRE_EXTINGUISHED")).count();

        System.out.println("Overall Simulation Metrics:");
        System.out.println("Total simulation time:  " + formatDuration(logEnd - logStart));
        System.out.println("First fire → last out:  " + formatDuration(lastExtinguish - firstFire));
        System.out.println("Total fires detected:   " + totalFires);
        System.out.println("Total fires extinguished: " + totalExtinguished);
    }

    /**
     * Formats a millisecond duration as a human-readable string.
     *
     * <ul>
     *   <li>Under 1 second: expressed in milliseconds, e.g. {@code "450ms"}</li>
     *   <li>Under 1 minute: expressed in seconds with one decimal place, e.g. {@code "12.5s"}</li>
     *   <li>Under 1 hour: expressed as minutes and seconds, e.g. {@code "3m 20s"}</li>
     *   <li>1 hour or more: expressed as hours, minutes, and seconds, e.g. {@code "1h 3m 20s"}</li>
     * </ul>
     *
     * @param ms duration in milliseconds
     * @return formatted duration string
     */
    private String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        if (totalSeconds < 60) return totalSeconds + "s";
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60;
        if (totalMinutes < 60) return String.format("%dm %02ds", totalMinutes, seconds);
        long minutes = totalMinutes % 60;
        long hours   = totalMinutes / 60;
        return String.format("%dh %02dm %02ds", hours, minutes, seconds);
    }

    /**
     * Entry point for the logger thread.
     *
     * <p>Clears the log file, then loops receiving UDP events and writing each to disk
     * until all three subsystems have sent {@code ENDED} events or the thread is
     * interrupted. Once the loop exits, {@link #displayMetrics()} is called to print
     * the final performance report.</p>
     */
    @Override
    public void run() {
        clearLogFile();
        System.out.println("Starting Event Logger");
        while ((schedulerRunning | fireSystemRunning | droneSystemRunning) && !Thread.currentThread().isInterrupted()) {
            EventLog event = recieve();
            if (event != null) {
                writeLog(event.toString());
                checkFlags(event);
            }
        }
        displayMetrics();
    }
}
