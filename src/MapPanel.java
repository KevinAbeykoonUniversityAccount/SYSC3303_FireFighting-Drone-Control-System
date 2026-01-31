import javax.swing.*;
import java.awt.*;
import java.util.*;

public class MapPanel extends JPanel {
    private java.util.List<Zone> zones = new ArrayList<>();
    private java.util.List<Drone> drones = new ArrayList<>();

    // Grid properties: 30x30 cells, each representing 100m x 100m
    private final int GRID_CELLS = 30;
    private final int CELL_SIZE_PX = 25;

    public MapPanel() {
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

        // Create 4 zones (in grid coordinates, not pixels)
        // Each zone is 15x15 grid cells (1500m x 1500m)
        zones.add(new Zone(1, 0, 0, 14, 14));      // Top-left
        zones.add(new Zone(2, 15, 0, 29, 14));     // Top-right
        zones.add(new Zone(3, 0, 15, 14, 29));     // Bottom-left
        zones.add(new Zone(4, 15, 15, 29, 29));    // Bottom-right
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Draw grid lines
        g2d.setColor(Color.GRAY);
        for (int x = 0; x <= GRID_CELLS * CELL_SIZE_PX; x += CELL_SIZE_PX) {
            g2d.drawLine(x, 0, x, GRID_CELLS * CELL_SIZE_PX);
        }
        for (int y = 0; y <= GRID_CELLS * CELL_SIZE_PX; y += CELL_SIZE_PX) {
            g2d.drawLine(0, y, GRID_CELLS * CELL_SIZE_PX, y);
        }

        // Draw zones (outlines only)
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));
        for (Zone zone : zones) {
            int x1 = zone.x1 * CELL_SIZE_PX;
            int y1 = zone.y1 * CELL_SIZE_PX;
            int width = (zone.x2 - zone.x1 + 1) * CELL_SIZE_PX;
            int height = (zone.y2 - zone.y1 + 1) * CELL_SIZE_PX;

            g2d.drawRect(x1, y1, width, height);

            // Draw zone label
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            String label = "Zone " + zone.id;
            g2d.drawString(label, x1 + 5, y1 + 15);
        }

        // Draw title
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("3km x 3km Area (30x30 Grid)", 10, GRID_CELLS * CELL_SIZE_PX + 20);
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.drawString("Each cell: 100m x 100m", 10, GRID_CELLS * CELL_SIZE_PX + 35);
    }

    // Zone visualization
    class Zone {
        int id;
        int x1, y1, x2, y2; // Grid coordinates (0-29)

        Zone(int id, int x1, int y1, int x2, int y2) {
            this.id = id;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    // Drone visualization
    class Drone {
        int id;
        int x, y; // Grid coordinates

        Drone(int id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // Make panel big enough to show the 30x30 grid
        return new Dimension(GRID_CELLS * CELL_SIZE_PX + 40,
                GRID_CELLS * CELL_SIZE_PX + 60);
    }
}