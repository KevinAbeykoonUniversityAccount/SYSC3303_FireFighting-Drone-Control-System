import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for soft fault (DRONE_STUCK) and hard fault (NOZZLE_FAULT)
 * behaviour in DroneMachine.
 *
 * Faults are injected via DroneMachine.injectFault(), which is exactly
 * what DroneSubsystem calls when it receives an INJECT_FAULT UDP packet.
 * A simple StubCallback records what the drone reports back so we can
 * assert on state transitions without needing a real Scheduler or network.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public class FaultHandlingTest {

    // Simple stub that records what the drone reports back
    private static class StubCallback implements DroneCallback {
        boolean hardFaultFired  = false;
        boolean recoveredFired  = false;
        boolean rescheduleFired = false;
        boolean locationFaulted = false;   // true once onLocationUpdate("FAULTED") fires
        boolean locationResumed = false;   // true once ONROUTE is seen after FAULTED

        @Override
        public void onLocationUpdate(int id, int x, int y, String state) {
            if ("FAULTED".equals(state))  locationFaulted = true;
            if (locationFaulted && "ONROUTE".equals(state)) locationResumed = true;
        }

        @Override public void onMissionCompleted(int id, int zoneId, int water) {}
        @Override public void onRescheduleFireEvent(int id, FireEvent e) { rescheduleFired = true; }
        @Override public void onDroneRefilling(int id) {}
        @Override public void onDroneRefillComplete(int id) {}
        @Override public void onHardFault(int id)      { hardFaultFired  = true; }
        @Override public void onDroneRecovered(int id) { recoveredFired  = true; }
    }

    private DroneMachine drone;
    private StubCallback callback;

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

    // Give the drone a mission so it starts flying — fault can then be injected mid-flight
    private void startMission() {
        drone.receiveMissionPush(new FireEvent(1, "FIRE", "HIGH", 0));
    }

    // ============================================================
    // SOFT FAULT — DRONE_STUCK
    // ============================================================

    @Test
    void testSoftFaultStateBecomesFaulted() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.DRONE_STUCK);
        Thread.sleep(2000); // fault is picked up within 200 ms; 2 s is plenty

        assertEquals(DroneMachine.DroneState.FAULTED, drone.getDroneState());
    }

    @Test
    void testSoftFaultReportsLocationAsFaulted() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.DRONE_STUCK);
        Thread.sleep(2000);

        assertTrue(callback.locationFaulted,
                "onLocationUpdate must be called with state FAULTED during a soft fault");
    }

    @Test
    void testSoftFaultDoesNotTriggerHardFault() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.DRONE_STUCK);
        Thread.sleep(2000);

        assertFalse(callback.hardFaultFired,
                "A soft fault must never call onHardFault");
    }

    @Test
    void testSoftFaultDoesNotDecommissionDrone() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.DRONE_STUCK);
        Thread.sleep(2000);

        assertNotEquals(DroneMachine.DroneState.DECOMMISSIONED, drone.getDroneState());
    }

    @Test
    void testSoftFaultDoesNotRescheduleMission() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.DRONE_STUCK);
        Thread.sleep(2000);

        assertFalse(callback.rescheduleFired,
                "Soft fault should not reschedule — drone pauses then continues the same mission");
    }

    /**
     * After the 10 s freeze the drone resumes flying and sends an ONROUTE
     * location update.  That is the observable recovery signal for a fault
     * that fires mid-flight (onDroneRecovered is only called for faults that
     * fire during extinguishing, not during movement).
     */
    @Test
    @Timeout(20)
    void testSoftFaultDroneResumesAfterRecovery() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.DRONE_STUCK);
        Thread.sleep(12000); // 10 s freeze + 2 s buffer

        assertTrue(callback.locationResumed,
                "Drone should send an ONROUTE location update after recovering from a soft fault");
        assertNotEquals(DroneMachine.DroneState.FAULTED, drone.getDroneState());
    }

    // ============================================================
    // HARD FAULT — NOZZLE_FAULT
    // ============================================================

    @Test
    void testHardFaultCallsOnHardFault() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.NOZZLE_FAULT);
        Thread.sleep(2000);

        assertTrue(callback.hardFaultFired,
                "onHardFault must be called when a NOZZLE_FAULT is injected");
    }

    @Test
    void testHardFaultStateBecomesFaulted() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.NOZZLE_FAULT);
        Thread.sleep(2000);

        // Internal registry and state are set to decomissioned
        assertEquals(DroneMachine.DroneState.DECOMMISSIONED, drone.getDroneState());
    }

    @Test
    void testHardFaultDecommissionTransition() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.NOZZLE_FAULT);
        Thread.sleep(2000);

        drone.handleEvent(DroneMachine.droneEvents.DECOMMISSION);
        Thread.sleep(500);

        assertEquals(DroneMachine.DroneState.DECOMMISSIONED, drone.getDroneState(),
                "Drone must be DECOMMISSIONED after NOZZLE_FAULT + DECOMMISSION event");
    }

    @Test
    void testHardFaultDoesNotCallOnDroneRecovered() throws InterruptedException {
        startMission();
        Thread.sleep(300);
        drone.injectFault(FaultType.NOZZLE_FAULT);
        Thread.sleep(2000);

        assertFalse(callback.recoveredFired,
                "onDroneRecovered must never be called after a hard fault");
    }
}
