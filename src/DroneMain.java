import java.util.*;

/**
 * Entry point for the Drone process. Start after SchedulerMain.
 *
 * Usage:   java DroneMain <id1> [id2 ...] [--host schedulerHost]
 * Example: java DroneMain 1 2 3
 *          java DroneMain 1 2 3 --host 192.168.1.10
 * 
 * Usage:   java DroneMain --count <numDrones> [--host schedulerHost]
 * Example: java DroneMain --count 20
 *          java DroneMain --count 20 --host 192.168.1.1
 */
public class DroneMain {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int count = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    host = args[++i];
                    break;
                case "--count":
                    count = Integer.parseInt(args[++i]);
                    break;
            }
        }

        // If the user inputs an invalid command
        if (count <= 0) {
            System.err.println("Usage: java DroneMain --count <numDrones> [--host schedulerHost]");
            System.err.println("Example: java DroneMain --count 20");
            System.exit(1);
        }

        // Generate IDs 1 through count
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ids.add(i);
        }

        // DroneSubsystem owns the socket and manages all DroneMachine instances
        DroneSubsystem subsystem = new DroneSubsystem(ids, host, Scheduler.PORT);
        subsystem.setName("DroneSubsystem");
        subsystem.start();

        System.out.printf("DroneSubsystem started — managing %d drones (IDs 1-%d) → %s:%d%n",
                count, count, host, Scheduler.PORT);
    }
}