/**
 * The Zone class represents a zone with flexible bounds.
 * This supports flexible zone boundaries for differing maps.
 *
 * @author Kevin Abeykoon (101301971)
 */

public class Zone {

    private int id;
    private int xMin;
    private int xMax;
    private int yMin;
    private int yMax;

    /**
     * Constructor to create a zone
     *
     * @param id
     * @param xMin
     * @param xMax
     * @param yMin
     * @param yMax
     */
    public Zone(int id, int xMin, int xMax, int yMin, int yMax) {
        this.id = id;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    /**
     * Getter method for the zone id
     * @return zone id
     */
    public int getId() {
        return id;
    }

    /**
     * Getter method for the zone center x coordinate
     * @return x coordinate
     */
    public int getCenterX() {
        return (xMin + xMax) / 2;
    }

    /**
     * Getter method for the zone center y coordinate
     * @return y coordinate
     */
    public int getCenterY() {
        return (yMin + yMax) / 2;
    }

    public int getXMin() { return xMin; }
    public int getXMax() { return xMax; }
    public int getYMin() { return yMin; }
    public int getYMax() { return yMax; }
}