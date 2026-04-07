import org.junit.*;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.Assert.*;

/**
 * Tests for firefighting-agent capacity limits and the refill-station mechanic.
 *
 * All tests exercise the Scheduler directly (no UDP loop running) using the
 * package-private test helpers added to Scheduler.
 */
public class AgentCapacityTest {

    private Scheduler scheduler;

    @Before
    public void setUp() throws Exception {
        scheduler = new Scheduler();
        new Thread(scheduler, "Scheduler").start();
    }

    @After
    public void tearDown() {
        scheduler.stop();              // releases port 6000
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FireEvent fire(int zoneId, String severity) {
        return new FireEvent(zoneId, "FIRE", severity, 0);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * 1. A drone with a full tank is dispatched immediately when a fire arrives.
     *    Its state must change from IDLE to ONROUTE.
     */
    @Test
    public void fullTankDroneDispatchedOnFireEvent() throws Exception {
        scheduler.registerDroneForTest(1, 15); // full tank
        scheduler.receiveFireEvent(fire(1, "HIGH"));

        DroneInfo drone = scheduler.getDroneInfo(1);
        assertEquals("Drone with water must be dispatched", "ONROUTE", drone.state);
    }

    /**
     * 2. A drone with an empty tank is never dispatched — the fire stays queued.
     */
    @Test
    public void emptyTankDroneNotDispatched() throws Exception {
        scheduler.registerDroneForTest(1, 0); // empty tank
        scheduler.receiveFireEvent(fire(1, "LOW"));

        DroneInfo drone = scheduler.getDroneInfo(1);
        assertNotEquals("Empty-tank drone must not be sent on a mission", "ONROUTE", drone.state);
        assertTrue("Fire must remain queued", scheduler.hasQueuedFireForZone(1));
    }

    /**
     * 3. A second fire for the same zone is held in the pending queue while
     *    the first fire is still being serviced.
     */
    @Test
    public void secondFireForActiveZoneHeldInPendingQueue() throws Exception {
        scheduler.registerDroneForTest(1, 15);

        scheduler.receiveFireEvent(fire(1, "HIGH"));  // dispatched, zone becomes active
        scheduler.receiveFireEvent(fire(1, "LOW"));   // zone still active → pending

        assertEquals("Second fire for active zone must be pending", 1,
                scheduler.pendingFireCountForZone(1));
    }

    /**
     * 4. droneRefillComplete restores the drone's water to a full 15 L tank
     *    and marks it IDLE.
     */
    @Test
    public void droneRefillCompleteRestoresWaterAndSetsIdle() throws Exception {
        scheduler.registerDroneForTest(1, 0); // arrives empty
        scheduler.droneRefilling(1);
        scheduler.droneRefillComplete(1);

        DroneInfo drone = scheduler.getDroneInfo(1);
        assertEquals("Water must be 15 after refill", 15, drone.waterRemaining);
        assertEquals("Drone must be IDLE after refill", "IDLE", drone.state);
    }

    /**
     * 5. A fire that could not be dispatched (no drone with water) is picked up
     *    automatically once a drone completes a refill.
     */
    @Test
    public void pendingFireDispatchedAfterDroneRefill() throws Exception {
        // No drone available initially — fire queues up
        scheduler.receiveFireEvent(fire(1, "MODERATE"));
        assertTrue("Fire must be queued when no drone available",
                scheduler.hasQueuedFireForZone(1));

        // Drone arrives back and refills
        scheduler.registerDroneForTest(2, 0);
        scheduler.droneRefilling(2);
        scheduler.droneRefillComplete(2);   // triggers tryDispatch internally

        DroneInfo drone = scheduler.getDroneInfo(2);
        assertEquals("Refilled drone must be dispatched to the waiting fire",
                "ONROUTE", drone.state);
    }

    /**
     * 6. When a drone has less water than the fire requires, only the available
     *    water is assigned and the remainder is re-queued as a partial mission.
     */
    @Test
    public void partialMissionRequeuesRemainder() throws Exception {
        scheduler.registerDroneForTest(1, 10); // only 10 L, HIGH fire needs 15 L

        scheduler.receiveFireEvent(fire(1, "HIGH"));

        DroneInfo drone = scheduler.getDroneInfo(1);
        assertEquals("Drone must be sent on partial mission", "ONROUTE", drone.state);

        // 5 L remainder must be re-queued for zone 1
        assertTrue("Remaining 5 L must be re-queued for zone 1",
                scheduler.hasQueuedFireForZone(1));
    }
}
