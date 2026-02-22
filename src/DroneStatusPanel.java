import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

/**
 * This DroneStatusPanel class is the panel that displays
 * real-time information about all drones in the swarm,
 * including their state, water level, and position.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public class DroneStatusPanel extends JPanel {
    private JTable droneTable;
    private DefaultTableModel tableModel;
    private JLabel fireCountLabel;
    private Scheduler scheduler;

    public DroneStatusPanel(Scheduler scheduler) {
        this.scheduler = scheduler;
        initializePanel();
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Drone Status",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14)
        ));
        setPreferredSize(new Dimension(300, 0));
        setBackground(new Color(240, 240, 240));

        // Create table model with column names
        String[] columnNames = {"ID", "State", "Water(L)", "Position"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table non-editable
            }
        };

        // Create and configure table
        droneTable = new JTable(tableModel);
        droneTable.setFillsViewportHeight(true);
        droneTable.setRowHeight(25);
        droneTable.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Set column widths
        droneTable.getColumnModel().getColumn(0).setPreferredWidth(30);  // ID
        droneTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // State
        droneTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Water
        droneTable.getColumnModel().getColumn(3).setPreferredWidth(70);  // Position

        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(droneTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(scrollPane, BorderLayout.CENTER);

        // Create fire count panel
        JPanel fireCountPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        fireCountPanel.setBackground(new Color(220, 220, 220));
        fireCountPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        fireCountLabel = new JLabel("Active Fires: 0 (H:0 M:0 L:0)");
        fireCountLabel.setFont(new Font("Arial", Font.BOLD, 12));
        fireCountPanel.add(fireCountLabel);

        add(fireCountPanel, BorderLayout.SOUTH);

        // Initial data load
        refreshData();
    }

    /**
     * Refreshes the drone status table and fire count display
     */
    public void refreshData() {
        if (scheduler == null) return;

        //System.out.println("DroneStatusPanel: Refreshing data..."); // Debug line

        // Clear existing rows
        tableModel.setRowCount(0);

        // Add current drone data
        Map<Integer, DroneSubsystem> drones = scheduler.getDroneStates();
        if (drones != null) {
            for (DroneSubsystem drone : drones.values()) {
                if (drone != null) {
                    String state = formatState(drone.getDroneState());
                    int water = drone.getWaterRemaining();
                    String pos = "(" + drone.getX() + "," + drone.getY() + ")";

                    tableModel.addRow(new Object[]{
                            drone.getDroneId(), state, water, pos
                    });
                }
            }
        }

        // Update fire count label
        int[] counts = scheduler.getFireCountsBySeverity();
        int totalFires = counts[0] + counts[1] + counts[2];
        String fireText = String.format("Active Fires: %d (H:%d M:%d L:%d)",
                totalFires, counts[0], counts[1], counts[2]);
        fireCountLabel.setText(fireText);

        // Set label color based on fire severity
        if (counts[0] > 0) {
            fireCountLabel.setForeground(new Color(180, 0, 0)); // Dark red for high severity
        } else if (counts[1] > 0) {
            fireCountLabel.setForeground(new Color(200, 100, 0)); // Orange for moderate
        } else if (counts[2] > 0) {
            fireCountLabel.setForeground(new Color(150, 150, 0)); // Dark yellow for low
        } else {
            fireCountLabel.setForeground(Color.BLACK); // Black for no fires
        }
    }

    /**
     * Formats the drone state enum into a readable string
     */
    private String formatState(DroneSubsystem.DroneState state) {
        if (state == null) return "UNKNOWN";

        switch (state) {
            case IDLE:
                return "IDLE";
            case ONROUTE:
                return "EN ROUTE";
            case EXTINGUISHING:
                return "EXTINGUISHING";
            case REFILLING:
                return "REFILLING";
            case FAULTED:
                return "FAULTED";
            case DECOMMISSIONED:
                return "DECOMMISSIONED";
            default:
                return state.toString();
        }
    }

    /**
     * Logs a message to the status panel (can be called from scheduler)
     */
    public void logDroneEvent(String message) {
        // This could be expanded to show events in a separate log area
        System.out.println("[DroneStatus] " + message);
    }

    /**
     * Highlights a specific drone in the table
     */
    public void highlightDrone(int droneId) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if ((int) tableModel.getValueAt(i, 0) == droneId) {
                droneTable.setRowSelectionInterval(i, i);
                break;
            }
        }
    }

    /**
     * Clears any drone highlighting
     */
    public void clearHighlight() {
        droneTable.clearSelection();
    }

    /**
     * Gets the selected drone ID from the table
     */
    public Integer getSelectedDrone() {
        int selectedRow = droneTable.getSelectedRow();
        if (selectedRow >= 0) {
            return (Integer) tableModel.getValueAt(selectedRow, 0);
        }
        return null;
    }
}