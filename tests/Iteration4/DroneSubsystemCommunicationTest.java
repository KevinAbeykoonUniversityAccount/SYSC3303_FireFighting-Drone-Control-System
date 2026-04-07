import org.junit.jupiter.api.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the communication between Scheduler ↔ DroneSubsystem ↔ DroneMachine.
 *
 * A MockScheduler (a small UDP server) stands in for the real Scheduler process.
 * It records every message DroneSubsystem sends and can push messages back to
 * DroneSubsystem to simulate what the real Scheduler would do.
 *
 * @author Aryan Kumar Singh (101299776)
 * @author Abdullah Khan (101305235)
 */
public class DroneSubsystemCommunicationTest {

    // A simple UDP server that stands in for the Scheduler
    private static class MockScheduler {

        DatagramSocket socket;
        int  port;
        // Thread-safe list so the listener thread and test thread don't conflict
        List<String> received = Collections.synchronizedList(new ArrayList<>());

        // DroneSubsystem's main receive port — discovered from the registerDrone message
        InetAddress droneAddr = null;
        int         dronePort = -1;
        boolean     running   = true;

        MockScheduler() throws Exception {
            socket = new DatagramSocket(0);   // OS picks a free port
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

                    // First registerDrone tells us DroneSubsystem's listen port
                    if (msg.startsWith("registerDrone|") && dronePort == -1) {
                        String[] parts = msg.split("\\|");
                        droneAddr = pkt.getAddress();
                        dronePort = Integer.parseInt(parts[5]);
                    }

                    // Reply ACK to every message (same as the real Scheduler)
                    byte[] ack = "ACK".getBytes();
                    socket.send(new DatagramPacket(ack, ack.length,
                            pkt.getAddress(), pkt.getPort()));

                } catch (SocketTimeoutException ignored) {
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
        }

        // Send a message directly to DroneSubsystem's main socket
        void push(String message) throws Exception {
            byte[] data = message.getBytes();
            socket.send(new DatagramPacket(data, data.length, droneAddr, dronePort));
        }

        // Check whether any received message starts with the given prefix
        boolean received(String prefix) {
            for (String m : received) {
                if (m.startsWith(prefix)) return true;
            }
            return false;
        }

        void stop() {
            running = false;
            socket.close();
        }
    }

    private MockScheduler mock;
    private Thread        subsystemThread;

    @AfterEach
    void teardown() {
        if (mock != null)           mock.stop();
        if (subsystemThread != null) subsystemThread.interrupt();
    }

    // Start a DroneSubsystem with the given drone IDs and wait for it to register
    private void startSystem(int... ids) throws Exception {
        List<Integer> idList = new ArrayList<>();
        for (int id : ids) idList.add(id);

        mock = new MockScheduler();
        DroneSubsystem subsystem = new DroneSubsystem(idList, "localhost", mock.port);
        subsystemThread = new Thread(subsystem);
        subsystemThread.setDaemon(true);
        subsystemThread.start();

        Thread.sleep(2000); // give DroneSubsystem time to register all drones
    }


    // ************ Scheduler → DroneSubsystem → DroneMachine ******************

    @Test
    void testDroneSubsystemRegistersWithScheduler() throws Exception {
        startSystem(1);
        assertTrue(mock.received("registerDrone|1|"),
                "DroneSubsystem must send a registerDrone message for drone 1");
    }

    @Test
    void testAllDronesRegisterOnStartup() throws Exception {
        startSystem(1, 2);
        assertTrue(mock.received("registerDrone|1|"), "Drone 1 must register");
        assertTrue(mock.received("registerDrone|2|"), "Drone 2 must register");
    }

    @Test
    void testRegistrationMessageFormat() throws Exception {
        startSystem(1);

        String reg = null;
        for (String m : mock.received) {
            if (m.startsWith("registerDrone|1|")) { reg = m; break; }
        }
        assertNotNull(reg, "registerDrone|1 message not found");

        // Expected: registerDrone|droneId|x|y|water|listenPort
        String[] parts = reg.split("\\|");
        assertEquals(6, parts.length, "registerDrone must have 6 fields");
        int listenPort = Integer.parseInt(parts[5]);
        assertTrue(listenPort > 0, "listenPort must be a positive number");
    }

    @Test
    void testAssignMissionCausesDroneToSendLocationUpdates() throws Exception {
        startSystem(1);

        // ASSIGN_MISSION|droneId|zoneId|eventType|severity|water|seconds
        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|100");
        Thread.sleep(2000);

        assertTrue(mock.received("locationUpdate|1|"),
                "Drone 1 must send locationUpdate messages after being assigned a mission");
    }

    @Test
    void testAssignMissionOnlyActivatesTargetDrone() throws Exception {
        startSystem(1, 2);

        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|100");
        Thread.sleep(2000);

        assertTrue(mock.received("locationUpdate|1|"),  "Drone 1 must send location updates");
        assertFalse(mock.received("locationUpdate|2|"), "Drone 2 must not move — it got no mission");
    }

    @Test
    void testInjectSoftFaultCausesFaultedLocationUpdate() throws Exception {
        startSystem(1);

        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|100");
        Thread.sleep(500); // let drone start flying
        mock.push("INJECT_FAULT|1|DRONE_STUCK");
        Thread.sleep(2000);

        boolean faultedSeen = false;
        for (String m : mock.received) {
            if (m.startsWith("locationUpdate|1|") && m.endsWith("FAULTED")) {
                faultedSeen = true;
                break;
            }
        }
        assertTrue(faultedSeen,
                "Drone 1 must send a locationUpdate with state FAULTED after DRONE_STUCK");
    }

    @Test
    void testInjectHardFaultSendsDroneHardFaultToScheduler() throws Exception {
        startSystem(1);

        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|100");
        Thread.sleep(500);
        mock.push("INJECT_FAULT|1|NOZZLE_FAULT");
        Thread.sleep(2000);

        assertTrue(mock.received("droneHardFault|1"),
                "DroneSubsystem must notify the Scheduler with droneHardFault|1 after a hard fault");
    }

    // ============================================================
    // DroneMachine → DroneSubsystem → Scheduler  (outbound reporting)
    // ============================================================

    @Test
    void testHardFaultMessageContainsCorrectDroneId() throws Exception {
        startSystem(2); // use drone ID 2 to make sure the ID is not hard-coded

        mock.push("ASSIGN_MISSION|2|1|FIRE|HIGH|10|100");
        Thread.sleep(500);
        mock.push("INJECT_FAULT|2|NOZZLE_FAULT");
        Thread.sleep(2000);

        assertTrue(mock.received("droneHardFault|2"),
                "droneHardFault message must contain the correct drone ID");
    }

    @Test
    void testSoftFaultDoesNotSendDroneHardFault() throws Exception {
        startSystem(1);

        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|100");
        Thread.sleep(500);
        mock.push("INJECT_FAULT|1|DRONE_STUCK");
        Thread.sleep(2000);

        assertFalse(mock.received("droneHardFault|"),
                "A soft fault (DRONE_STUCK) must never send droneHardFault to the Scheduler");
    }

    @Test
    void testFaultOnlyAffectsTargetDrone() throws Exception {
        startSystem(1, 2);

        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|100");
        mock.push("ASSIGN_MISSION|2|2|FIRE|HIGH|10|200");
        Thread.sleep(500);

        mock.push("INJECT_FAULT|1|DRONE_STUCK"); // only drone 1
        Thread.sleep(2000);

        boolean drone1Faulted = false;
        boolean drone2Faulted = false;
        for (String m : mock.received) {
            if (m.startsWith("locationUpdate|1|") && m.endsWith("FAULTED")) drone1Faulted = true;
            if (m.startsWith("locationUpdate|2|") && m.endsWith("FAULTED")) drone2Faulted = true;
        }

        assertTrue(drone1Faulted,  "Drone 1 should report FAULTED");
        assertFalse(drone2Faulted, "Drone 2 should not be affected by fault injected into Drone 1");
    }

    @Test
    void testDecommissionAfterHardFaultStopsLocationUpdates() throws Exception {
        startSystem(1);

        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|100");
        Thread.sleep(500);
        mock.push("INJECT_FAULT|1|NOZZLE_FAULT");
        Thread.sleep(2000); // wait for hard fault to be processed

        // Count location updates before sending DECOMMISSION
        int countBefore = 0;
        for (String m : mock.received) {
            if (m.startsWith("locationUpdate|1|")) countBefore++;
        }

        mock.push("DECOMMISSION|1");
        Thread.sleep(1000); // wait for drone to shut down

        int countAfter = 0;
        for (String m : mock.received) {
            if (m.startsWith("locationUpdate|1|")) countAfter++;
        }

        assertEquals(countBefore, countAfter,
                "No new locationUpdate messages should be sent after DECOMMISSION");
    }
}
