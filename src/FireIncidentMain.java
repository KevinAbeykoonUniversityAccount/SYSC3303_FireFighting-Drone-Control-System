/**
 * Entry point for the FireIncidentSubsystem process. Start alongside SchedulerMain.
 *
 * Usage:   java FireIncidentMain [--host schedulerHost]
 * Example: java FireIncidentMain
 *          java FireIncidentMain --host 192.168.1.10
 *
 * The subsystem listens on UDP port 6001 for a loadFile command sent by the
 * GUI (Load Incident File + Start Simulation buttons). No CSV file is needed
 * at startup.
 */
public class FireIncidentMain {
    public static void main(String[] args) throws Exception {
        String host = "localhost";

        for (int i = 0; i < args.length; i++) {
            if ("--host".equals(args[i])) host = args[++i];
        }

        FireIncidentSubsystem subsystem =
                new FireIncidentSubsystem(host, Scheduler.PORT);
        new Thread(subsystem, "FireIncident").start();
        System.out.println("FireIncidentMain: ready on port "
                + FireIncidentSubsystem.PORT + ", waiting for loadFile command.");
    }
}
