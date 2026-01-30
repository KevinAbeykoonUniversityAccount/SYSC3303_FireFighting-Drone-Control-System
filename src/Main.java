import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Initialize centralized clock
            SimulationClock clock = SimulationClock.getInstance();

            // Set simulation to start at a specific time and the tick speed to 4x
            clock.setSimulationStartTime(0, 0, 0); // Start at 14:00:00
            // clock.setClockSpeedMultiplier(1); NEED TO REDO, DOES NOT SPEED OR SLOW DOWN

            // Create shared scheduler
            Scheduler scheduler = new Scheduler(1); // One drone for beginning

            // Create fire subsystem thread
            String inputFile = "src/Sample_event_file.csv";
            Thread fireSubsystem = new Thread(new FireIncidentSubsystem(scheduler, inputFile), "FireSubsystem");

            // Create the drone subsystem threads


            // DroneSwarmFrame gui = new DroneSwarmFrame(scheduler);
            //gui.setVisible(true);

            // Start the subsystem threads
            fireSubsystem.start();



        });
    }
}