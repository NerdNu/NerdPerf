package nu.nerd.stats;

import java.util.LinkedList;

// ----------------------------------------------------------------------------
/**
 * A synchronous task that measures the TPS.
 *
 * This task runs every 20 ticks and measures the elapsed time since the last
 * run, which it stores in a queue that it uses to compute a rolling average TPS
 * over the last 10 task invocations.
 */
public class TPSTask implements Runnable {
    // ------------------------------------------------------------------------
    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        long now = System.currentTimeMillis();
        float elapsedSeconds = 0.001f * (now - _lastTime);
        _elapsedTimes.add(elapsedSeconds);
        if (_elapsedTimes.size() > NUM_SAMPLES) {
            _elapsedTimes.poll();
        }
        _lastTime = now;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the average TPS for the sample period.
     *
     * @return the average TPS.
     */
    public float getTPS() {
        float totalElapsed = 0;
        for (Float elapsed : _elapsedTimes) {
            totalElapsed += elapsed;
        }
        return Math.min(20.0f, 20 * _elapsedTimes.size() / totalElapsed);
    }

    // ------------------------------------------------------------------------
    /**
     * Maximum number of elapsed time samples to retain and average into the TPS
     * value.
     */
    protected static int NUM_SAMPLES = 10;

    /**
     * Instant when this task was last run.
     */
    protected long _lastTime = System.currentTimeMillis();

    /**
     * Measured elapsed times between task runs in seconds.
     */
    protected LinkedList<Float> _elapsedTimes = new LinkedList<Float>();
} // class TPSTask