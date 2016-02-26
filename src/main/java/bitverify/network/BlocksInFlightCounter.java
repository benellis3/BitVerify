package bitverify.network;

import java.util.concurrent.Callable;

/**
 * Created by Rob on 26/02/2016.
 */
public class BlocksInFlightCounter {
    private int blocksInFlight = 0;

    /**
     * Wait for there to be no more blocks in flight, then execute some action
     * @param c the function to call
     */
    public synchronized <T> T onceZero(Callable<T> c) throws Exception {
        while (blocksInFlight > 0)
            this.wait();
        return c.call();
    }

    /**
     * Wait for there to be no more blocks in flight, then execute some action
     * @param r the action to execute
     */
    public synchronized void onceZero(Runnable r) throws InterruptedException {
        while (blocksInFlight > 0)
            this.wait();
        r.run();
    }

    /**
     * Increase the number of blocks in flight
     */
    public synchronized void increase(int by) {
        blocksInFlight += by;
    }

    public synchronized void increment() {
        blocksInFlight++;
    }

    public synchronized void decrement() {
        switch (blocksInFlight) {
            case 0:
                break;
            case 1:
                blocksInFlight--;
                this.notifyAll();
                break;
            default:
                blocksInFlight--;
                break;
        }
    }
}
