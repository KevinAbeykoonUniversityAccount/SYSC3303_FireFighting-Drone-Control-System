import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DroneMachine in isolation.
 *
 * A StubCallback records what the drone reports back so assertions can be
 * made on state transitions without any network layer or Scheduler involved.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public class DroneTest {

    // Records all callbacks the drone fires
    private static class StubCallback implements DroneCallback {

        final List<String> statesSeen = Collections.synchronizedList(new ArrayList<>());
        boolean missionCompletedFired = false;
        boolean hardFaultFired        = false;
        boolean refillingFired        = false;
        boolean refillCompleteFired   = false;
        int     lastBattery           = 100;

        @Override
        public void onLocationUpdate(int id, int x, int y, String state) {
            statesSeen.add(state);
        }

        @Override
        public void onBatteryUpdate(int id, int battery) {
            lastBattery = battery;
        }

        @Override
        public void onMissionCompleted(int id, int zoneId, int waterUsed) {
            missionCompletedFired = true;
        }

        @Override
        public void onRescheduleFireEvent(int id, FireEvent e) {}

        @Override
        public void onDroneRefilling(int id)        { refillingFired = true; }

        @Override
        public void onDroneRefillComplete(int id)   { refillCompleteFired = true; }

        @Override
        public void onHardFault(int id)             { hardFaultFired = true; }

        @Override
        public void onDroneRecovered(int id) {}

        @Override
        public void log(String msg) {}
    }

    private DroneMachine drone;
    private StubCallback  callback;

    @BeforeAll
    static void setFastClock() {
        SimulationClock.getInstance().setClockSpeedMultiplier(100);
    }

    @AfterAll
    static void restoreClock() {
        SimulationClock.getInstance().setClockSpeedMultiplier(1);
    }

    @BeforeEach
    void setup() {
        callback = new StubCallback();
        drone    = new DroneMachine(1, callback);
        drone.setDaemon(true);
        drone.start();
    }

    @AfterEach
    void teardown() {
        drone.handleEvent(DroneMachine.droneEvents.DECOMMISSION);
        drone.interrupt();
    }

    // Helper functions to assign low-priority mission

    /** Give the drone a LOW-severity mission with target at (1, 0). */
    private void startLowMission() {
        drone.setMissionCoordinates(1, 0);
        drone.receiveMissionPush(new FireEvent(1, "FIRE", "LOW", 0));
    }

    /**
     * 1. A new drone carries exactly 15 litres of water.
     */
    @Test
    void initialWaterCapacityIs15Litres() {
        assertEquals(15, drone.getWaterRemaining(),
                "New drone must start with 15 L of water");
    }

    /**
     * 2. Battery starts at 100 % for a newly created drone.
     */
    @Test
    void initialBatteryIs100Percent() {
        assertEquals(100, drone.getBatteryRemaining(),
                "New drone must start with 100 % battery");
    }

    /**
     * 3. After completing a LOW-severity mission (5 L required), the water
     *    remaining decreases by 5 L to 10 L.
     */
    @Test
    void waterDecreasesAfterMission() throws InterruptedException {
        startLowMission();
        Thread.sleep(2000);  // wait for extinguish + callback

        assertTrue(callback.missionCompletedFired,
                "onMissionCompleted must fire to confirm the mission ran");
        assertEquals(10, drone.getWaterRemaining(),
                "Drone must have used 5 L and have 10 L left after a LOW mission");
    }

    /**
     * 4. Once the drone is IDLE at a non-base position, requestReturnToBase()
     *    causes it to enter RETURNING state (visible in locationUpdate state field).
     */
    @Test
    void requestReturnToBaseTriggersReturningState() throws InterruptedException {
        // Mission completes: drone ends up IDLE at (1,0)
        startLowMission();
        Thread.sleep(1500);  // let mission complete

        // Request return — drone at (1,0) should start going back to (0,0)
        drone.requestReturnToBase();
        Thread.sleep(1000);

        boolean returningStateSeen = callback.statesSeen.stream()
                .anyMatch("RETURNING"::equals);
        assertTrue(returningStateSeen,
                "Drone must report RETURNING state after requestReturnToBase");
    }

    /**
     * 5. Calling requestReturnToBase on a drone that is already at the origin (0,0)
     *    must not cause it to move — no RETURNING location updates are emitted.
     */
    @Test
    void droneAlreadyAtBaseDoesNotMoveOnReturnRequest() throws InterruptedException {
        // Drone hasn't moved; it is at (0,0)
        assertEquals(0, drone.getX());
        assertEquals(0, drone.getY());

        drone.requestReturnToBase();
        Thread.sleep(500);

        boolean returningStateSeen = callback.statesSeen.stream()
                .anyMatch("RETURNING"::equals);
        assertFalse(returningStateSeen,
                "A drone already at base must not emit RETURNING updates");
    }

    /**
     * 6. After refilling (back at base), the drone's water level is restored to 15 L.
     */
    @Test
    void refillRestoresWaterTo15Litres() throws InterruptedException {
        // Give drone a HIGH mission (uses 15 L): drone returns to base automatically
        drone.setMissionCoordinates(0, 0);  // arrive at target immediately
        drone.receiveMissionPush(new FireEvent(1, "FIRE", "HIGH", 0));
        Thread.sleep(2000);  // extinguish + refill completes

        // onDroneRefillComplete should have fired
        assertTrue(callback.refillCompleteFired,
                "onDroneRefillComplete must fire after the drone refills");
        assertEquals(15, drone.getWaterRemaining(),
                "Water must be restored to 15 L after refill");
    }

    /**
     * 7. A DECOMMISSIONED drone must not act on a new mission push — it stays
     *    DECOMMISSIONED and never calls onMissionCompleted.
     */
    @Test
    void decommissionedDroneIgnoresNewMissions() throws InterruptedException {
        drone.handleEvent(DroneMachine.droneEvents.DECOMMISSION);
        Thread.sleep(200);  // let the state change propagate

        assertEquals(DroneMachine.DroneState.DECOMMISSIONED, drone.getDroneState());

        // Push a mission (must ignore)
        drone.setMissionCoordinates(1, 0);
        drone.receiveMissionPush(new FireEvent(1, "FIRE", "HIGH", 0));
        Thread.sleep(500);

        assertFalse(callback.missionCompletedFired,
                "A decommissioned drone must never call onMissionCompleted");
        assertEquals(DroneMachine.DroneState.DECOMMISSIONED, drone.getDroneState(),
                "State must remain DECOMMISSIONED after mission push");
    }
}
