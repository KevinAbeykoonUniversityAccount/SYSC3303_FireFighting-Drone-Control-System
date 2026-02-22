import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroneSubSystemTest2 {
    private DroneSubsystem droneSubsystem;
    private FireEvent fireEvent;
    private Scheduler scheduler;

    @BeforeEach
    public void setup() {
        scheduler = new Scheduler(1);
        fireEvent = new FireEvent(0, "TestEvent", "HIGH", 0);
        scheduler.receiveFireEvent(fireEvent);
        droneSubsystem = new DroneSubsystem(0, scheduler);
        scheduler.registerDrone(droneSubsystem);
    }

    @Test
    public void testStates() {
        assertEquals(DroneSubsystem.DroneState.IDLE, droneSubsystem.getDroneState());
        droneSubsystem.performAction();
        assertEquals(DroneSubsystem.DroneState.ONROUTE, droneSubsystem.getDroneState());
        droneSubsystem.performAction();
        assertEquals(DroneSubsystem.DroneState.EXTINGUISHING, droneSubsystem.getDroneState());
        droneSubsystem.performAction();
        assertEquals(DroneSubsystem.DroneState.REFILLING, droneSubsystem.getDroneState());
        droneSubsystem.performAction();
        assertEquals(DroneSubsystem.DroneState.IDLE, droneSubsystem.getDroneState());
    }
}
