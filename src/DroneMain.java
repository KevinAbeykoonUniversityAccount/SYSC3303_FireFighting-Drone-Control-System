import java.util.*;

/**
 * Entry point for the Drone process. Start after SchedulerMain.
 *
 * Usage:   java DroneMain <id1> [id2 ...] [--host schedulerHost]
 * Example: java DroneMain 1 2 3
 *          java DroneMain 1 2 3 --host 192.168.1.10
 */
public class DroneMain {
    public static void main(String[] args) throws Exception {
        String       host  = "localhost";
        int          speed = 60;
        List<Integer> ids  = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            if ("--host".equals(args[i])) host = args[++i];
            else ids.add(Integer.parseInt(args[i]));
        }

        if (ids.isEmpty()) {
            System.err.println("Usage: java DroneMain <id1> [id2 ...] [--host schedulerHost]");
            System.exit(1);
        }

        SimulationClock clock = SimulationClock.getInstance();
        clock.setClockSpeedMultiplier(speed);
        new Thread(clock, "SimulationClock").start();

        // DroneSubsystem owns the socket and manages all DroneMachine instances
        DroneSubsystem subsystem = new DroneSubsystem(ids, host, Scheduler.PORT);
        subsystem.setName("DroneSubsystem");
        subsystem.start();

        System.out.printf("DroneSubsystem started — managing drones %s → %s:%d%n",
                ids, host, Scheduler.PORT);
    }
}
