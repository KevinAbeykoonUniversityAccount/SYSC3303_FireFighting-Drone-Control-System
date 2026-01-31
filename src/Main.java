import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Initialize centralized clock
            SimulationClock clock = SimulationClock.getInstance();

            // Create scheduler with number of drones
            int numberOfDrones = 2;
            Scheduler scheduler = new Scheduler(numberOfDrones);

            // Create and start drones threads
            for (int i = 0; i < numberOfDrones; i++) {
                DroneSubsystem drone = new DroneSubsystem(i, scheduler);
                drone.start(); // Start drone thread
            }

            // Create and start fire subsystem thread
            String inputFile = "src/Sample_event_file.csv";
            Thread fireSubsystem = new Thread(new FireIncidentSubsystem(scheduler, inputFile), "FireSubsystem");
            fireSubsystem.start();

            // Set simulation to start at a specific time and the tick speed to 4x
            clock.setSimulationStartTime(0, 0, 0); // Start at 14:00:00
            clock.setClockSpeedMultiplier(1);
        });
    }
}