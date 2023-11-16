package io.github.hordieiko.concurrent;

/**
 * Represents a task that can be cancelled.
 * <p>
 * Designed to use as a submitted task for {@link CancellableThreadPoolExecutorService}.
 *
 * @param <U> the task cancellation reason type
 * @see CancellableExecutor
 * @see CancellableExecutorService
 */
@FunctionalInterface
public interface CancellableTask<U extends CancellableTask.CancellationReason> {

    /**
     * When an object implementing interface {@code CancellableTask} is used
     * to create a task for {@code CancellableThreadPoolExecutor}, canceling
     * the executor's task causes the object's {@code cancel(CancellationReason)} method to be
     * called before canceling.
     *
     * @param reason the task cancellation reason
     * @see CancellableFutureTask#cancel(boolean)
     * @see CancellableFutureTask#cancel(boolean, CancellationReason)
     */
    void cancel(U reason);

    /**
     * The State of the {@link CancellableTask}
     *
     * @param <U> the task cancellation reason type
     */
    interface State<U extends CancellableTask.CancellationReason> {
        /**
         * Returns {@code true} if this task was cancelled before it completed
         * normally.
         *
         * @return {@code true} if this task was cancelled before it completed
         */
        boolean isCancelled();


        /**
         * Gets cancellation reason.
         *
         * @return the cancellation reason
         */
        U getCancellationReason();
    }

    /**
     * The {@link CancellableTask task}'s cancellation reason.
     */
    interface CancellationReason {
    }
}
