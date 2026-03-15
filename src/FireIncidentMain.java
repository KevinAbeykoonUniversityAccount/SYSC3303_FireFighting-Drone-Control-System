/**
 * Entry point for the FireIncidentSubsystem process. Start after SchedulerMain.
 *
 * Usage:   java FireIncidentMain <inputFile> [--host schedulerHost]
 * Example: java FireIncidentMain fire_events.csv
 *          java FireIncidentMain fire_events.csv --host 192.168.1.10
 *
 * No local clock needed — FireIncidentSubsystem polls the scheduler's
 * clock directly via getTime UDP messages.
 */
public class FireIncidentMain {
    public static void main(String[] args) throws Exception {
        String host      = "localhost";
        String inputFile = null;

        for (int i = 0; i < args.length; i++) {
            if   ("--host".equals(args[i])) host = args[++i];
            else inputFile = args[i];
        }

        if (inputFile == null) {
            System.err.println("Usage: java FireIncidentMain <file> [--host H]");
            System.exit(1);
        }

        FireIncidentSubsystem subsystem =
                new FireIncidentSubsystem(host, Scheduler.PORT, inputFile);
        Thread t = new Thread(subsystem, "FireIncident");
        t.start();
        t.join();
        System.out.println("FireIncidentMain: all events dispatched.");
    }
}
