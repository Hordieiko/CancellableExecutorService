package io.github.hordieiko.concurrent;

/**
 * A {@link Runnable} that is {@link CancellableTask<U>}
 *
 * @param <U> the {@link CancellableTask} cancellation reason
 * @see CancellableExecutor
 * @see CancellableExecutorService
 */
public interface RunnableCancellableTask<U extends CancellableTask.CancellationReason>
        extends Runnable, CancellableTask<U> {
    /**
     * {@inheritDoc}
     */
    @Override
    void run();

    /**
     * {@inheritDoc}
     *
     * @param reason the cancellation reason
     */
    @Override
    void cancel(U reason);
}
