package com.ouyunc.base.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-blocking admission control: overload never executes business work on the submitting I/O thread. */
public final class BoundedTaskExecutor extends AbstractExecutorService {
    private final ExecutorService delegate;
    private final Semaphore permits;
    private final int capacity;

    public BoundedTaskExecutor(ExecutorService delegate, int capacity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.permits = new Semaphore(capacity);
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        if (isShutdown() || !permits.tryAcquire()) {
            throw new RejectedExecutionException("Executor admission capacity exhausted or executor shut down");
        }
        PermitTask task = new PermitTask(command);
        try {
            delegate.execute(task);
        } catch (RuntimeException | Error failure) {
            task.release();
            throw failure;
        }
    }

    public int inFlightTasks() {
        return capacity - permits.availablePermits();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> pending = new ArrayList<>();
        for (Runnable runnable : delegate.shutdownNow()) {
            if (runnable instanceof PermitTask task) {
                task.release();
                pending.add(task.command);
            } else {
                pending.add(runnable);
            }
        }
        return pending;
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private final class PermitTask implements Runnable {
        private final Runnable command;
        private final AtomicBoolean released = new AtomicBoolean();

        private PermitTask(Runnable command) {
            this.command = command;
        }

        @Override
        public void run() {
            try {
                command.run();
            } finally {
                release();
            }
        }

        private void release() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}

