import java.net.SocketException;
import java.net.UnknownHostException;

public class EventLoggerMain {
    public static void main(String[] args) throws SocketException, UnknownHostException {
        EventLogger logger = new EventLogger();
        new Thread(logger, "EventLogger").start();
    }
}
