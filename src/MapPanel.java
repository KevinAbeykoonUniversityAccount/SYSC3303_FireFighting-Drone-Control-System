
import javax.swing.*;
import java.awt.*;
import java.util.*;

public class MapPanel extends JPanel {
    private java.util.List<Zone> zones = new ArrayList<>();
    private java.util.List<Drone> drones = new ArrayList<>();

    public MapPanel() {
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

        // Found out how to add them based off threads, currently just manually typing them
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Draw zones and drones

        // Draw grid lines
        g2d.setColor(Color.GRAY);
        for (int x = 0; x < getWidth(); x += 50) {
            g2d.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += 50) {
            g2d.drawLine(0, y, getWidth(), y);
        }
    }

    // Simple data classes for visualization
    class Zone {
        // How to represent the zones and fire incidents as GUI objects
    }

    class Drone {
        // How to represent drone as a GUI object
    }
}