import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FireIncidentSubsystem file parsing and duplicate-fire suppression.
 *
 * One FireIncidentSubsystem instance is shared across all tests (it binds to
 * the fixed port 6001); tests must therefore run sequentially, which is the
 * JUnit 5 default.
 *
 * @author Aryan Kumar Singh (101299776)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FireIncidentSubsystemTest {

    // Small UDP server that stands in for the Scheduler
    private static class MockScheduler {

        DatagramSocket socket;
        int  port;
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        boolean running = true;

        MockScheduler() throws Exception {
            socket = new DatagramSocket(0);
            socket.setSoTimeout(100);
            port = socket.getLocalPort();
            Thread t = new Thread(this::listen);
            t.setDaemon(true);
            t.start();
        }

        void listen() {
            byte[] buf = new byte[1024];
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
                    received.add(msg);

                    byte[] reply;
                    if (msg.startsWith("isZoneActive|")) {
                        // Return "true" once a receiveFireEvent has been sent for that zone
                        int zoneId = Integer.parseInt(msg.split("\\|")[1]);
                        boolean active = received.stream()
                                .anyMatch(m -> m.startsWith("receiveFireEvent|" + zoneId + "|"));
                        reply = (active ? "true" : "false").getBytes();
                    } else if (msg.startsWith("getTime")) {
                        reply = "0".getBytes();
                    } else {
                        reply = "ACK".getBytes();
                    }
                    socket.send(new DatagramPacket(reply, reply.length,
                            pkt.getAddress(), pkt.getPort()));

                } catch (SocketTimeoutException ignored) {
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
        }

        boolean received(String prefix) {
            for (String m : received) if (m.startsWith(prefix)) return true;
            return false;
        }

        int countReceived(String prefix) {
            return (int) received.stream().filter(m -> m.startsWith(prefix)).count();
        }

        void stop() { running = false; socket.close(); }
    }

    private MockScheduler mock;
    private Thread        subsystemThread;
    private DatagramSocket cmdSocket;  // for sending loadFile|<path> to port 6001
    private Path          tempDir;

    @BeforeAll
    void startSubsystem() throws Exception {
        tempDir = Files.createTempDirectory("fire_test");
        mock = new MockScheduler();

        // Constructor binds to port 6001 and opens sendSocket to mock scheduler
        FireIncidentSubsystem sub =
                new FireIncidentSubsystem("localhost", mock.port, "");
        subsystemThread = new Thread(sub, "FireIncidentSubsystem");
        subsystemThread.setDaemon(true);
        subsystemThread.start();

        cmdSocket = new DatagramSocket();
        Thread.sleep(300);  // let the subsystem bind and start listening on 6001
    }

    @AfterAll
    void stopAll() throws Exception {
        if (cmdSocket != null) cmdSocket.close();
        if (mock != null) mock.stop();
        if (subsystemThread != null) subsystemThread.interrupt();

        // Clean up temp dir
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile)
                .forEach(File::delete);
    }

    @BeforeEach
    void clearReceived() {
        mock.received.clear();
    }


    // Helper Functions for writing and loading functionalities
    private Path writeCsv(String fileName, String... lines) throws Exception {
        Path f = tempDir.resolve(fileName);
        Files.write(f, List.of(lines));
        return f;
    }

    private void loadFile(Path path) throws Exception {
        String msg = "loadFile|" + path.toAbsolutePath();
        byte[] data = msg.getBytes();
        cmdSocket.send(new DatagramPacket(data, data.length,
                InetAddress.getLoopbackAddress(), FireIncidentSubsystem.PORT));
    }

    // ==== TESTS ====
    /**
     * 1. A row with event type FIRE_EVENT must send a receiveFireEvent message
     *    to the Scheduler.
     */
    @Test
    void fireEventRowSendsReceiveFireEvent() throws Exception {
        Path csv = writeCsv("t1.csv",
                "Time, ZoneID, EventType, Severity",
                "00:00:00, 1, FIRE_EVENT, HIGH");
        loadFile(csv);
        Thread.sleep(1500);
        assertTrue(mock.received("receiveFireEvent|1|"),
                "FIRE_EVENT row must send receiveFireEvent|1| to the Scheduler");
    }

    /**
     * 2. The keyword FIRE (without _EVENT) is also accepted and must produce the
     *    same receiveFireEvent message.
     */
    @Test
    void fireKeywordAlsoAccepted() throws Exception {
        Path csv = writeCsv("t2.csv",
                "Time, ZoneID, EventType, Severity",
                "00:00:00, 2, FIRE, MODERATE");
        loadFile(csv);
        Thread.sleep(1500);
        assertTrue(mock.received("receiveFireEvent|2|"),
                "FIRE keyword must be treated the same as FIRE_EVENT");
    }

    /**
     * 3. A row with event type DRONE_STUCK must send an injectFaultEvent message
     *    targeting the drone ID in column 2.
     */
    @Test
    void droneStuckSendsInjectFaultEvent() throws Exception {
        Path csv = writeCsv("t3.csv",
                "Time, ZoneID, EventType, Severity",
                "00:00:00, 3, DRONE_STUCK, NONE");
        loadFile(csv);
        Thread.sleep(1500);
        assertTrue(mock.received("injectFaultEvent|3|DRONE_STUCK"),
                "DRONE_STUCK row must send injectFaultEvent|<droneId>|DRONE_STUCK");
    }

    /**
     * 4. A row with event type NOZZLE_FAULT must send an injectFaultEvent message.
     */
    @Test
    void nozzleFaultSendsInjectFaultEvent() throws Exception {
        Path csv = writeCsv("t4.csv",
                "Time, ZoneID, EventType, Severity",
                "00:00:00, 2, NOZZLE_FAULT, NONE");
        loadFile(csv);
        Thread.sleep(1500);
        assertTrue(mock.received("injectFaultEvent|2|NOZZLE_FAULT"),
                "NOZZLE_FAULT row must send injectFaultEvent|<droneId>|NOZZLE_FAULT");
    }

    /**
     * 5. The very first data row must trigger a startClock message to the
     *    Scheduler so the simulation time begins.
     */
    @Test
    void firstEventTriggersStartClock() throws Exception {
        Path csv = writeCsv("t5.csv",
                "Time, ZoneID, EventType, Severity",
                "00:00:00, 1, FIRE_EVENT, LOW");
        loadFile(csv);
        Thread.sleep(1500);
        assertTrue(mock.received("startClock|"),
                "First event in the file must send a startClock message");
    }

    /**
     * 6. If a fire event for zone 1 is already active, a second fire event for
     *    the same zone must be suppressed — only one receiveFireEvent is sent.
     */
    @Test
    void duplicateFireForActiveZoneIsSkipped() throws Exception {
        Path csv = writeCsv("t6.csv",
                "Time, ZoneID, EventType, Severity",
                "00:00:00, 1, FIRE_EVENT, HIGH",
                "00:00:00, 1, FIRE_EVENT, LOW");   // zone 1 still active → skip
        loadFile(csv);
        Thread.sleep(2000);
        assertEquals(1, mock.countReceived("receiveFireEvent|1|"),
                "Only one receiveFireEvent for zone 1 — duplicate must be suppressed");
    }

    /**
     * 7. An unrecognised event type must not produce any receiveFireEvent or
     *    injectFaultEvent message — it is silently skipped.
     */
    @Test
    void unknownEventTypeIsSkipped() throws Exception {
        Path csv = writeCsv("t7.csv",
                "Time, ZoneID, EventType, Severity",
                "00:00:00, 1, BANANA, HIGH");
        loadFile(csv);
        Thread.sleep(1500);
        assertFalse(mock.received("receiveFireEvent|"),
                "Unknown event type must not produce a receiveFireEvent");
        assertFalse(mock.received("injectFaultEvent|"),
                "Unknown event type must not produce an injectFaultEvent");
    }
}
