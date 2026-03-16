import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

public class SchedulerUDPTest {

    private Scheduler scheduler;
    private Thread schedulerThread;

    @AfterEach
    public void stopScheduler() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    public void testReceiveFireEventUDP() throws Exception {
        scheduler = new Scheduler();
        schedulerThread = new Thread(scheduler);
        schedulerThread.start();

        DatagramSocket socket = new DatagramSocket();

        String message = "receiveFireEvent|1|TestFire|HIGH|0";
        byte[] data = message.getBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getLocalHost(),
                Scheduler.PORT
        );

        socket.send(packet);

        byte[] buffer = new byte[1024];
        DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
        socket.receive(reply);

        String response = new String(reply.getData(), 0, reply.getLength());

        assertEquals("ACK", response);

        socket.close();
    }
}