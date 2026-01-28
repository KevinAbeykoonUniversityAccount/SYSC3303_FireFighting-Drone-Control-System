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

    public FireIncidentSubsystem(Scheduler scheduler, String inputFileName) {
        this.scheduler = scheduler;
        this.inputFileName = inputFileName;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();

        System.out.println("Starting FireIncidentSubsystem - Reading: " + inputFileName + "...");

        try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
            String line;

            while ((line = br.readLine()) != null) {
                //Ignore comments or empty lines
                if (line.trim().isEmpty() || line.startsWith("#")) continue;

                try {
                    //Parse inputs (time, zone, type, severity)
                    String[] parts = line.split("\\s+");
                    int eventTime = Integer.parseInt(parts[0]);
                    int zoneId = Integer.parseInt(parts[1]);
                    String eventType = parts[2];
                    String severity  = parts[3];

                    //Create new fire event
                    FireEvent event = new FireEvent(zoneId, eventType, severity, eventTime);

                    //Real time simulation logic
                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = (currentTime - startTime) / 1000; //Convert to seconds
                    long timeToWait = eventTime - elapsedTime;

                    if (timeToWait > 0) {
                        System.out.println("FireIncidentSubsystem Waiting " + timeToWait + "s for next event...");
                        Thread.sleep(timeToWait * 1000);
                    }

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
