import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Create shared scheduler
            Scheduler scheduler = new Scheduler(1); // One drone for beginning

            DroneSwarmFrame gui = new DroneSwarmFrame(scheduler);
            gui.setVisible(true);



            // Create and start three threads (not separate programs)
            //

            // DroneSubsystem threads 1..*
            // Code here

            // Scheduler doesn't need its own thread - it's just shared object

            // fireThread.start();
            // droneThread1.start();
            // ...
        });
    }
}