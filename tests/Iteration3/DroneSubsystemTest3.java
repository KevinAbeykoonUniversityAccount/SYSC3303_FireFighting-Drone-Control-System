import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DroneSubsystemTest3 {

    private DroneSubsystem drone;

    @BeforeEach
    public void setup() throws Exception {

        drone = new DroneSubsystem(
                1,
                "localhost",
                Scheduler.PORT
        );
    }

    @Test
    public void testInitialState() {
        assertEquals(DroneSubsystem.DroneState.IDLE, drone.getDroneState());
    }

    @Test
    public void testMissionAssigned() {
        FireEvent fireEvent = new FireEvent(1, "TestFire", "HIGH", 10);

        drone.receiveMissionPush(fireEvent);

        assertEquals(fireEvent, drone.getIncomingMission());
    }

    @Test
    public void testWaterUsage() throws InterruptedException {
        int startWater = drone.getWaterRemaining();

        int used = drone.extinguishFire(5);

        assertEquals(5, used);
        assertEquals(startWater - 5, drone.getWaterRemaining());
    }

    @Test
    public void testRefillWater() throws InterruptedException {
        drone.extinguishFire(10);
        drone.refillWater();

        assertEquals(15, drone.getWaterRemaining());
    }

    @Test
    public void testDecommissionState() {
        drone.handleEvent(DroneSubsystem.droneEvents.DECOMMISSION);

        assertEquals(DroneSubsystem.DroneState.DECOMMISSIONED, drone.getDroneState());
    }
}
