import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/**
 * The SchedulerTest class tests the scheduling behaviors
 * of the Scheduler class without the fire/drone subsystems
 * active. Thus, all events and missions are manually simulated.
 *
 * @author Kevin Abeykoon (101301971)
 */

public class SchedulerTest {

    private Scheduler scheduler;

    @BeforeEach
    public void setup() {
        scheduler = new Scheduler(1);
    }

    @Test
    public void testHighSeverityPriority() throws InterruptedException {
        FireEvent lowEvent = new FireEvent(1, "FIRE_DETECTED", "LOW", 0);
        FireEvent highEvent = new FireEvent(2, "FIRE_DETECTED", "HIGH", 0);

        scheduler.receiveFireEvent(lowEvent);
        scheduler.receiveFireEvent(highEvent);
        FireEvent assignedMission = scheduler.requestMission(0);

        assertEquals(FireEvent.FireSeverity.HIGH, assignedMission.getSeverity());
    }

    @Test
    public void testPartialMissionRescheduling() throws InterruptedException {
        FireEvent highEvent = new FireEvent(1, "FIRE_DETECTED", "HIGH", 0);

        scheduler.receiveFireEvent(highEvent);
        FireEvent assigned = scheduler.requestMission(0);

        assertTrue(assigned.getWaterRemaining() > 0);
    }

    @Test
    public void testRescheduleUnfinishedEventGoesToFront() throws InterruptedException {
        FireEvent eventHigh = new FireEvent(1, "FIRE_DETECTED", "HIGH", 0);
        FireEvent eventLow = new FireEvent(2, "FIRE_DETECTED", "LOW", 0);

        scheduler.receiveFireEvent(eventHigh);
        scheduler.receiveFireEvent(eventLow);


        FireEvent firstAssignment = scheduler.requestMission(0);

        // Simulate partial usage (no drones here to use the water)
        firstAssignment.waterUsed(10);
        scheduler.rescheduleUnfinishedFireEvent(firstAssignment);

        FireEvent secondAssignment = scheduler.requestMission(0);

        assertEquals(firstAssignment.getZoneId(), secondAssignment.getZoneId());
    }
}