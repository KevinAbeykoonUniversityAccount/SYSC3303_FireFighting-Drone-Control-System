public class SimulationClock {
    private static SimulationClock instance;
    private long simulationStartTime; // Real-time when simulation started
    private long simulationOffset; // Offset for setting timer's start to a non-zero time
    private double clockSpeedMultiplier;

    private SimulationClock() {
        // Private constructor for singleton
        simulationStartTime = System.currentTimeMillis();
        simulationOffset = 0;
        clockSpeedMultiplier = 1.0;
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
        long realTimeElapsed = System.currentTimeMillis() - simulationStartTime;
        long simulatedTimeElapsed = (long) (realTimeElapsed * clockSpeedMultiplier);
        return (simulatedTimeElapsed / 1000) + simulationOffset;
    }

    /**
     * Converts a desired simulation duration (ms) into real duration (ms).
     * If speed is 10x, and you want to sleep 2000 sim-ms, this returns 200 real-ms.
     * @param simulatedTimeElapsed The time to be converted to sim-ms
     * @return The converted time based on the clock speed multiplier
     */
    public long scaleSimulatedToReal(long simulatedTimeElapsed) {
        if (clockSpeedMultiplier <= 0) return simulatedTimeElapsed;
        return (long) (simulatedTimeElapsed / clockSpeedMultiplier);
    }

    /**
     * Set a starting time for simulation (e.g., start at 14:00:00)
     */
    public void setSimulationStartTime(int hours, int minutes, int seconds) {
        simulationOffset = hours * 3600L + minutes * 60L + seconds; //Cast to long
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