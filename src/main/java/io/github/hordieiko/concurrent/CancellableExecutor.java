package io.github.hordieiko.concurrent;

import java.util.concurrent.RejectedExecutionException;

/**
 * The {@link CancellableExecutor} consumes {@link CancellableTask cancellable task}s
 * and creates {@link CancellableFuture cancellable future}s that can be cancelled
 * with a specified {@link CancellableTask.CancellationReason cancellation reason}.
 *
 * @param <U> the cancellation reason type
 */
public interface CancellableExecutor<U extends CancellableTask.CancellationReason> {
    /**
     * Submits a value-returning {@link CallableCancellableTask task} for execution
     * and returns a {@link CancellableFuture future} representing the pending
     * results of the task. The CancellableFuture's {@code get} method will
     * return the task's result upon successful completion.
     *
     * <p>
     * If you would like to immediately block waiting
     * for a task, you can use constructions of the form
     * {@code result = exec.submit(aCallable).get();}
     *
     * @param <V>  the type of the task's result
     * @param task the task to submit
     * @return a CancellableFuture representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be scheduled for execution
     * @throws NullPointerException       if the task is null
     */
    <V> CancellableFuture<V, U> submit(CallableCancellableTask<V, U> task);

    /**
     * Submits a {@link RunnableCancellableTask task} for execution
     * and returns a {@link CancellableFuture future} representing
     * that task. The CancellableFuture's {@code get} method will
     * return the given result upon successful completion.
     *
     * @param <V>    the type of the result
     * @param task   the task to submit
     * @param result the result to return
     * @return a CancellableFuture representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be scheduled for execution
     * @throws NullPointerException       if the task is null
     */
    <V> CancellableFuture<V, U> submit(RunnableCancellableTask<U> task, V result);

    /**
     * Submits a {@link RunnableCancellableTask task} for execution
     * and returns a {@link CancellableFuture future} representing
     * that task. The CancellableFuture's {@code get} method will
     * return {@code null} upon <em>successful</em> completion.
     *
     * @param task the task to submit
     * @return a CancellableFuture representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if the task is null
     */
    CancellableFuture<Void, U> submit(RunnableCancellableTask<U> task);
}
