/**
 * The DroneSwarmFrame is the main class for the GUI representation of the 
 * simulation. It displays the zones, log, drone movement, fires, etc.
 * 
 * @author Aryan Kumar Singh (101299776)
 */

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class DroneSwarmFrame extends JFrame {
    private MapPanel mapPanel;
    private StatusPanel statusPanel;
    private ControlPanel controlPanel;
    private DroneStatusPanel droneStatusPanel;

    private Scheduler model;
    private Timer refreshTimer;

    public DroneSwarmFrame(Scheduler model) {
        setTitle("Firefighting Drone Swarm - Control Center");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Attach model to this view
        this.model = model;


        // Create panels
        mapPanel = new MapPanel();
        statusPanel = new StatusPanel();
        controlPanel = new ControlPanel(statusPanel);
        droneStatusPanel = new DroneStatusPanel(model);

        // Add panels to frame
        JScrollPane mapScrollPane = new JScrollPane(mapPanel);
        mapScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mapScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(mapScrollPane, BorderLayout.CENTER);

        add(droneStatusPanel, BorderLayout.WEST);   // Drone status on left
        add(statusPanel, BorderLayout.EAST);         // System log on right
        add(controlPanel, BorderLayout.SOUTH);     // Input panel along bottom

        // Set size and make visible
        setSize(1600, 1050);
        setLocationRelativeTo(null); // Center on screen

        // Set up refresh timer for GUI elements (every 500 ms)
        refreshTimer = new Timer(500, e -> refreshDisplay());
        refreshTimer.start();
    }


    private void refreshDisplay() {
        if (model != null) {
            // Update drone positions and fire severities on map
            mapPanel.updateDronesAndFires(
                    model.getDroneStates(),
                    model.getActiveFiresPerZone()
            );

            // Update drone status table and fire counts
            droneStatusPanel.refreshData();
        }
    }


    public MapPanel getMapPanel() { return mapPanel; }
    public StatusPanel getStatusPanel() { return statusPanel; }
    public ControlPanel getControlPanel() { return controlPanel; }


    public class ControlPanel extends JPanel {
        private StatusPanel statusPanel; // Reference to update logs
        private JButton loadFileButton;
        private JButton startButton;
        private JButton stopButton;
        private JLabel fileLabel;

        public ControlPanel(StatusPanel statusPanel) {
            this.statusPanel = statusPanel;
            setLayout(new FlowLayout(FlowLayout.LEFT));
            setBorder(new TitledBorder("Controls"));

            // File selection button
            loadFileButton = new JButton("Load Incident File");
            //loadFileButton.addActionListener();

            // Start/Stop buttons
            startButton = new JButton("Start Simulation");
            startButton.setEnabled(false); // Disabled until file loaded
            //startButton.addActionListener();

            stopButton = new JButton("Stop Simulation");
            stopButton.setEnabled(false);
            //stopButton.addActionListener();

            // File name display
            fileLabel = new JLabel("No file loaded");

            // Add components
            add(loadFileButton);
            add(startButton);
            add(stopButton);
            add(fileLabel);
        }
    }
}
