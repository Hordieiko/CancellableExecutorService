package io.github.hordieiko.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * The {@link CancellableFutureTask} is a {@link FutureTask} extension
 * to support task cancellation with a possible {@link CancellableTask.CancellationReason reason}.
 *
 * @param <T> the cancellation reason type
 * @param <V> the result type returned by this Future's {@code get} methods
 * @param <C> the callable cancellable task
 * @param <R> the runnable cancellable task
 */
non-sealed class CancellableFutureTask<V,
        T extends CancellableTask.CancellationReason,
        C extends Callable<V> & CancellableTask<T>,
        R extends Runnable & CancellableTask<T>>
        extends FutureTask<V>
        implements RunnableCancellableFuture<V, T> {
    private final CancellableTask<T> cancellableTask;

    CancellableFutureTask(final C cancellableCallable) {
        super(cancellableCallable);
        this.cancellableTask = cancellableCallable;
    }

    CancellableFutureTask(final R cancellableRunnable, V value) {
        super(cancellableRunnable, value);
        this.cancellableTask = cancellableRunnable;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        cancelTask(null);
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning, final T reason) {
        cancelTask(reason);
        return super.cancel(mayInterruptIfRunning);
    }

    private void cancelTask(final T reason) {
        try {
            cancellableTask.cancel(reason);
        } catch (Exception ignore) {
            // An additional cancellation action mustn't affect the main cancellation.
        }
    }
}
