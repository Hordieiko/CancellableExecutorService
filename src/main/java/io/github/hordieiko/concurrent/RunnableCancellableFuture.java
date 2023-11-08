package io.github.hordieiko.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

/**
 * A {@link CancellableFuture} that is {@link Runnable}. Successful execution of
 * the {@code run} method causes completion of the {@code CancellableFuture}
 * and allows access to its results. The reasonable cancellation is available
 * by the {@link CancellableFuture#cancel(boolean, CancellableTask.CancellationReason)} method.
 *
 * @param <V> The result type returned by this Future's {@code get} method
 * @param <T> the type parameter
 * @see Executor
 * @see FutureTask
 * @see CancellableExecutorService
 * @see CancellableCompletionService
 * @see CancellableTimeoutCompletionService
 */
sealed interface RunnableCancellableFuture<V, T extends CancellableTask.CancellationReason>
        extends RunnableFuture<V>, CancellableFuture<V, T>
        permits CancellableFutureTask {
    /**
     * {@inheritDoc}
     */
    @Override
    void run();

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning {@code true} if the thread
     *                              executing this task should be interrupted (if the thread is
     *                              known to the implementation); otherwise, in-progress tasks are
     *                              allowed to complete
     * @param reason                the cancellation reason
     * @return
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning, T reason);
}
