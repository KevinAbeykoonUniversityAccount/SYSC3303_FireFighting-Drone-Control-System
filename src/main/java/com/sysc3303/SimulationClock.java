package com.sysc3303;

public class SimulationClock implements Runnable {
    private static SimulationClock instance;
    private long simulationStartTimeMillis; // Real-time when simulation started
    private long simulationTimeSeconds; // Current simulated time in seconds
    private int clockSpeedMultiplier; // How many simulation seconds pass per real second
    private boolean running;

    private SimulationClock() {
        // Private constructor for singleton
        simulationStartTimeMillis = System.currentTimeMillis();
        simulationTimeSeconds = 0;
        clockSpeedMultiplier = 1; // Default: 1 simulation second per real second
        running = false;
    }

    /**
     * Return the instance of the simulation clock or create one if it doesn't exist yet
     *
     * @return The instance of the clock
     */
    public static synchronized SimulationClock getInstance() {
        // Applying the singleton design pattern to ensure one centralized clock exists
        if (instance == null) {
            instance = new SimulationClock();
        }
        return instance;
    }

    /**
     * Stops the simulation clock
     */
    public synchronized void stop() {
        running = false;
    }

    @Override
    public void run() {
        running = true;
        long lastUpdate = System.currentTimeMillis();

        while (running) {
            try {
                Thread.sleep(100); // Update every 100ms for smoothness

                long now = System.currentTimeMillis();
                long elapsedRealMillis = now - lastUpdate;

                if (elapsedRealMillis >= 100) { // Update at least every 100ms
                    // Calculate how many simulation seconds have passed
                    double elapsedRealSeconds = elapsedRealMillis / 1000.0;
                    long elapsedSimSeconds = (long)(elapsedRealSeconds * clockSpeedMultiplier);

                    if (elapsedSimSeconds > 0) {
                        simulationTimeSeconds += elapsedSimSeconds;
                        lastUpdate = now;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Makes the current thread sleep for the specified number of simulation seconds
     *
     * @param simulationSeconds Number of simulation seconds to sleep
     */
    public void sleepForSimulationSeconds(long simulationSeconds) throws InterruptedException {
        if (clockSpeedMultiplier <= 0) return;

        long targetTime = simulationTimeSeconds + simulationSeconds;
        sleepUntilSimulationTime(targetTime);
    }

    /**
     * Makes the current thread sleep until the specified simulation time is reached
     *
     * @param targetSimulationTime Target simulation time in seconds
     */
    public void sleepUntilSimulationTime(long targetSimulationTime) throws InterruptedException {
        if (targetSimulationTime <= simulationTimeSeconds) {
            return; // Already passed the target time
        }

        // Calculate how many real milliseconds to wait
        long simulationSecondsToWait = targetSimulationTime - simulationTimeSeconds;
        double realSecondsToSleep = (double) simulationSecondsToWait / clockSpeedMultiplier;
        long realMillisToSleep = (long)(realSecondsToSleep * 1000);

        if (realMillisToSleep > 0) {
            Thread.sleep(realMillisToSleep);
        }
    }

    /**
     * Gets current simulation time in seconds
     */
    public synchronized long getSimulationTimeSeconds() {
        return simulationTimeSeconds;
    }

    /**
     * Get the formatted simulation time as HH:MM:SS
     */
    public synchronized String getFormattedTime() {
        return formatTime(simulationTimeSeconds);
    }

    /**
     * Formats the duration into a Hour:Minute:Secondsn format
     *
     * @param seconds Duration in seconds
     * @return  duration in HH:MM:SS string format
     */
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Set the clock to start at a specific time
     */
    public synchronized void setSimulationStartTime(int hours, int minutes, int seconds) {
        simulationTimeSeconds = hours * 3600 + minutes * 60 + seconds;
    }

    /**
     * Set how many simulation seconds pass per real second
     * Example: clockSpeedMultiplier = 60 means 1 real second = 1 simulated minute
     */
    public synchronized void setClockSpeedMultiplier(int clockSpeedMultiplier) {
        this.clockSpeedMultiplier = Math.max(1, clockSpeedMultiplier);
    }

    /**
     * Fast forward the simulation by X seconds (immediately advances simulation time)
     */
    public synchronized void fastForward(long seconds) {
        simulationTimeSeconds += seconds;
    }

    /**
     * Reset the clock
     */
    public synchronized void reset() {
        simulationTimeSeconds = 0;
        clockSpeedMultiplier = 1;
        simulationStartTimeMillis = System.currentTimeMillis();
    }

    /**
     * Check if current simulation time has reached/passed a target time
     */
    public synchronized boolean hasReachedTime(long targetSeconds) {
        return simulationTimeSeconds >= targetSeconds;
    }

    /**
     * Get the clock speed multiplier
     */
    public synchronized int getClockSpeedMultiplier() {
        return clockSpeedMultiplier;
    }

    /**
     * Check if the clock is currently running
     */
    public synchronized boolean isRunning() {
        return running;
    }
}