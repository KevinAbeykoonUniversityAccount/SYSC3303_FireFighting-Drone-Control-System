
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
/**
 * This StatusPanel class is the panel that contains the 
 * log information about whats happening in a textual form.
 *
 * @author Aryan Kumar Singh (101299776)
 */

public class StatusPanel extends JPanel {
    private JTextArea statusArea;
    private JScrollPane scrollPane;

    public StatusPanel() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("System Log"));
        setPreferredSize(new Dimension(300, 0));

        statusArea = new JTextArea(20, 25);
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        scrollPane = new JScrollPane(statusArea);
        add(scrollPane, BorderLayout.CENTER);

        // Add clear button
        JButton clearButton = new JButton("Clear Log");
        clearButton.addActionListener(e -> statusArea.setText(""));
        add(clearButton, BorderLayout.SOUTH);

        logMessage("System initialized");
        logMessage("Waiting for input file...");
    }

    public void logMessage(String message) {
        // Need to have it be set by model telling view based on actual incident reporting
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        statusArea.append("[" + timestamp + "] " + message + "\n");
        statusArea.setCaretPosition(statusArea.getDocument().getLength()); // Auto-scroll
    }
}
