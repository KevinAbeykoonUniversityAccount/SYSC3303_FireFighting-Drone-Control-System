import java.io.FileWriter;
import java.io.IOException;
import java.net.*;

public class EventLogger implements Runnable{
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

    public String recieve() {
        byte[] buffer = new byte[100];
        try {
            DatagramPacket event = new DatagramPacket(buffer, buffer.length);
            reciever.receive(event);
            String eventStr = new String(event.getData(), 0, event.getLength());
            return eventStr;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Log Error";
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

    public void checkFlags(String event) {
        if (false) schedulerRunning = false;
        if (false) fireSystemRunning = false;
        if (false) droneSystemRunning = false;
    }

    public void displayMetrics() {

    }

    @Override
    public void run() {
        System.out.println("Starting Event Logger");
        while (schedulerRunning | fireSystemRunning | droneSystemRunning) {
            String event = recieve();
            writeLog(event);
            checkFlags(event);
        }
        displayMetrics();
    }
}
