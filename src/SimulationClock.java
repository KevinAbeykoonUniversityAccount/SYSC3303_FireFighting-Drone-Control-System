public class SimulationClock {
    private static SimulationClock instance;
    private long simulationStartTime; // Real-time when simulation started
    private long simulationOffset; // Offset for setting timer's start to a non-zero time
    private long clockSpeedMultiplier;

    private SimulationClock() {
        // Private constructor for singleton
        simulationStartTime = System.currentTimeMillis();
        simulationOffset = 0;
        clockSpeedMultiplier = 1;  // NOT HOW TO DO IT
    }


    public static synchronized SimulationClock getInstance() {
        // Apply singleton pattern to have only one centralized clock
        if (instance == null) {
            instance = new SimulationClock();
        }
        return instance;
    }

    /**
     * Gets current simulation time in seconds since simulation start
     */
    public long getSimulationTimeSeconds() {
        long realTimeElapsed = (System.currentTimeMillis() * clockSpeedMultiplier) - simulationStartTime;
        return (realTimeElapsed / 1000) + simulationOffset;
    }

    /**
     * Set a starting time for simulation (e.g., start at 14:00:00)
     */
    public void setSimulationStartTime(int hours, int minutes, int seconds) {
        simulationOffset = hours * 3600 + minutes * 60 + seconds;
    }

    /**
     * Reset the clock (useful for restarting simulation)
     */
    public void reset() {
        simulationStartTime = System.currentTimeMillis();
        simulationOffset = 0;
    }


    /**
     * Set the speed for how long a simulated second would be in real seconds.
     *
     * @param clockSpeedMultiplier is the simulation speed the clock operates
     */
    public void setClockSpeedMultiplier(long clockSpeedMultiplier){
        this.clockSpeedMultiplier = clockSpeedMultiplier;
    }
}