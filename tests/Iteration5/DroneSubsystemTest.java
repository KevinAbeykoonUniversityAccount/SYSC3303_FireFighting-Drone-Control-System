import org.junit.jupiter.api.*;
import java.net.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration 5 tests for DroneSubsystem.
 *
 * Focuses on features added / changed in Iteration 5:
 *   - registerDrone now has 7 fields (includes battery level)
 *   - ASSIGN_MISSION now has 9 fields (includes targetX, targetY)
 *   - New RETURN_TO_BASE message type routing
 *
 * A MockScheduler stands in for the real Scheduler process.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public class DroneSubsystemTest {

    // Small UDP server that stands in for the Scheduler
    private static class MockScheduler {

        DatagramSocket socket;
        int  port;
        List<String> received = Collections.synchronizedList(new ArrayList<>());

        InetAddress droneAddr = null;
        int         dronePort = -1;
        boolean     running   = true;

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

                    // Capture DroneSubsystem's listen port from the first registerDrone
                    if (msg.startsWith("registerDrone|") && dronePort == -1) {
                        droneAddr = pkt.getAddress();
                        dronePort = Integer.parseInt(msg.split("\\|")[5]);
                    }

                    // Reply ACK to everything (same as the real Scheduler)
                    byte[] ack = "ACK".getBytes();
                    socket.send(new DatagramPacket(ack, ack.length,
                            pkt.getAddress(), pkt.getPort()));

                } catch (SocketTimeoutException ignored) {
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
        }

        void push(String message) throws Exception {
            byte[] data = message.getBytes();
            socket.send(new DatagramPacket(data, data.length, droneAddr, dronePort));
        }

        boolean received(String prefix) {
            for (String m : received) if (m.startsWith(prefix)) return true;
            return false;
        }

        void stop() {
            running = false;
            socket.close();
        }
    }

    private MockScheduler mock;
    private Thread        subsystemThread;

    @BeforeAll
    static void setFastClock() {
        // Speed up drone movement so tests finish in milliseconds, not seconds
        SimulationClock.getInstance().setClockSpeedMultiplier(100);
    }

    @AfterAll
    static void restoreClock() {
        SimulationClock.getInstance().setClockSpeedMultiplier(1);
    }

    @AfterEach
    void teardown() {
        if (mock != null)           mock.stop();
        if (subsystemThread != null) subsystemThread.interrupt();
    }

    private void startSystem(int... ids) throws Exception {
        List<Integer> idList = new ArrayList<>();
        for (int id : ids) idList.add(id);

        mock = new MockScheduler();
        DroneSubsystem subsystem = new DroneSubsystem(idList, "localhost", mock.port);
        subsystemThread = new Thread(subsystem);
        subsystemThread.setDaemon(true);
        subsystemThread.start();
        Thread.sleep(500);  // wait for all drones to register
    }

    // ==== Registration ====

    /** 1. From the most recent iteration introducing battery of drones,
     *     there should be 7 columns
    */
    @Test
    void testRegisterDroneMessageHasSevenFields() throws Exception {
        startSystem(1);

        String reg = null;
        for (String m : mock.received) {
            if (m.startsWith("registerDrone|1|")) { reg = m; break; }
        }
        assertNotNull(reg, "registerDrone|1 not found in received messages");
        assertEquals(7, reg.split("\\|").length,
                "registerDrone must have exactly 7 pipe-delimited fields in Iteration 5");
    }

    /**
     * 2. The 7th field (index 6) of registerDrone is the battery level.
     *    A freshly created drone must report 100 %.
     */
    @Test
    void testRegisterDroneReportsBatteryAt100() throws Exception {
        startSystem(1);

        String reg = null;
        for (String m : mock.received) {
            if (m.startsWith("registerDrone|1|")) { reg = m; break; }
        }
        assertNotNull(reg, "registerDrone|1 not found");
        assertEquals("100", reg.split("\\|")[6],
                "Battery field (index 6) must be 100 for a new drone");
    }

    /**
     * 3. Every drone in the list registers independently with its own droneId.
     */
    @Test
    void testAllDronesRegisterWithCorrectIds() throws Exception {
        startSystem(1, 3);
        assertTrue(mock.received("registerDrone|1|"), "Drone 1 must register");
        assertTrue(mock.received("registerDrone|3|"), "Drone 3 must register");
    }

    // ==== ASSIGN_MISSION related tests ====
    /**
     * 4. A 9-field ASSIGN_MISSION (with targetX=0, targetY=0) is parsed without
     *    error and causes the drone to report missionCompleted after extinguishing.
     */
    @Test
    void testNineFieldAssignMissionParsedSuccessfully() throws Exception {
        startSystem(1);

        // targetX=0, targetY=0 — drone arrives instantly (already at origin)
        mock.push("ASSIGN_MISSION|1|1|FIRE|HIGH|10|0|0|0");
        Thread.sleep(2000);

        assertTrue(mock.received("missionCompleted|1|"),
                "Drone must report missionCompleted after a 9-field ASSIGN_MISSION");
    }

    /**
     * 5. When targetX and targetY are non-zero the drone moves, producing
     *    locationUpdate messages en route.
     */
    @Test
    void testNineFieldAssignMissionWithNonZeroTargetProducesLocationUpdates()
            throws Exception {
        startSystem(1);

        // Target is (2, 0) — 2 cells away, drone will send locationUpdate each step
        mock.push("ASSIGN_MISSION|1|1|FIRE|LOW|5|0|2|0");
        Thread.sleep(2000);

        assertTrue(mock.received("locationUpdate|1|"),
                "Drone must send locationUpdate messages when moving to a non-base target");
    }

    // ==== RETURN_TO_BASE related tests ====

    /**
     * 6. After completing a mission at a non-base cell, a RETURN_TO_BASE message
     *    causes the drone to enter RETURNING state (visible in locationUpdate state field).
     */
    @Test
    void testReturnToBaseCausesReturningLocationUpdate() throws Exception {
        startSystem(1);

        // Send drone to (1, 0), wait for mission to finish, then request return
        mock.push("ASSIGN_MISSION|1|1|FIRE|LOW|5|0|1|0");
        Thread.sleep(1500); // drone should be IDLE at (1,0) by now

        mock.push("RETURN_TO_BASE|1");
        Thread.sleep(1000);

        boolean returningStateSeen = false;
        for (String m : mock.received) {
            if (m.startsWith("locationUpdate|1|") && m.endsWith("RETURNING")) {
                returningStateSeen = true;
                break;
            }
        }
        assertTrue(returningStateSeen,
                "Drone 1 must send a RETURNING locationUpdate after RETURN_TO_BASE");
    }

    /**
     * 7. RETURN_TO_BASE targeting drone 1 must not cause drone 2 to report RETURNING.
     */
    @Test
    void testReturnToBaseDoesNotAffectSiblingDrone() throws Exception {
        startSystem(1, 2);

        mock.push("ASSIGN_MISSION|1|1|FIRE|LOW|5|0|1|0");
        Thread.sleep(1500);
        mock.push("RETURN_TO_BASE|1");
        Thread.sleep(1000);

        boolean drone2Returning = false;
        for (String m : mock.received) {
            if (m.startsWith("locationUpdate|2|") && m.endsWith("RETURNING")) {
                drone2Returning = true;
                break;
            }
        }
        assertFalse(drone2Returning,
                "Drone 2 must not be affected by a RETURN_TO_BASE targeting drone 1");
    }
}
