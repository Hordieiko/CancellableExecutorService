package io.github.hordieiko.concurrent;

import java.util.concurrent.FutureTask;

/**
 * The {@link CancellableFutureTask} is a {@link FutureTask} extension
 * to support task cancellation with a possible {@link CancellableTask.CancellationReason reason}.
 *
 * @param <V> the result type returned by this Future's {@code get} methods
 */
sealed class CancellableFutureTask<V, U extends CancellableTask.CancellationReason> extends FutureTask<V>
        implements RunnableCancellableFuture<V, U>
        permits ObservableCancellableFutureTask {
    private final CancellableTask<U> cancellableTask;

    /**
     * Instantiates a new Cancellable future task.
     *
     * @param cancellableCallable the cancellable callable
     */
    CancellableFutureTask(final CallableCancellableTask<V, U> cancellableCallable) {
        super(cancellableCallable);
        this.cancellableTask = cancellableCallable;
    }

    /**
     * Instantiates a new Cancellable future task.
     *
     * @param cancellableRunnable the cancellable runnable
     * @param value               the value
     */
    CancellableFutureTask(final RunnableCancellableTask<U> cancellableRunnable, V value) {
        super(cancellableRunnable, value);
        this.cancellableTask = cancellableRunnable;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        cancelTask(null);
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning, final U reason) {
        cancelTask(reason);
        return super.cancel(mayInterruptIfRunning);
    }

    private void cancelTask(final U reason) {
        try {
            cancellableTask.cancel(reason);
        } catch (Exception ignore) {
            // An additional cancellation action mustn't affect the main cancellation.
        }
    }
}