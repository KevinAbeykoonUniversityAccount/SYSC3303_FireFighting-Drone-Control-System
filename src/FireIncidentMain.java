import java.net.*;

/**
 * Entry point for the FireIncidentSubsystem process. Start alongside SchedulerMain.
 *
 * Usage:   java FireIncidentMain [--host schedulerHost] [--file path/to/fire_events.csv] [--zones path/to/zones.csv]
 * Example: java FireIncidentMain
 *          java FireIncidentMain --file src/fire_events.csv
 *          java FireIncidentMain --file src/fire_events.csv --zones src/zones.csv
 *          java FireIncidentMain --host 192.168.1.10 --file fire_events.csv
 *
 * Without --file/--zones: subsystem starts and waits for GUI loadFile commands.
 * With --file: sends a loadFile UDP command to the subsystem immediately.
 * With --zones: sends a loadZones UDP command to the Scheduler immediately.
 */
public class FireIncidentMain {
    public static void main(String[] args) throws Exception {
        String host      = "localhost";
        String incFile   = null;
        String zonesFile = null;

        for (int i = 0; i < args.length; i++) {
            if ("--host".equals(args[i]))  host      = args[++i];
            else if ("--file".equals(args[i]))  incFile   = args[++i];
            else if ("--zones".equals(args[i])) zonesFile = args[++i];
        }

        FireIncidentSubsystem subsystem =
                new FireIncidentSubsystem(host, Scheduler.PORT);
        new Thread(subsystem, "FireIncident").start();
        System.out.println("FireIncidentMain: ready on port "
                + FireIncidentSubsystem.PORT + ", waiting for loadFile command.");

        // Small delay so the listen socket is bound before we send to it
        Thread.sleep(200);

        if (zonesFile != null) {
            String absPath = new java.io.File(zonesFile).getAbsolutePath();
            sendUdp(host, Scheduler.PORT, "loadZones|" + absPath);
            System.out.println("FireIncidentMain: sent loadZones -> " + absPath);
        }

        if (incFile != null) {
            String absPath = new java.io.File(incFile).getAbsolutePath();
            sendUdp(host, FireIncidentSubsystem.PORT, "loadFile|" + absPath);
            System.out.println("FireIncidentMain: sent loadFile -> " + absPath);
        }
    }

    private static void sendUdp(String host, int port, String message) throws Exception {
        try (DatagramSocket sock = new DatagramSocket()) {
            byte[]         data = message.getBytes();
            InetAddress    addr = InetAddress.getByName(host);
            DatagramPacket pkt  = new DatagramPacket(data, data.length, addr, port);
            sock.send(pkt);
        }
    }
}
