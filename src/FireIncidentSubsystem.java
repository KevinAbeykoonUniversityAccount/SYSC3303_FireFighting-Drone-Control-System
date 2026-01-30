import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Reads an input file and creates fire events.
 * If an event is scheduled at a certain time, it waits until the simulation time reaches the scheduled time before sending the event.
 * Passes the fire events to the scheduler.
 */
public class FireIncidentSubsystem implements Runnable {
    private final Scheduler scheduler;
    private final String inputFileName;
    private final SimulationClock clock;

    public FireIncidentSubsystem(Scheduler scheduler, String inputFileName) {
        this.scheduler = scheduler;
        this.inputFileName = inputFileName;
        this.clock = SimulationClock.getInstance();
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        boolean isFirstLine = true; // Flag to skip header

        System.out.println("Starting FireIncidentSubsystem - Reading: " + inputFileName + "...\n\n");


        try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
            String line;

            while ((line = br.readLine()) != null) {
                //Ignore comments or empty lines
                if (line.trim().isEmpty() || line.startsWith("#")) continue;

                // Skip the header row
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                try {
                    //Parse inputs (time, zone, type, severity)
                    String[] parts = line.split("[,\\s]+");

                    // Convert HH:MM:SS to seconds
                    String timeStr = parts[0].trim();
                    String[] timeParts = timeStr.split(":");
                    int hours = Integer.parseInt(timeParts[0]);
                    int minutes = Integer.parseInt(timeParts[1]);
                    int seconds = Integer.parseInt(timeParts[2]);
                    int eventTimeSeconds = hours * 3600 + minutes * 60 + seconds;

                    // Calculate wait time based on simulation clock
                    long currentSimTime = clock.getSimulationTimeSeconds();
                    System.out.println(currentSimTime);
                    long timeToWait = eventTimeSeconds - currentSimTime;

                    if (timeToWait > 0) {
                        System.out.printf("FireIncidentSubsystem: Event scheduled at %d:%02d:%02d, current sim time: %d, waiting %d seconds%n",
                                hours, minutes, seconds, currentSimTime, timeToWait);
                        Thread.sleep(timeToWait * 1000); // Convert to milliseconds
                    }

                    // Parse the other parameters of the fire outbreak
                    int zoneId = Integer.parseInt(parts[1].trim());
                    String eventType = parts[2].trim();
                    String severity = parts[3].trim();

                    // Create a new event object that represents the real-time fire incident
                    FireEvent event = new FireEvent(zoneId, eventType, severity, eventTimeSeconds);

                    System.out.println("FireIncidentSubsystem Sending Event: " + event);
                    scheduler.receiveFireEvent(event);
                }
                catch (Exception e) {
                    System.err.println("FireIncidentSubsystem Error Parsing Line: " + line);
                    e.printStackTrace();
                }
            }
        }
        catch (IOException e) {
            System.err.println("FireIncidentSubsystem File Error: " + e.getMessage());
        }
    }
}
