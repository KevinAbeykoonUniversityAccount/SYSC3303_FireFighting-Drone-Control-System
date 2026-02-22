import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This MapPanel class is the panel that contains the
 * zone map, with all the visual fire and drone information.
 *
 * @author Aryan Kumar Singh (101299776)
 */
public class MapPanel extends JPanel {
    private java.util.List<Zone> zones = new ArrayList<>();
    private Map<Integer, DroneSubsystem> drones = new HashMap<>();
    private Map<Integer, Integer> fireSeverityMap = new HashMap<>(); // zone -> total water needed

    // Grid properties: 30x30 cells, each representing 100m x 100m
    private final int GRID_CELLS = 30;
    private final int CELL_SIZE_PX = 25;

    // Track fire cells: zone ID -> list of cell coordinates that are on fire
    private Map<Integer, List<Point>> fireCells = new ConcurrentHashMap<>();
    // Track water needed per cell
    private Map<Point, Integer> cellWaterNeeded = new HashMap<>();
    // Track cells being extinguished
    private Set<Point> extinguishingCells = new HashSet<>();

    public MapPanel() {
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

        // Create 4 zones (in grid coordinates)
        zones.add(new Zone(1, 0, 0, 14, 14));      // Top-left
        zones.add(new Zone(2, 15, 0, 29, 14));     // Top-right
        zones.add(new Zone(3, 0, 15, 14, 29));     // Bottom-left
        zones.add(new Zone(4, 15, 15, 29, 29));    // Bottom-right
    }

    /**
     * Update drone positions and fire data from scheduler
     */
    public void updateDronesAndFires(Map<Integer, DroneSubsystem> droneMap,
                                     Map<Integer, Integer> zoneWater) {
        this.drones = droneMap;
        this.fireSeverityMap = zoneWater;
        // Convert zone water to fire cells
        updateFireCells(zoneWater);
        repaint(); // Trigger redraw
    }

    /**
     * Convert zone water requirements to individual fire cells at zone centers
     */
    private void updateFireCells(Map<Integer, Integer> zoneWater) {
        fireCells.clear();
        cellWaterNeeded.clear();

        for (Map.Entry<Integer, Integer> entry : zoneWater.entrySet()) {
            int zoneId = entry.getKey();
            int totalWater = entry.getValue();

            if (totalWater > 0) {
                Zone zone = getZoneById(zoneId);
                if (zone != null) {
                    // Calculate number of fire cells based on water needed
                    // Each cell starts with 5L of water requirement
                    int numFireCells = (int) Math.ceil(totalWater / 5.0);

                    // Generate fire cells at the CENTER of the zone, not random
                    List<Point> cells = generateCenterFireCells(zone, numFireCells);
                    fireCells.put(zoneId, cells);

                    // Distribute water among cells
                    int waterPerCell = totalWater / numFireCells;
                    int remainder = totalWater % numFireCells;

                    for (int i = 0; i < cells.size(); i++) {
                        Point cell = cells.get(i);
                        int cellWater = waterPerCell + (i < remainder ? 1 : 0);
                        cellWaterNeeded.put(cell, cellWater);
                    }
                }
            }
        }
    }

    /**
     * Generate fire cells clustered at the center of the zone
     */
    private List<Point> generateCenterFireCells(Zone zone, int count) {
        List<Point> cells = new ArrayList<>();

        // Calculate center of zone
        int centerX = (zone.x1 + zone.x2) / 2;
        int centerY = (zone.y1 + zone.y2) / 2;

        // Generate cells in a 3x3 grid around the center
        int[] offsets = {-1, 0, 1};

        for (int i = 0; i < count && i < 9; i++) {
            int x = centerX + offsets[i % 3];
            int y = centerY + offsets[i / 3];
            // Ensure within zone bounds
            x = Math.max(zone.x1, Math.min(zone.x2, x));
            y = Math.max(zone.y1, Math.min(zone.y2, y));
            cells.add(new Point(x, y));
        }

        return cells;
    }

    /**
     * Mark a cell as being extinguished
     */
    public void markCellExtinguishing(int x, int y) {
        extinguishingCells.add(new Point(x, y));
        repaint();
    }

    /**
     * Mark a cell as extinguished (remove fire)
     */
    public void markCellExtinguished(int x, int y) {
        Point cell = new Point(x, y);
        extinguishingCells.remove(cell);
        cellWaterNeeded.remove(cell);

        // Remove from fireCells map
        for (List<Point> cells : fireCells.values()) {
            cells.remove(cell);
        }
        repaint();
    }

    /**
     * Get the background color for a zone based on fire status
     */
    private Color getZoneBackgroundColor(int zoneId) {
        Integer waterNeeded = fireSeverityMap.get(zoneId);
        if (waterNeeded == null || waterNeeded == 0) {
            return new Color(200, 255, 200, 50); // Light green with low opacity for safe zones
        } else {
            return new Color(255, 200, 200, 30); // Very light red tint for zones with fire
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Draw background grid
        drawGrid(g2d);

        // Draw zone background colors
        drawZoneBackgrounds(g2d);

        // Draw fire cells
        drawFireCells(g2d);

        // Draw zone borders
        drawZoneBorders(g2d);

        // Draw drones on top
        drawDrones(g2d);

        // Draw title and legend
        drawTitleAndLegend(g2d);
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(Color.GRAY);
        for (int x = 0; x <= GRID_CELLS * CELL_SIZE_PX; x += CELL_SIZE_PX) {
            g2d.drawLine(x, 0, x, GRID_CELLS * CELL_SIZE_PX);
        }
        for (int y = 0; y <= GRID_CELLS * CELL_SIZE_PX; y += CELL_SIZE_PX) {
            g2d.drawLine(0, y, GRID_CELLS * CELL_SIZE_PX, y);
        }
    }

    private void drawZoneBackgrounds(Graphics2D g2d) {
        for (Zone zone : zones) {
            int x1 = zone.x1 * CELL_SIZE_PX;
            int y1 = zone.y1 * CELL_SIZE_PX;
            int width = (zone.x2 - zone.x1 + 1) * CELL_SIZE_PX;
            int height = (zone.y2 - zone.y1 + 1) * CELL_SIZE_PX;

            // Fill zone with background color
            g2d.setColor(getZoneBackgroundColor(zone.id));
            g2d.fillRect(x1, y1, width, height);
        }
    }

    private void drawFireCells(Graphics2D g2d) {
        // Draw all fire cells
        for (Map.Entry<Point, Integer> entry : cellWaterNeeded.entrySet()) {
            Point cell = entry.getKey();
            int waterNeeded = entry.getValue();

            int x = cell.x * CELL_SIZE_PX;
            int y = cell.y * CELL_SIZE_PX;

            // Check if this cell is being extinguished
            if (extinguishingCells.contains(cell)) {
                // Being extinguished - yellow
                g2d.setColor(new Color(255, 255, 0, 200)); // Bright yellow
            } else {
                // On fire - color based on water needed
                if (waterNeeded >= 4) {
                    g2d.setColor(new Color(255, 0, 0, 220)); // Red for high
                } else if (waterNeeded >= 2) {
                    g2d.setColor(new Color(255, 100, 0, 220)); // Orange for medium
                } else {
                    g2d.setColor(new Color(255, 200, 0, 220)); // Yellow-orange for low
                }
            }

            // Fill the cell with a slight margin to show grid
            g2d.fillRect(x + 2, y + 2, CELL_SIZE_PX - 4, CELL_SIZE_PX - 4);

            // Draw flame icon
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString("🔥", x + 6, y + 18);

            // Draw water amount
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 8));
            g2d.drawString(waterNeeded + "L", x + 8, y + 28);
        }
    }

    private void drawZoneBorders(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));

        for (Zone zone : zones) {
            int x1 = zone.x1 * CELL_SIZE_PX;
            int y1 = zone.y1 * CELL_SIZE_PX;
            int width = (zone.x2 - zone.x1 + 1) * CELL_SIZE_PX;
            int height = (zone.y2 - zone.y1 + 1) * CELL_SIZE_PX;

            // Draw zone border
            g2d.drawRect(x1, y1, width, height);

            // Draw zone label
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.setColor(Color.BLACK);
            String label = "Zone " + zone.id;
            g2d.drawString(label, x1 + 5, y1 + 15);
        }
    }

    private void drawDrones(Graphics2D g2d) {
        if (drones != null && !drones.isEmpty()) {
            for (DroneSubsystem drone : drones.values()) {
                if (drone != null) {
                    int x = drone.getX() * CELL_SIZE_PX + CELL_SIZE_PX/2;
                    int y = drone.getY() * CELL_SIZE_PX + CELL_SIZE_PX/2;

                    // Draw drone shadow
                    g2d.setColor(new Color(0, 0, 0, 50));
                    g2d.fillOval(x - 8, y - 8, 20, 20);

                    // Color by state
                    DroneSubsystem.DroneState state = drone.getDroneState();
                    if (state == DroneSubsystem.DroneState.ONROUTE) {
                        g2d.setColor(Color.BLUE);
                    } else if (state == DroneSubsystem.DroneState.EXTINGUISHING) {
                        g2d.setColor(Color.GREEN);
                    } else if (state == DroneSubsystem.DroneState.REFILLING) {
                        g2d.setColor(Color.CYAN);
                    } else if (state == DroneSubsystem.DroneState.FAULTED) {
                        g2d.setColor(Color.MAGENTA);
                    } else {
                        g2d.setColor(Color.BLACK); // IDLE
                    }

                    // Draw drone circle
                    g2d.fillOval(x - 8, y - 8, 16, 16);

                    // Draw drone ID
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.BOLD, 10));
                    g2d.drawString(String.valueOf(drone.getDroneId()), x - 4, y + 4);
                }
            }
        }
    }

    private void drawTitleAndLegend(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("3km x 3km Area (30x30 Grid)", 10, GRID_CELLS * CELL_SIZE_PX + 20);
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.drawString("Each cell: 100m x 100m", 10, GRID_CELLS * CELL_SIZE_PX + 35);

        // Draw legend below the grid
        drawLegend(g2d);
    }

    private void drawLegend(Graphics2D g2d) {
        int legendX = 10;
        int legendY = GRID_CELLS * CELL_SIZE_PX + 50; // below the grid

        // Semi-transparent background
        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.fillRect(legendX, legendY, 200, 120);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(legendX, legendY, 200, 120);

        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("Drone States", legendX + 10, legendY + 20);

        g2d.setFont(new Font("Arial", Font.PLAIN, 10));

        // IDLE
        g2d.setColor(Color.BLACK);
        g2d.fillOval(legendX + 12, legendY + 30, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("IDLE", legendX + 30, legendY + 40);

        // EN ROUTE
        g2d.setColor(Color.BLUE);
        g2d.fillOval(legendX + 12, legendY + 50, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("EN ROUTE", legendX + 30, legendY + 60);

        // EXTINGUISHING
        g2d.setColor(Color.GREEN);
        g2d.fillOval(legendX + 12, legendY + 70, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("EXTINGUISHING", legendX + 30, legendY + 80);

        // REFILLING
        g2d.setColor(Color.CYAN);
        g2d.fillOval(legendX + 12, legendY + 90, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("REFILLING", legendX + 30, legendY + 100);

        // FAULTED
        g2d.setColor(Color.MAGENTA);
        g2d.fillOval(legendX + 12, legendY + 110, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("FAULTED", legendX + 30, legendY + 120);
    }

    // Zone class
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

    /**
     * Get zone by ID
     */
    private Zone getZoneById(int id) {
        for (Zone zone : zones) {
            if (zone.id == id) return zone;
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(GRID_CELLS * CELL_SIZE_PX + 40,
                GRID_CELLS * CELL_SIZE_PX + 180); // extra space for legend
    }
}