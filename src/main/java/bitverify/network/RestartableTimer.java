package bitverify.network;

import java.util.concurrent.*;

/**
 * A timer that executes a given action after some delay, and can be started and stopped.
 * Thread-safe: all methods are synchronised.
 */
public class RestartableTimer {
    private final ScheduledThreadPoolExecutor executor;
    private final long delay;
    private final TimeUnit unit;
    private final Runnable action;
    private Future<?> future;

    public RestartableTimer(Runnable action, long delay, TimeUnit unit) {
        this.action = action;
        this.delay = delay;
        this.unit = unit;

        executor = new ScheduledThreadPoolExecutor(1);
        // we want the timer task to be removed from the pool as soon as the timer is cancelled
        executor.setRemoveOnCancelPolicy(true);
    }

    /**
     * Start the timer. Does nothing if the timer is already running.
     */
    public synchronized void start() {
        if (future == null)
            future = executor.schedule(action, delay, unit);
    }

    /**
     * Stop the timer.
     */
    public synchronized void stop() {
        if (future != null) {
            future.cancel(true);
            future = null;
        }
    }

    /**
     * Check if the timer is running.
     */
    public synchronized boolean isRunning() {
        return future != null;
    }

}
