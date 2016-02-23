package bitverify.network;

import com.squareup.otto.Bus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class ProtocolFuture<T> implements RunnableFuture<T> {
    protected T result;
    protected final CountDownLatch resultLatch = new CountDownLatch(1);
    protected final Bus bus;

    public ProtocolFuture(Bus bus) {
        this.bus = bus;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return resultLatch.getCount() == 0;
    }

    @Override
    public T get() throws InterruptedException {
        resultLatch.await();
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (resultLatch.await(timeout, unit))
            return result;
        else {
            bus.unregister(this);
            throw new TimeoutException();
        }
    }
}
