package nu.nerd.perf;

import java.util.Queue;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;

// ----------------------------------------------------------------------------
/**
 * A synchronous Bukkit task that imposes a strict limit on its run time in any
 * one tick and automatically re-schedules itself to run in the next tick until
 * the work is done.
 *
 * The time limit is taken from {@link NerdPerf#CONFIG}.
 */
public class SynchronousTimeLimitedTask implements Runnable {
    // ------------------------------------------------------------------------
    /**
     * Construct a SynchronousTimeLimitedTask from a list of steps to be
     * performed.
     *
     * @param steps the list of steps, in the order they should be performed.
     *        Each step should return true if there is more work to be done.
     *
     */
    public SynchronousTimeLimitedTask(Queue<BooleanSupplier> steps) {
        _steps = steps;
    }

    // ------------------------------------------------------------------------
    /**
     * @see java.lang.Runnable#run()
     *
     *      Call process() until there is no more work to be done or the time
     *      limit is reached. Reschedule this task to run in the next tick if
     *      processing was paused due to the time limit.
     */
    @Override
    public void run() {
        long start = System.nanoTime();
        long elapsed;
        boolean more;
        do {
            more = process();
            elapsed = System.nanoTime() - start;
        } while (more && elapsed < NerdPerf.CONFIG.TASK_TIME_LIMIT_MILLIS * 1_000_000);

        if (more) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(NerdPerf.PLUGIN, this);
        }

        if (NerdPerf.CONFIG.DEBUG_OVERHEAD) {
            double elapsedMillis = elapsed * 1e-6;
            NerdPerf.PLUGIN.getLogger().info("Measurement task took " + elapsedMillis + " ms");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Run the next step and return true if it has more work to do or if there
     * are subsequent steps still to be done.
     *
     * @return true if there is more work to be done.
     */
    protected boolean process() {
        BooleanSupplier step = _steps.peek();
        if (step == null) {
            return false;
        }
        if (step.getAsBoolean()) {
            return true;
        } else {
            _steps.remove();
            return !_steps.isEmpty();
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Steps, in sequence.
     */
    protected Queue<BooleanSupplier> _steps;
} // class SynchronousTimeLimitedTask