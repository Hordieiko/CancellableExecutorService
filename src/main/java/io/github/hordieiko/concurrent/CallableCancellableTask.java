package io.github.hordieiko.concurrent;

import java.util.concurrent.Callable;

/**
 * A {@link Callable<V>} that is {@link CancellableTask<U>}
 *
 * @param <V> the result type of method {@link Callable#call()}
 * @param <U> the {@link CancellableTask} cancellation reason
 * @see CancellableExecutor
 * @see CancellableExecutorService
 */
public interface CallableCancellableTask<V, U extends CancellableTask.CancellationReason>
        extends Callable<V>, CancellableTask<U> {
    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws Exception {@inheritDoc}
     */
    @Override
    V call() throws Exception;

    /**
     * {@inheritDoc}
     *
     * @param reason the cancellation reason
     */
    @Override
    void cancel(U reason);
}
