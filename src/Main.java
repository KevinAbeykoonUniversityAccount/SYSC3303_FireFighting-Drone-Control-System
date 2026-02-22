import javax.swing.*;
/**
 * The Main class creates and starts the scheduler, drone, and 
 * FireSubsytem thread. It also creates the GUI window.
 *
 * @author Aryan Kumar Singh (101299776)
 */

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Initialize centralized clock
            SimulationClock clock = SimulationClock.getInstance();

            // Set the start time of simulated clock
            clock.setSimulationStartTime(0, 0, 0);

            // Set clock speed: 60 = 1 real second = 1 simulated minute
            clock.setClockSpeedMultiplier(60);
            Thread clockThread = new Thread(clock, "SimulationClock");
            clockThread.start();

            // Create scheduler with number of drones
            int numberOfDrones = 3;
            Scheduler scheduler = new Scheduler(numberOfDrones);

            // Create and start drones threads
            for (int i = 0; i < numberOfDrones; i++) {
                DroneSubsystem drone = new DroneSubsystem(i, scheduler);
                scheduler.registerDrone(drone); // Register the drone with scheduler
                drone.start(); // Start drone thread
            }

            // Create and start fire subsystem thread
            String inputFile = "src/Sample_event_file.csv";
            Thread fireSubsystem = new Thread(new FireIncidentSubsystem(scheduler, inputFile), "FireSubsystem");
            fireSubsystem.start();

            DroneSwarmFrame gui = new DroneSwarmFrame(scheduler);
            gui.setVisible(true);
        });
    }
}
