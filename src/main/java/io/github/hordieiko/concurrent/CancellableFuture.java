package io.github.hordieiko.concurrent;

import java.util.concurrent.Future;

/**
 * A {@link Future} that is cancellable with a {@link CancellableTask.CancellationReason reason}
 * for {@link CancellableTask}.
 * <p>
 * Cancelling a {@link CancellableTask task} with a known {@link CancellableTask.CancellationReason reason}
 * allows for its proper completion.
 *
 * @param <V> the result type returned by this CancellableFuture's {@code get} method
 * @param <U> the cancellation reason type
 * @see CancellableTask
 * @see CancellableExecutor
 * @see CancellableExecutorService
 */
public interface CancellableFuture<V, U extends CancellableTask.CancellationReason> extends Future<V> {

    /**
     * Attempts to cancel execution of this task.
     * <p>
     * If the execution task is a {@link CancellableTask} transfers the {@code reason} to it.
     *
     * @param mayInterruptIfRunning {@code true} if the thread
     *                              executing this task should be interrupted (if the thread is
     *                              known to the implementation); otherwise, in-progress tasks are
     *                              allowed to complete
     * @param reason                the cancellation reason
     * @return {@code false} if the task could not be cancelled,
     * typically because it has already completed; {@code true}
     * otherwise. If two or more threads cause a task to be cancelled,
     * then at least one of them returns {@code true}. Implementations
     * may provide stronger guarantees.
     */
    boolean cancel(boolean mayInterruptIfRunning, U reason);
}
