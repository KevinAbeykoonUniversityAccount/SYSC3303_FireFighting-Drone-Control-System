import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for Scheduler dispatch logic and zone-loading validation.
 *
 * All tests exercise the Scheduler directly (no UDP loop running) using the
 * package-private test helpers and the public API.  A real Scheduler is
 * created for each test; its stop() method releases port 6000.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public class SchedulerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Scheduler scheduler;

    @Before
    public void setUp() throws Exception {
        scheduler = new Scheduler();
        new Thread(scheduler, "Scheduler").start();
    }

    @After
    public void tearDown() {
        scheduler.stop();
    }


    // Helper Functions
    private FireEvent fire(int zoneId, String severity) {
        return new FireEvent(zoneId, "FIRE", severity, 0);
    }

    private File csv(String... lines) throws IOException {
        File f = tmp.newFile("zones_test.csv");
        try (PrintWriter pw = new PrintWriter(f)) {
            for (String line : lines) pw.println(line);
        }
        return f;
    }


    /**
     * 1. A freshly constructed Scheduler starts in the IDLE state with no fires
     *    queued and no drones registered.
     */
    @Test
    public void schedulerStartsInIdleState() {
        assertEquals("Scheduler must start IDLE",
                Scheduler.SchedulerState.IDLE, scheduler.getCurrentState());
    }

    /**
     * 2. When a HIGH-severity fire and a LOW-severity fire are both queued and
     *    only one drone becomes available, the HIGH fire is dispatched first.
     */
    @Test
    public void highPriorityFireDispatchedBeforeLow() throws Exception {
        // Register drone 1 — zone 1 fire (LOW) is dispatched immediately
        scheduler.registerDroneForTest(1, 15);
        scheduler.receiveFireEvent(fire(1, "LOW"));

        // Drone is now ONROUTE to zone 1 (LOW). Queue a HIGH fire for zone 2.
        scheduler.receiveFireEvent(fire(2, "HIGH"));

        // HIGH fire should be waiting in the high-priority queue
        assertTrue("HIGH fire must be queued while drone is busy",
                scheduler.hasQueuedFireForZone(2));

        // Drone 1 returns (droneRefilling + droneRefillComplete triggers tryDispatch)
        scheduler.droneRefilling(1);
        scheduler.droneRefillComplete(1);

        // tryDispatch picks the HIGH event first
        DroneInfo drone = scheduler.getDroneInfo(1);
        assertEquals("Drone must be dispatched to the HIGH fire after refill",
                "ONROUTE", drone.state);
        assertFalse("HIGH fire must have been dispatched from the queue",
                scheduler.hasQueuedFireForZone(2));
    }

    /**
     * 3. receiveFireEvent marks the zone as active, so hasQueuedFireForZone
     *    returns true immediately after the event is received (and no drone
     *    is available to consume it).
     */
    @Test
    public void zoneIsActiveAfterFireReceived() {
        // No drone registered — fire stays in the priority queue
        scheduler.receiveFireEvent(fire(3, "MODERATE"));
        assertTrue("Zone 3 must be active (queued) after receiving a fire event",
                scheduler.hasQueuedFireForZone(3));
    }

    /**
     * 4. A zone that has not received any fire event must NOT be reported as active.
     */
    @Test
    public void zoneIsInactiveWithNoFire() {
        assertFalse("Zone 99 must not be active when no fire has been received",
                scheduler.hasQueuedFireForZone(99));
    }

    /**
     * 5. A second fire for a zone that is already burning is held in the pending
     *    queue.  Once the first mission completes the pending fire is released into
     *    the priority queue.
     */
    @Test
    public void pendingFireReleasedAfterZoneClears() throws Exception {
        scheduler.registerDroneForTest(1, 15);

        // First fire dispatched immediately (zone 1 becomes active)
        scheduler.receiveFireEvent(fire(1, "LOW"));

        // Second fire for same zone (held as pending)
        scheduler.receiveFireEvent(fire(1, "HIGH"));
        assertEquals("Second fire must be in pending queue while zone 1 is active",
                1, scheduler.pendingFireCountForZone(1));

        // Mission completes -> zone 1 clears -> pending fire released to priority queue
        scheduler.missionCompleted(1, 1, 5);
        assertEquals("Pending queue for zone 1 must be empty after mission completes",
                0, scheduler.pendingFireCountForZone(1));
        assertTrue("Released pending fire must appear in the priority queue",
                scheduler.hasQueuedFireForZone(1));
    }

    /**
     * 6. droneRefillComplete triggers tryDispatch: a queued fire is dispatched to
     *    the newly refilled drone.
     */
    @Test
    public void droneRefillCompleteTriggersPendingDispatch() throws Exception {
        // Fire queued with no drone
        scheduler.receiveFireEvent(fire(1, "HIGH"));
        assertTrue("Fire must be queued when no drone is available",
                scheduler.hasQueuedFireForZone(1));

        // Drone arrives empty and refills
        scheduler.registerDroneForTest(2, 0);
        scheduler.droneRefilling(2);
        scheduler.droneRefillComplete(2);

        // Refilled drone must be dispatched to the waiting fire
        DroneInfo drone = scheduler.getDroneInfo(2);
        assertEquals("Refilled drone must be sent to the queued fire",
                "ONROUTE", drone.state);
    }

    /**
     * 7. A zone file where two zones share the same boundary cell (overlap) is
     *    rejected with a non-empty error list.
     */
    @Test
    public void overlappingZonesRejectedByLoadZones() throws Exception {
        // Zone 1 ends at x=450 m (cell 14); zone 2 starts at x=420 m (cell 14) → overlap
        File f = csv(
                "ZoneID, ZoneStart, ZoneEnd",
                "1, (0, 0), (450, 450)",
                "2, (420, 0), (900, 450)"
        );
        List<String> errors = scheduler.loadZonesFromFile(f.getAbsolutePath());
        assertFalse("Overlapping zones must produce at least one error", errors.isEmpty());
    }
}
