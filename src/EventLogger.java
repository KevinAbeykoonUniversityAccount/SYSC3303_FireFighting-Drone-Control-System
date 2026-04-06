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

public class EventLogger implements Runnable{
    private class EventLog {
        final long time;
        final String entity;
        final String code;
        final String[] data;

        public EventLog(long time, String entity, String code, String... data) {
            this.time = time;
            this.entity = entity;
            this.code = code;
            this.data = data;
        }

        public String getEntity() {
            return entity;
        }

        public String getCode() {
            return code;
        }

        public String toString() {
            Instant instantTime = Instant.ofEpochMilli(time);
            String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault()).format(instantTime);

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

    public EventLogger() throws SocketException, UnknownHostException {
        this.port = DEFAULT_PORT;
        this.reciever = new DatagramSocket(port);
    }

    public EventLog recieve() {
        byte[] buffer = new byte[100];
        EventLog log;
        try {
            DatagramPacket event = new DatagramPacket(buffer, buffer.length);
            reciever.receive(event);
            String[] eventStr = new String(event.getData(), 0, event.getLength()).split(",");
            if (eventStr.length == 2) log = new EventLog(System.currentTimeMillis(), eventStr[0], eventStr[1]);
            else log = new EventLog(System.currentTimeMillis(), eventStr[0], eventStr[1], eventStr[2]);
            return log;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void writeLog(String event) {
        try {
            FileWriter writer = new FileWriter("log.txt", true);
            writer.write(event + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clearLogFile() {
        try {
            FileWriter writer = new FileWriter("log.txt");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkFlags(EventLog event) {
        if (event.getEntity().equals("Scheduler") && event.code.equals("ENDED")) schedulerRunning = false;
        if (event.getEntity().equals("FireSubsystem") && event.code.equals("ENDED")) fireSystemRunning = false;
        if (event.getEntity().equals("DroneSubsystem") && event.code.equals("ENDED")) droneSystemRunning = false;
    }

    public void displayMetrics() {
        List<String> logLines = readLogLines();
        List<EventLog> events = parseLogLines(logLines);

        System.out.println("\nPERFORMANCE METRICS:\n");
        displayFireIncidentMetrics(events);
        displayDroneMetrics(events);
        displayOverallMetrics(events);
    }

    private List<String> readLogLines() {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("log.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        } catch (IOException e) { e.printStackTrace(); }
        return lines;
    }

    private List<EventLog> parseLogLines(List<String> lines) {
        List<EventLog> events = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
        for (String line : lines) {
            int start = line.indexOf('['), end = line.lastIndexOf(']');
            if (start < 0 || end < 0) continue;
            String[] parts = line.substring(start + 1, end).split(", ", 4);
            if (parts.length < 3) continue;
            long ts = Instant.from(fmt.parse(parts[0])).toEpochMilli();
            String[] extra = parts.length == 4 ? new String[]{parts[3]} : new String[0];
            events.add(new EventLog(ts, parts[1], parts[2], extra));
        }
        return events;
    }

    private void displayFireIncidentMetrics(List<EventLog> events) {
        Map<String, Long> lastDetected = new HashMap<>();
        List<long[]> responseTimes = new ArrayList<>();  // [responseMs, durationMs]
        String[] droneNames = {"Drone 1", "Drone 2", "Drone 3"};

        System.out.println("Fire Incident Metrics:");
        System.out.printf("%-8s %-12s %-18s %-18s%n", "Zone", "Response(ms)", "Extinguish(ms)", "Dispatch lag(ms)");

        for (EventLog e : events) {
            if (e.getCode().equals("FIRE_DETECTED") && e.data.length > 0) {
                lastDetected.put(e.data[0], e.time);
            }
            if (e.getCode().equals("FIRE_EXTINGUISHED") && e.data.length > 0) {
                String zone = e.data[0];
                if (!lastDetected.containsKey(zone)) continue;
                long detectedAt = lastDetected.remove(zone);
                long extinguishDuration = e.time - detectedAt;

                // Find first ONROUTE event after fire detected
                long dispatchLag = -1;
                for (EventLog de : events) {
                    if (de.time >= detectedAt && de.getCode().equals("STATE_CHANGE")
                            && de.data.length > 0 && de.data[0].equals("ONROUTE")) {
                        dispatchLag = de.time - detectedAt;
                        break;
                    }
                }
                responseTimes.add(new long[]{dispatchLag, extinguishDuration});
                System.out.printf("%-8s %-12s %-18s %-18s%n",
                        zone,
                        dispatchLag >= 0 ? dispatchLag + "ms" : "N/A",
                        extinguishDuration + "ms",
                        dispatchLag >= 0 ? dispatchLag + "ms" : "N/A");
            }
        }

        if (!responseTimes.isEmpty()) {
            double avgDispatch = responseTimes.stream()
                    .filter(r -> r[0] >= 0).mapToLong(r -> r[0]).average().orElse(0);
            double avgExtinguish = responseTimes.stream()
                    .mapToLong(r -> r[1]).average().orElse(0);
            System.out.printf("%nAvg dispatch lag:       %.0fms%n", avgDispatch);
            System.out.printf("Avg time to extinguish: %.0fms%n%n", avgExtinguish);
        }
    }

    private void displayDroneMetrics(List<EventLog> events) {
        // Discover all unique drone entities from the log
        List<String> drones = events.stream()
                .map(EventLog::getEntity)
                .filter(name -> name.startsWith("Drone "))
                .distinct()
                .sorted()
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

    private void displayOverallMetrics(List<EventLog> events) {
        long logStart = events.get(0).time;
        long logEnd   = events.get(events.size() - 1).time;

        // First fire event to last extinguish
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

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.2fs", ms / 1000.0);
    }

    @Override
    public void run() {
        clearLogFile();
        System.out.println("Starting Event Logger");
        while ((schedulerRunning | fireSystemRunning | droneSystemRunning) && !Thread.currentThread().isInterrupted()) {
            EventLog event = recieve();
            writeLog(event.toString());
            checkFlags(event);
        }
        displayMetrics();
    }
}
