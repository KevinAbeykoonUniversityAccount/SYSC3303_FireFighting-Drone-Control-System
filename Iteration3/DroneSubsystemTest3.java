import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DroneSubsystemTest3 {

    private DroneSubsystem drone;
    private Scheduler scheduler;
    private FireEvent fireEvent;

    @BeforeEach
    public void setup() {
        scheduler = new Scheduler(1);
        fireEvent = new FireEvent(1, "TestFire", "HIGH", 10);

        drone = new DroneSubsystem(1, scheduler);
        scheduler.registerDrone(drone);
    }

    @Test
    public void testInitialState() {
        assertEquals(DroneSubsystem.DroneState.IDLE, drone.getDroneState());
    }

    @Test
    public void testMissionLifecycleCompletes() {

        drone.incomingMission(fireEvent);
        drone.handleEvent(DroneSubsystem.droneEvents.NEW_MISSION);

        // After the full lifecycle the drone should return to IDLE
        assertEquals(DroneSubsystem.DroneState.IDLE, drone.getDroneState());
        assertNull(drone.getCurrentMission());
    }

    @Test
    public void testMissionCoordinatesSet() {

        drone.setMissionCoordinates(10, 12);

        assertTrue(drone.hasTarget());
        assertEquals(10, drone.getTargetX());
        assertEquals(12, drone.getTargetY());
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