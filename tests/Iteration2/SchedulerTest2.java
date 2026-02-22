import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.Assert;
import java.util.Map;

public class SchedulerTest2 {

    private Scheduler scheduler;
    private DroneSubsystem drone0;
    private DroneSubsystem drone1;

    @BeforeEach
    public void setup() {
        // Create scheduler with room for two drones
        scheduler = new Scheduler(2);

        // Create and register two drones
        drone0 = new DroneSubsystem(0, scheduler);
        drone1 = new DroneSubsystem(1, scheduler);
        scheduler.registerDrone(drone0);
        scheduler.registerDrone(drone1);
    }

    @Test
    public void testPriorityHighBeforeModerate() throws InterruptedException {
        FireEvent moderate = new FireEvent(1, "FIRE_DETECTED", "MODERATE", 100);
        FireEvent high = new FireEvent(2, "FIRE_DETECTED", "HIGH", 200);
        scheduler.receiveFireEvent(moderate);
        scheduler.receiveFireEvent(high);

        // First drone gets a partial from HIGH (15L)
        FireEvent assigned1 = scheduler.requestMission(0);
        assertNotNull(assigned1);
        assertEquals(FireEvent.FireSeverity.HIGH, assigned1.getSeverity());
        assertEquals(2, assigned1.getZoneId());
        assertEquals(15, assigned1.getWaterRemaining());
        drone0.setCurrentMission(assigned1);  // so counts include active mission

        // After first assignment: queue has HIGH (15L) + MODERATE (20L)
        int[] counts = scheduler.getFireCountsBySeverity();
        assertEquals(2, counts[0]); // one in queue + one on drone = 2 HIGH
        assertEquals(1, counts[1]); // MODERATE in queue

        // Second drone (different ID) takes the remaining HIGH (15L)
        FireEvent assigned2 = scheduler.requestMission(1);
        assertNotNull(assigned2);
        assertEquals(FireEvent.FireSeverity.HIGH, assigned2.getSeverity());
        assertEquals(2, assigned2.getZoneId());
        assertEquals(15, assigned2.getWaterRemaining());
        drone1.setCurrentMission(assigned2);

        // Now both drones are on HIGH missions, MODERATE still in queue
        counts = scheduler.getFireCountsBySeverity();
        assertEquals(2, counts[0]); // both drones on HIGH
        assertEquals(1, counts[1]); // MODERATE still waiting
    }

    @Test
    public void testPartialMissionAssignment() throws InterruptedException {
        // HIGH fire needs 30L, drone capacity 15L
        FireEvent bigFire = new FireEvent(3, "FIRE_DETECTED", "HIGH", 300);
        scheduler.receiveFireEvent(bigFire);

        FireEvent assigned = scheduler.requestMission(0);
        assertNotNull(assigned);
        assertEquals(15, assigned.getWaterRemaining());
        assertEquals(FireEvent.FireSeverity.HIGH, assigned.getSeverity());
        assertEquals(3, assigned.getZoneId());

        // Simulate drone storing the mission
        drone0.setCurrentMission(assigned);

        // After partial assignment, the original event (reduced to 15L) is back in the queue
        int[] counts = scheduler.getFireCountsBySeverity();

        // Queue: 1 HIGH (15L) + drone0 on HIGH (15L) = 2 HIGH
        assertEquals(2, counts[0]); // two HIGH (one in queue, one on drone)
        assertEquals(0, counts[1]);
        assertEquals(0, counts[2]);

        // Total water needed for zone 3: 15 (queue) + 15 (assigned) = 30L
        Map<Integer, Integer> activePerZone = scheduler.getActiveFiresPerZone();
        assertEquals(30, activePerZone.get(3));
    }

    @Test
    public void testMissionCompletion() throws InterruptedException {
        // Add a fire
        FireEvent fire = new FireEvent(4, "FIRE_DETECTED", "LOW", 400);
        scheduler.receiveFireEvent(fire);

        // Assign mission to drone0
        FireEvent assigned = scheduler.requestMission(0);
        assertNotNull(assigned);
        assertEquals(4, assigned.getZoneId());

        // Simulate drone completing the mission (using 10L of water)
        scheduler.missionCompleted(0, 4, 10);

        // Drone should be IDLE again
        assertEquals(DroneSubsystem.DroneState.IDLE, drone0.getDroneState());

        // No remaining water for zone 4 (fire was fully extinguished)
        Map<Integer, Integer> activePerZone = scheduler.getActiveFiresPerZone();
        assertNull(activePerZone.get(4)); // or assertFalse(activePerZone.containsKey(4))
    }

    @Test
    public void testMultipleDrones() throws InterruptedException {
        // Two fires: HIGH (30L) and MODERATE (20L)
        FireEvent high = new FireEvent(1, "FIRE_DETECTED", "HIGH", 500);
        FireEvent moderate = new FireEvent(2, "FIRE_DETECTED", "MODERATE", 600);
        scheduler.receiveFireEvent(high);
        scheduler.receiveFireEvent(moderate);

        // Drone0 takes a partial from HIGH (15L)
        FireEvent mission0 = scheduler.requestMission(0);
        assertNotNull(mission0);
        assertEquals(FireEvent.FireSeverity.HIGH, mission0.getSeverity());
        assertEquals(15, mission0.getWaterRemaining());
        drone0.setCurrentMission(mission0);

        // Drone1 now sees: HIGH queue still has 15L, MODERATE queue has 20L
        // It will take the remaining HIGH (15L) because priority is higher
        FireEvent mission1 = scheduler.requestMission(1);
        assertNotNull(mission1);
        assertEquals(FireEvent.FireSeverity.HIGH, mission1.getSeverity());
        assertEquals(15, mission1.getWaterRemaining());
        drone1.setCurrentMission(mission1);


        // After both assignments:
        // - HIGH queue is empty (both portions assigned)
        // - MODERATE queue still has 20L
        // - Both drones on HIGH missions
        int[] counts = scheduler.getFireCountsBySeverity();
        assertEquals(2, counts[0]); // two drones on HIGH
        assertEquals(1, counts[1]); // one MODERATE in queue
        assertEquals(0, counts[2]);

        // Total water per zone: zone1 30L (both drones), zone2 20L
        Map<Integer, Integer> active = scheduler.getActiveFiresPerZone();
        assertEquals(30, active.get(1));
        assertEquals(20, active.get(2));
    }

    @Test
    public void testFireCountsAfterEvents() {
        // Initially all counts zero
        int[] counts = scheduler.getFireCountsBySeverity();
        assertArrayEquals(new int[]{0, 0, 0}, counts);

        // Add one event of each severity
        scheduler.receiveFireEvent(new FireEvent(1, "FIRE_DETECTED", "HIGH", 100));
        scheduler.receiveFireEvent(new FireEvent(2, "FIRE_DETECTED", "MODERATE", 200));
        scheduler.receiveFireEvent(new FireEvent(3, "FIRE_DETECTED", "LOW", 300));

        counts = scheduler.getFireCountsBySeverity();
        assertArrayEquals(new int[]{1, 1, 1}, counts);
    }

    @Test
    public void testActiveFiresPerZone() {
        // Add fires in zones 1 and 2
        scheduler.receiveFireEvent(new FireEvent(1, "FIRE_DETECTED", "HIGH", 100));   // 30L
        scheduler.receiveFireEvent(new FireEvent(2, "FIRE_DETECTED", "LOW", 200));    // 10L

        Map<Integer, Integer> active = scheduler.getActiveFiresPerZone();
        assertEquals(30, active.get(1));
        assertEquals(10, active.get(2));
        assertEquals(2, active.size());
    }
}