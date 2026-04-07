import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests for battery drain and decommission behaviour in DroneMachine.
 *
 * Tests involving movement sleep ~1 s per grid cell.
 * The soft-fault test waits ~10 s for recovery; timeout is set accordingly.
 */
public class BatteryTest {

    private static class StubCallback implements DroneCallback {
        volatile int     lastBattery    = 100;
        volatile int     batteryUpdates = 0;
        volatile boolean hardFaultFired = false;

        @Override public void onBatteryUpdate(int id, int battery) {
            lastBattery = battery;
            batteryUpdates++;
        }
        @Override public void onHardFault(int id)                               { hardFaultFired = true; }
        @Override public void onLocationUpdate(int id, int x, int y, String s)  {}
        @Override public void onMissionCompleted(int id, int z, int w)          {}
        @Override public void onRescheduleFireEvent(int id, FireEvent e)        {}
        @Override public void onDroneRefilling(int id)                          {}
        @Override public void onDroneRefillComplete(int id)                     {}
        @Override public void onDroneRecovered(int id)                          {}
    }

    private DroneMachine startDaemon(DroneMachine drone) {
        drone.setDaemon(true);
        drone.start();
        return drone;
    }

    private void pushMission(DroneMachine drone, int zoneId, int tx, int ty) {
        drone.setMissionCoordinates(tx, ty);
        drone.receiveMissionPush(new FireEvent(zoneId, "FIRE", "LOW", 0));
    }


    /** 1. A fresh drone starts at 100 % battery. */
    @Test
    public void initialBatteryIs100() {
        DroneMachine drone = new DroneMachine(1, new StubCallback());
        assertEquals(100, drone.getBatteryRemaining());
    }

    /** 2. Battery decreases by 1 for each grid cell moved. */
    @Test(timeout = 5000)
    public void batteryDecreasesOnePerMovementStep() throws InterruptedException {
        StubCallback cb = new StubCallback();
        DroneMachine drone = startDaemon(new DroneMachine(1, cb));

        pushMission(drone, 1, 2, 0); // 2 steps -> should drop to 98
        Thread.sleep(4000);

        assertTrue("Battery must fall after 2 steps", cb.lastBattery <= 98);
        drone.interrupt();
    }

    /** 3. onBatteryUpdate callback is fired during movement. */
    @Test(timeout = 5000)
    public void batteryUpdateCallbackFiredDuringMovement() throws InterruptedException {
        StubCallback cb = new StubCallback();
        DroneMachine drone = startDaemon(new DroneMachine(1, cb));

        pushMission(drone, 1, 1, 0); // 1 step
        Thread.sleep(3000);

        assertTrue("onBatteryUpdate must fire during movement", cb.batteryUpdates > 0);
        drone.interrupt();
    }

    /** 4. Battery is fully restored to 100 % after a refill/recharge cycle. */
    @Test(timeout = 12000)
    public void batteryRestoredToFullAfterRefill() throws InterruptedException {
        StubCallback cb = new StubCallback();
        DroneMachine drone = new DroneMachine(1, cb, 50); // start at 50 %
        drone.refillWaterAndRechargeBattery();             // blocking ~6 s
        assertEquals(100, drone.getBatteryRemaining());
    }

    /** 5. Battery level never goes below zero regardless of drain count. */
    @Test(timeout = 5000)
    public void batteryNeverDropsBelowZero() throws InterruptedException {
        StubCallback cb = new StubCallback();
        DroneMachine drone = startDaemon(new DroneMachine(1, cb, 2)); // 2 % start

        pushMission(drone, 1, 10, 0); // far target ensures depletion
        Thread.sleep(4000);

        assertTrue("Battery must not go negative", cb.lastBattery >= 0);
        drone.interrupt();
    }

    /** 6. Drone transitions to DECOMMISSIONED and fires onHardFault when battery hits zero. */
    @Test(timeout = 5000)
    public void droneDecommissionedWhenBatteryReachesZero() throws InterruptedException {
        StubCallback cb = new StubCallback();
        DroneMachine drone = startDaemon(new DroneMachine(1, cb, 2));

        pushMission(drone, 1, 10, 0);
        Thread.sleep(4000);

        assertEquals(DroneMachine.DroneState.DECOMMISSIONED, drone.getDroneState());
        assertTrue("onHardFault must fire on battery depletion", cb.hardFaultFired);
    }

    /**
     * 7. A DRONE_STUCK soft fault drains exactly 5 % battery over the
     *    10-second recovery pause (+/-1 for integer rounding per tick).
     */
    @Test(timeout = 20000)
    public void softFaultDrainsFivePercentBattery() throws InterruptedException {
        StubCallback cb = new StubCallback();
        DroneMachine drone = startDaemon(new DroneMachine(1, cb));

        pushMission(drone, 1, 20, 0); // long trip so drone is ONROUTE during fault
        Thread.sleep(2000);           // let it begin moving

        int batteryBeforeFault = cb.lastBattery;
        drone.injectFault(FaultType.DRONE_STUCK);

        Thread.sleep(12000); // 10 s pause + margin

        int drained = batteryBeforeFault - cb.lastBattery;
        assertTrue("Soft fault must drain ~5 % (got " + drained + " %)",
                drained >= 4 && drained <= 6);

        drone.interrupt();
    }
}
