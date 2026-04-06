import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class EventLogger implements Runnable{
    private class EventLog {
        final long time;
        final String entity;
        final String code;
        final String[] data;

        public EventLog(long time, String entity, String code, String... data) {
            this.time = time;
            this.entity = entity;
            this.code = code;
            this.data = data;
        }

        public String getEntity() {
            return entity;
        }

        public String getCode() {
            return code;
        }

        public String toString() {
            Instant instantTime = Instant.ofEpochMilli(time);
            String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault()).format(instantTime);

            String logString = "Event log: [" + formattedTime + ", " + entity + ", " + code;

            for (String thing : data) {
                logString += ", " + thing;
            }

            logString += "]";

            return  logString;
        }
    }

    public final static int DEFAULT_PORT = 9000;

    private DatagramSocket reciever;
    private int port;

    private boolean schedulerRunning = true;
    private boolean fireSystemRunning = true;
    private boolean droneSystemRunning = true;

    public EventLogger() throws SocketException, UnknownHostException {
        this.port = DEFAULT_PORT;
        this.reciever = new DatagramSocket(port);
    }

    public EventLog recieve() {
        byte[] buffer = new byte[100];
        EventLog log;
        try {
            DatagramPacket event = new DatagramPacket(buffer, buffer.length);
            reciever.receive(event);
            String[] eventStr = new String(event.getData(), 0, event.getLength()).split(",");
            if (eventStr.length == 2) log = new EventLog(System.currentTimeMillis(), eventStr[0], eventStr[1]);
            else log = new EventLog(System.currentTimeMillis(), eventStr[0], eventStr[1], eventStr[2]);
            return log;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void writeLog(String event) {
        try {
            FileWriter writer = new FileWriter("log.txt", true);
            writer.write(event + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clearLogFile() {
        try {
            FileWriter writer = new FileWriter("log.txt");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkFlags(EventLog event) {
        if (event.getEntity().equals("Scheduler") && event.code.equals("ENDED")) schedulerRunning = false;
        if (event.getEntity().equals("FireSubsystem") && event.code.equals("ENDED")) fireSystemRunning = false;
        if (event.getEntity().equals("DroneSubsystem") && event.code.equals("ENDED")) droneSystemRunning = false;
    }

    public void displayMetrics() {

    }

    @Override
    public void run() {
        clearLogFile();
        System.out.println("Starting Event Logger");
        while (schedulerRunning | fireSystemRunning | droneSystemRunning) {
            EventLog event = recieve();
            writeLog(event.toString());
            checkFlags(event);
        }
        displayMetrics();
    }
}
