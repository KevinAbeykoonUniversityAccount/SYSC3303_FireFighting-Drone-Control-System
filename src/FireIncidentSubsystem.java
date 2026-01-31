import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Reads an input file and creates fire events. If an event is scheduled at 
 * a certain time, it waits until the simulation time reaches the scheduled time 
 * before sending the event. Passes the fire events to the scheduler.
 *
 * @author Rayyan Kashif (101274266)
 * @author Aryan Kumar Singh (101299776)
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

                    // Use the clock's sleep method to wait until event time
                    if (eventTimeSeconds > clock.getSimulationTimeSeconds()) {
                        System.out.printf("FireIncidentSubsystem: Event scheduled at %s, current sim time: %s, waiting...%n",
                                String.format("%02d:%02d:%02d", hours, minutes, seconds),
                                clock.getFormattedTime());

                        clock.sleepUntilSimulationTime(eventTimeSeconds);
                    }

                    // Parse the other parameters of the fire outbreak
                    int zoneId = Integer.parseInt(parts[1].trim());
                    String eventType = parts[2].trim();
                    String severity = parts[3].trim();

                    // Create a new event object that represents the real-time fire incident
                    FireEvent event = new FireEvent(zoneId, eventType, severity, eventTimeSeconds);

                    System.out.printf("FireIncidentSubsystem [%s]: Sending Event: %s%n",
                            clock.getFormattedTime(), event);
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
