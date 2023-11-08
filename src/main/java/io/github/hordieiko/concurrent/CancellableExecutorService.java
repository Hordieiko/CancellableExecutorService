package io.github.hordieiko.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The CancellableExecutorService consumes {@link CancellableTask cancellable task}s
 * and creates {@link CancellableFuture cancellable future}s that can be cancelled
 * with a specified {@link io.github.hordieiko.concurrent.CancellableTask.CancellationReason cancellation reason}.
 *
 * @param <U> the cancellation reason type
 */
public interface CancellableExecutorService<U extends CancellableTask.CancellationReason> {

    /**
     * Submits a value-returning task for execution and returns a
     * CancellableFuture representing the pending results of the task. The
     * Future's {@code get} method will return the task's result upon
     * successful completion.
     *
     * <p>
     * If you would like to immediately block waiting
     * for a task, you can use constructions of the form
     * {@code result = exec.submit(aCallable).get();}
     *
     * <p>Note: The {@link Executors} class includes a set of methods
     * that can convert some other common closure-like objects,
     * for example, {@link java.security.PrivilegedAction} to
     * {@link Callable} form so they can be submitted.
     *
     * @param <V>  the type of the task's result
     * @param <C>  the type or the submitted callable cancellable task
     * @param task the task to submit
     * @return a CancellableFuture representing pending completion of the task
     * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
     * @throws NullPointerException       if the {@code task} is null
     */
    <V, C extends Callable<V> & CancellableTask<U>>
    CancellableFuture<V, U> submitCancellable(C task);

    /**
     * Submits a Runnable task for execution and returns a CancellableFuture
     * representing that task. The Future's {@code get} method will
     * return the given result upon successful completion.
     *
     * @param <V>    the type of the result
     * @param <R>    the type or the submitted runnable cancellable task
     * @param task   the task to submit
     * @param result the result to return
     * @return a CancellableFuture representing pending completion of the task
     * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
     * @throws NullPointerException       if the {@code task} is null
     */
    <V, R extends Runnable & CancellableTask<U>>
    CancellableFuture<V, U> submitCancellable(R task, V result);

    /**
     * Submits a Runnable task for execution and returns a CancellableFuture
     * representing that task. The Future's {@code get} method will
     * return {@code null} upon <em>successful</em> completion.
     *
     * @param <R>  the type or the submitted runnable cancellable task
     * @param task the task to submit
     * @return a CancellableFuture representing pending completion of the task
     * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
     * @throws NullPointerException       if the {@code task} is null
     */
    <R extends Runnable & CancellableTask<U>>
    CancellableFuture<?, U> submitCancellable(R task);
}
