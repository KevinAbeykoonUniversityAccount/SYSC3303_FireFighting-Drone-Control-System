import javax.swing.*;
import java.io.*;

/**
 * Entry point for the Scheduler process. Start this first.
 *
 * Usage:   java SchedulerMain [clockSpeed]
 * Example: java SchedulerMain 60   (1 simulation minute in one real second)
 */
public class SchedulerMain {
    public static void main(String[] args) throws Exception {
        Scheduler scheduler = new Scheduler();
        new Thread(scheduler, "Scheduler").start();

        System.out.println("Scheduler running on port " + Scheduler.PORT);


        // Launch GUI on the Swing event thread
        SwingUtilities.invokeLater(() -> {
            DroneSwarmFrame frame = new DroneSwarmFrame(scheduler);
            frame.setVisible(true);
        });
    }
}
