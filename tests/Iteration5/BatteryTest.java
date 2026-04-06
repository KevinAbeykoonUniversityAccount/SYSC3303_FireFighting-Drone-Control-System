import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

    class BatteryTest {

        private DroneMachine drone;

        // Minimal implementation to avoid compiler complaints
        private final DroneCallback mockCallback = new DroneCallback() {
            @Override public void onLocationUpdate(int droneId, int x, int y, String state) {}
            @Override public void onBatteryUpdate(int droneId, int battery) {}
            @Override public void onMissionCompleted(int droneId, int zoneId, int waterUsed) {}
            @Override public void onRescheduleFireEvent(int droneId, FireEvent abandonedMission) {}
            @Override public void onDroneRefilling(int droneId) {}
            @Override public void onDroneRefillComplete(int droneId) {}
            @Override public void onHardFault(int droneId) {}
            @Override public void onDroneRecovered(int droneId) {}
        };

        @BeforeEach
        void setup() {
            drone = new DroneMachine(1, mockCallback);
        }

        @Test
        void testBatteryDecreasesMidFlight() throws InterruptedException {
            DroneMachine drone = new DroneMachine(1, mockCallback);
            drone.setMissionCoordinates(10, 10);
            int startBattery = drone.getBatteryRemaining();

            // run moveDrone in its own thread so I can interrupt it before it recharges
            Thread moveThread = new Thread(() -> {
                try {
                    drone.moveDrone();
                } catch (InterruptedException e) {
                    System.out.println("Drone movement interrupted");
                }
            });
            moveThread.start();

            // Let it move for 3 seconds, then check battery mid flight
            Thread.sleep(3000);

            int midBattery = drone.getBatteryRemaining();
            assertTrue(midBattery < startBattery, "Battery should decrease mid-flight");
        }

        @Test
        void testBatteryRechargeRestoresFullLevel() {
            drone.setMissionCoordinates(3, 3);

            try {
                drone.moveDrone();
            } catch (InterruptedException e) {}

            try {
                drone.refillWaterAndRechargeBattery();
            } catch (InterruptedException e) {
                fail("Recharge should not throw InterruptedException");
            }

            assertEquals(drone.getBatteryCapacity(), drone.getBatteryRemaining(),
                    "Battery should be full after recharge");
        }
    }
