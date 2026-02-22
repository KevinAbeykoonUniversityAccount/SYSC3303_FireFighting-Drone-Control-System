import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroneSubSystemTest {
    private DroneSubsystem droneSubsystem;

    @BeforeEach
    public void setup() { droneSubsystem = new DroneSubsystem(1, new Scheduler(1)); }

    @Test
    public void testSetMissionCoordinates() {
        assertEquals(0, droneSubsystem.getX());
        assertEquals(0, droneSubsystem.getY());

        droneSubsystem.setMissionCoordinates(5, 5);

        assertEquals(5, droneSubsystem.getX());
        assertEquals(5, droneSubsystem.getY());

        droneSubsystem.setMissionCoordinates(6, 7);

        assertEquals(6, droneSubsystem.getX());
        assertEquals(7, droneSubsystem.getY());
    }

    @Test
    public void testMoveDrone() throws InterruptedException {
        assertEquals(0, droneSubsystem.getX());
        assertEquals(0, droneSubsystem.getY());

        droneSubsystem.moveDrone(2, 2);

        assertEquals(2, droneSubsystem.getX());
        assertEquals(2, droneSubsystem.getY());

        droneSubsystem.moveDrone(7, 5);

        assertEquals(7, droneSubsystem.getX());
        assertEquals(5, droneSubsystem.getY());

        droneSubsystem.moveDrone(10, 5);

        assertEquals(10, droneSubsystem.getX());
        assertEquals(5, droneSubsystem.getY());

        droneSubsystem.moveDrone(10, 8);

        assertEquals(10, droneSubsystem.getX());
        assertEquals(8, droneSubsystem.getY());
    }

    @Test
    public void testExtinguishFire() throws InterruptedException {
        assertEquals(15, droneSubsystem.getWaterRemaining());

        droneSubsystem.extinguishFire(5);
        assertEquals(10, droneSubsystem.getWaterRemaining());

        droneSubsystem.extinguishFire(7);
        assertEquals(3, droneSubsystem.getWaterRemaining());

        droneSubsystem.extinguishFire(20);
        assertEquals(0, droneSubsystem.getWaterRemaining());
    }

    @Test
    public void testRefillWater() throws InterruptedException {
        assertEquals(15, droneSubsystem.getWaterRemaining());

        droneSubsystem.extinguishFire(15);
        assertEquals(0, droneSubsystem.getWaterRemaining());

        droneSubsystem.refillWater();
        assertEquals(15, droneSubsystem.getWaterRemaining());

        droneSubsystem.extinguishFire(8);
        assertEquals(7, droneSubsystem.getWaterRemaining());

        droneSubsystem.refillWater();
        assertEquals(15, droneSubsystem.getWaterRemaining());
    }
}
