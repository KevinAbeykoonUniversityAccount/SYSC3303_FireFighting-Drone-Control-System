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
        System.out.println("Starting FireIncidentSubsystem - Reading: " + inputFileName + "...\n\n");

        try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
            String line;

            while ((line = br.readLine()) != null) {
                //Ignore comments or empty lines
                if (line.trim().isEmpty() || line.startsWith("#")) continue;

                try {
                    //Parse inputs (time, zone, type, severity)
                    String[] parts = line.split(","); //Assuming CSV
                    int eventTimeSeconds = Integer.parseInt(parts[0].trim());

                    // Calculate wait time based on simulation clock
                    long currentSimTime = clock.getSimulationTimeSeconds();
                    long simTimeToWait = eventTimeSeconds - currentSimTime;

                    if (simTimeToWait > 0) {
                        System.out.printf("FireIncidentSubsystem: Event scheduled at T = %d, current sim time: %d, waiting %d seconds%n",
                                eventTimeSeconds, currentSimTime, simTimeToWait);
                        Thread.sleep(clock.scaleSimulatedToReal(simTimeToWait * 1000)); //Convert to milliseconds
                    }

                    // Parse the other parameters of the fire outbreak
                    int zoneId = Integer.parseInt(parts[1].trim());
                    String eventType = parts[2].trim();
                    String severity = parts[3].trim();

                    // Create a new event object that represents the real-time fire incident
                    FireEvent event = new FireEvent(zoneId, eventType, severity, eventTimeSeconds);

                    //Send event to scheduler
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
