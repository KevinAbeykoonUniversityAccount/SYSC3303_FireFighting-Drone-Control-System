import java.net.InetAddress;

/**
 * Lightweight record the Scheduler uses to track each drone.
 * Replaces the direct DroneSubsystem reference from Iteration 2
 * since drones now run in a separate process.
 *
 * @author Abdullah Khan (101305235)
 * @author Aryan Kumar Singh (101299776)
 * @author Kevin Abeykoon (101301971)
 */
public class DroneInfo {
    public int         droneId;
    public int         x, y;
    public int         waterRemaining;
    public String      state = "IDLE";
    public InetAddress address;
    public int         port;
    public int         batteryLevel; // from 0 == empty to 100 == full

    public DroneInfo(int droneId, int x, int y, int water,
                     InetAddress address, int port, int batteryLevel) {
        this.droneId        = droneId;
        this.x              = x;
        this.y              = y;
        this.waterRemaining = water;
        this.address        = address;
        this.port           = port;
        this.batteryLevel   = batteryLevel;
    }
}