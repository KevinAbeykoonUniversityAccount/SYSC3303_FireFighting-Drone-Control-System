package com.sysc3303;

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

    private Scheduler model;

    public DroneSwarmFrame(Scheduler model) {
        setTitle("Firefighting Drone Swarm - Control Center");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create panels
        mapPanel = new MapPanel();
        statusPanel = new StatusPanel();
        controlPanel = new ControlPanel(statusPanel);

        // Add panels to frame
        add(mapPanel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.EAST);
        add(controlPanel, BorderLayout.SOUTH);

        // Set size and make visible
        setSize(1400, 900);
        setLocationRelativeTo(null); // Center on screen

        // Attach model to this view
        this.model = model;


    }


    // Inner class for control panel   ? I TRIED SEPERATE CLASSES BUT MADE MVC CONFUSING?
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


    public MapPanel getMapPanel() { return mapPanel; }
    public StatusPanel getStatusPanel() { return statusPanel; }
    public ControlPanel getControlPanel() { return controlPanel; }
}
