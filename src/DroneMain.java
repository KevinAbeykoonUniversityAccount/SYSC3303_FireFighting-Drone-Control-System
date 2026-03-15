/**
 * Entry point for the Drone process. Start after SchedulerMain.
 *
 * Usage:   java DroneMain <id1> [id2 ...] [--host schedulerHost]
 * Example: java DroneMain 1 2 3
 */
public class DroneMain {
    public static void main(String[] args) throws Exception {
        String host  = "localhost";
        int    speed = 60;
        java.util.List<Integer> ids = new java.util.ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            if      ("--host".equals(args[i]))  host  = args[++i];
            else ids.add(Integer.parseInt(args[i]));
        }

        if (ids.isEmpty()) {
            System.exit(1);
        }

        SimulationClock clock = SimulationClock.getInstance();
        clock.setClockSpeedMultiplier(speed);
        new Thread(clock, "SimulationClock").start();

        for (int id : ids) {
            DroneSubsystem drone = new DroneSubsystem(id, host, Scheduler.PORT);
            drone.setName("Drone-" + id);
            drone.start();
            System.out.println("Drone " + id + " started → " + host + ":" + Scheduler.PORT);
        }
    }
}
