/**
 * The DroneSwarmFrame is the main class for the GUI representation of the 
 * simulation. It displays the zones, log, drone movement, fires, etc.
 * 
 * @author Aryan Kumar Singh (101299776)
 */

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.util.List;

public class DroneSwarmFrame extends JFrame {
    private MapPanel mapPanel;
    private StatusPanel statusPanel;
    private ControlPanel controlPanel;
    private DroneStatusPanel droneStatusPanel;

    private Scheduler model;
    private Timer mapTimer;    // fast  — smooth drone movement on the map
    private Timer statusTimer; // slow  — drone table + fire count label
    private int lastZonesVersion = -1;

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

        // Fast timer: only redraws the map (one synchronized snapshot call)
        mapTimer = new Timer(100, e -> refreshMap());
        mapTimer.start();

        // Slow timer: updates the drone status table and fire counts
        statusTimer = new Timer(500, e -> droneStatusPanel.refreshData());
        statusTimer.start();
    }

    private void refreshMap() {
        if (model != null) {
            int version = model.getZonesVersion();
            if (version != lastZonesVersion) {
                lastZonesVersion = version;
                mapPanel.setZones(model.getZones());
            }
            Scheduler.GuiSnapshot snap = model.getGuiSnapshot();
            mapPanel.updateDronesAndFires(snap.drones, snap.firesPerZone);
        }
    }


    public MapPanel getMapPanel() { return mapPanel; }
    public StatusPanel getStatusPanel() { return statusPanel; }
    public ControlPanel getControlPanel() { return controlPanel; }


    public class ControlPanel extends JPanel {
        private StatusPanel statusPanel;
        private JButton loadFileButton;
        private JButton loadZoneButton;
        private JButton startButton;
        private JButton stopButton;
        private JLabel fileLabel;
        private String selectedFireFile = null;

        public ControlPanel(StatusPanel statusPanel) {
            this.statusPanel = statusPanel;
            setLayout(new FlowLayout(FlowLayout.LEFT));
            setBorder(new TitledBorder("Controls"));

            // --- Load incident CSV ---
            loadFileButton = new JButton("Load Incident File");
            loadFileButton.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    selectedFireFile = chooser.getSelectedFile().getAbsolutePath();
                    fileLabel.setText(chooser.getSelectedFile().getName());
                    startButton.setEnabled(true);
                    statusPanel.logMessage("Incident file loaded: " + chooser.getSelectedFile().getName());
                }
            });

            // --- Load zone CSV ---
            loadZoneButton = new JButton("Load Zone File");
            loadZoneButton.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String zonePath = chooser.getSelectedFile().getAbsolutePath();
                    try {
                        List<String> errors = model.loadZonesFromFile(zonePath);
                        if (errors.isEmpty()) {
                            mapPanel.setZones(model.getZones());
                            statusPanel.logMessage("Zones loaded: " + chooser.getSelectedFile().getName()
                                    + " (" + model.getZones().size() + " zones)");
                        } else {
                            JOptionPane.showMessageDialog(this,
                                    String.join("\n", errors),
                                    "Zone Load Errors", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this,
                                "Error loading zone file: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            // --- Start simulation: send loadFile to the running FireIncidentSubsystem ---
            startButton = new JButton("Start Simulation");
            startButton.setEnabled(false);
            startButton.addActionListener(e -> {
                if (selectedFireFile != null) {
                    try {
                        byte[] data = ("loadFile|" + selectedFireFile).getBytes();
                        java.net.DatagramSocket sock = new java.net.DatagramSocket();
                        sock.send(new java.net.DatagramPacket(data, data.length,
                                java.net.InetAddress.getByName("localhost"),
                                FireIncidentSubsystem.PORT));
                        sock.close();
                        startButton.setEnabled(false);
                        stopButton.setEnabled(true);
                        statusPanel.logMessage("Simulation started.");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this,
                                "Could not reach FireIncidentSubsystem on port "
                                        + FireIncidentSubsystem.PORT
                                        + " — is FireIncidentMain running?\n" + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            // --- Stop button (UI feedback only — scheduler keeps running) ---
            stopButton = new JButton("Stop Simulation");
            stopButton.setEnabled(false);
            stopButton.addActionListener(e -> {
                statusPanel.logMessage("Simulation stopped by user.");
                stopButton.setEnabled(false);
            });

            fileLabel = new JLabel("No file loaded");

            add(loadFileButton);
            add(loadZoneButton);
            add(startButton);
            add(stopButton);
            add(fileLabel);
        }
    }
}
