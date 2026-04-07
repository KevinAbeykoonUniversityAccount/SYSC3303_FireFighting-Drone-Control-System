import java.net.SocketException;
import java.net.UnknownHostException;

public class EventLoggerMain {
    public static void main(String[] args) throws SocketException, UnknownHostException, InterruptedException {
        EventLogger logger = new EventLogger();
        Thread thread = new Thread(logger, "EventLogger");
        thread.start();
        Thread.sleep(200000);
        thread.interrupt();
        logger.displayMetrics();
    }
}
