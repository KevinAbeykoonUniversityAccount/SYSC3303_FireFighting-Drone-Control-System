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
    private List<ZoneRect> zones = new ArrayList<>();
    private Map<Integer, DroneInfo> drones = new HashMap<>();
    private Map<Integer, Integer> fireSeverityMap = new HashMap<>(); // zone -> total water needed

    // Grid properties: cells each representing 100m x 100m; expands with loaded zones
    private final int CELL_SIZE_PX = 25;
    private final int GRID_CELLS = 30; // default / minimum
    private int gridCols = GRID_CELLS;
    private int gridRows = GRID_CELLS;

    // Track fire cells: zone ID -> list of cell coordinates that are on fire
    private Map<Integer, List<Point>> fireCells = new ConcurrentHashMap<>();
    // Track water needed per cell
    private Map<Point, Integer> cellWaterNeeded = new HashMap<>();
    // Track cells being extinguished
    private Set<Point> extinguishingCells = new HashSet<>();

    public MapPanel() {
        setBackground(Color.LIGHT_GRAY);
        setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

        // Default 4 zones (x1=left col, y1=top row, x2=right col, y2=bottom row)
        zones.add(new ZoneRect(1, 0, 0, 14, 14));
        zones.add(new ZoneRect(2, 15, 0, 29, 14));
        zones.add(new ZoneRect(3, 0, 15, 14, 29));
        zones.add(new ZoneRect(4, 15, 15, 29, 29));
    }

    /**
     * Replace the displayed zones with those loaded from a file.
     * Accepts the top-level Zone type (xMin/xMax/yMin/yMax) and converts
     * to the inner rendering format. Also resizes the grid to fit all zones.
     */
    public void setZones(Map<Integer, Zone> schedulerZones) {
        zones.clear();
        fireCells.clear();
        cellWaterNeeded.clear();
        extinguishingCells.clear();
        fireSeverityMap.clear();

        int maxCol = GRID_CELLS - 1;
        int maxRow = GRID_CELLS - 1;
        for (Zone sz : schedulerZones.values()) {
            zones.add(new ZoneRect(sz.getId(), sz.getXMin(), sz.getYMin(), sz.getXMax(), sz.getYMax()));
            maxCol = Math.max(maxCol, sz.getXMax());
            maxRow = Math.max(maxRow, sz.getYMax());
        }
        gridCols = maxCol + 1;
        gridRows = maxRow + 1;
        invalidate();
        revalidate();
        repaint();
    }

    /**
     * Update drone positions and fire data from scheduler
     */
    public void updateDronesAndFires(Map<Integer, DroneInfo> droneMap,
                                     Map<Integer, Integer> zoneWater) {
        this.drones = droneMap;
        this.fireSeverityMap = zoneWater;
        updateFireCells(zoneWater);
        repaint();
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
                ZoneRect zone = getZoneById(zoneId);
                if (zone != null) {
                    int numFireCells = (int) Math.ceil(totalWater / 5.0);
                    List<Point> cells = generateCenterFireCells(zone, numFireCells);
                    fireCells.put(zoneId, cells);

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
    private List<Point> generateCenterFireCells(ZoneRect zone, int count) {
        List<Point> cells = new ArrayList<>();

        int centerX = (zone.x1 + zone.x2) / 2;
        int centerY = (zone.y1 + zone.y2) / 2;

        int[] offsets = {-1, 0, 1};

        for (int i = 0; i < count && i < 9; i++) {
            int x = centerX + offsets[i % 3];
            int y = centerY + offsets[i / 3];
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
            return new Color(200, 255, 200, 50);
        } else {
            return new Color(255, 200, 200, 30);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        drawGrid(g2d);
        drawZoneBackgrounds(g2d);
        drawFireCells(g2d);
        drawZoneBorders(g2d);
        drawDrones(g2d);
        drawTitleAndLegend(g2d);
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(Color.GRAY);
        for (int x = 0; x <= gridCols * CELL_SIZE_PX; x += CELL_SIZE_PX) {
            g2d.drawLine(x, 0, x, gridRows * CELL_SIZE_PX);
        }
        for (int y = 0; y <= gridRows * CELL_SIZE_PX; y += CELL_SIZE_PX) {
            g2d.drawLine(0, y, gridCols * CELL_SIZE_PX, y);
        }
    }

    private void drawZoneBackgrounds(Graphics2D g2d) {
        for (ZoneRect zone : zones) {
            int x1 = zone.x1 * CELL_SIZE_PX;
            int y1 = zone.y1 * CELL_SIZE_PX;
            int width = (zone.x2 - zone.x1 + 1) * CELL_SIZE_PX;
            int height = (zone.y2 - zone.y1 + 1) * CELL_SIZE_PX;

            g2d.setColor(getZoneBackgroundColor(zone.id));
            g2d.fillRect(x1, y1, width, height);
        }
    }

    private void drawFireCells(Graphics2D g2d) {
        for (Map.Entry<Point, Integer> entry : cellWaterNeeded.entrySet()) {
            Point cell = entry.getKey();
            int waterNeeded = entry.getValue();

            int x = cell.x * CELL_SIZE_PX;
            int y = cell.y * CELL_SIZE_PX;

            if (extinguishingCells.contains(cell)) {
                g2d.setColor(new Color(255, 255, 0, 200));
            } else {
                if (waterNeeded >= 4) {
                    g2d.setColor(new Color(255, 0, 0, 220));
                } else if (waterNeeded >= 2) {
                    g2d.setColor(new Color(255, 100, 0, 220));
                } else {
                    g2d.setColor(new Color(255, 200, 0, 220));
                }
            }

            g2d.fillRect(x + 2, y + 2, CELL_SIZE_PX - 4, CELL_SIZE_PX - 4);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString("🔥", x + 6, y + 18);

            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 8));
            g2d.drawString(waterNeeded + "L", x + 8, y + 28);
        }
    }

    private void drawZoneBorders(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));

        for (ZoneRect zone : zones) {
            int x1 = zone.x1 * CELL_SIZE_PX;
            int y1 = zone.y1 * CELL_SIZE_PX;
            int width = (zone.x2 - zone.x1 + 1) * CELL_SIZE_PX;
            int height = (zone.y2 - zone.y1 + 1) * CELL_SIZE_PX;

            g2d.drawRect(x1, y1, width, height);

            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.setColor(Color.BLACK);
            g2d.drawString("Zone " + zone.id, x1 + 5, y1 + 15);
        }
    }

    private void drawDrones(Graphics2D g2d) {
        if (drones != null && !drones.isEmpty()) {
            for (DroneInfo drone : drones.values()) {
                if (drone != null) {
                    int x = drone.x * CELL_SIZE_PX + CELL_SIZE_PX / 2;
                    int y = drone.y * CELL_SIZE_PX + CELL_SIZE_PX / 2;

                    g2d.setColor(new Color(0, 0, 0, 50));
                    g2d.fillOval(x - 8, y - 8, 20, 20);

                    Color droneColor;
                    switch (drone.state) {
                        case "ONROUTE":        droneColor = Color.BLUE;              break;
                        case "EXTINGUISHING":  droneColor = new Color(148, 0, 211);  break; // purple
                        case "REFILLING":      droneColor = Color.CYAN;  break;
                        case "FAULTED":        droneColor = new Color(255, 191, 0); break;
                        case "DECOMMISSIONED": droneColor = new Color(200, 50, 50); break;
                        default:               droneColor = Color.BLACK;
                    }

                    g2d.setColor(droneColor);
                    g2d.fillOval(x - 8, y - 8, 16, 16);

                    if ("DECOMMISSIONED".equals(drone.state)) {
                        g2d.setColor(Color.WHITE);
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawLine(x - 5, y - 5, x + 5, y + 5);
                        g2d.drawLine(x + 5, y - 5, x - 5, y + 5);
                        g2d.setStroke(new BasicStroke(1));
                    }

                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.BOLD, 10));
                    g2d.drawString(String.valueOf(drone.droneId), x - 4, y + 4);
                }
            }
        }
    }

    private void drawTitleAndLegend(Graphics2D g2d) {
        int gridH = gridRows * CELL_SIZE_PX;
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString(gridCols + "x" + gridRows + " Grid (" + (gridCols * 100) + "m x " + (gridRows * 100) + "m)", 10, gridH + 20);
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.drawString("Each cell: 100m x 100m", 10, gridH + 35);

        drawLegend(g2d, gridH);
    }

    private void drawLegend(Graphics2D g2d, int gridH) {
        int legendX = 10;
        int legendY = gridH + 50;

        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.fillRect(legendX, legendY, 200, 160);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(legendX, legendY, 200, 160);

        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.drawString("Drone States", legendX + 10, legendY + 20);

        g2d.setFont(new Font("Arial", Font.PLAIN, 10));

        g2d.setColor(Color.BLACK);
        g2d.fillOval(legendX + 12, legendY + 30, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("IDLE", legendX + 30, legendY + 40);

        g2d.setColor(Color.BLUE);
        g2d.fillOval(legendX + 12, legendY + 50, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("EN ROUTE", legendX + 30, legendY + 60);

        g2d.setColor(new Color(148, 0, 211));
        g2d.fillOval(legendX + 12, legendY + 70, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("EXTINGUISHING", legendX + 30, legendY + 80);

        g2d.setColor(Color.CYAN);
        g2d.fillOval(legendX + 12, legendY + 90, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("REFILLING", legendX + 30, legendY + 100);

        g2d.setColor(new Color(255, 191, 0));
        g2d.fillOval(legendX + 12, legendY + 110, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("SOFT FAULT", legendX + 30, legendY + 120);

        g2d.setColor(new Color(200, 50, 50));
        g2d.fillOval(legendX + 12, legendY + 130, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("HARD FAULT (offline)", legendX + 30, legendY + 140);
    }

    // Inner rendering rectangle for a zone
    class ZoneRect {
        int id;
        int x1, y1, x2, y2; // Grid coordinates (col/row, 0-indexed)

        ZoneRect(int id, int x1, int y1, int x2, int y2) {
            this.id = id;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private ZoneRect getZoneById(int id) {
        for (ZoneRect zone : zones) {
            if (zone.id == id) return zone;
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(gridCols * CELL_SIZE_PX + 40,
                gridRows * CELL_SIZE_PX + 180);
    }
}
