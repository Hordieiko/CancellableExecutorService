package io.github.hordieiko.concurrent;

import io.github.hordieiko.observer.EventManager;
import io.github.hordieiko.observer.Observable;
import io.github.hordieiko.observer.ObserverManager;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * A {@link CancellableTimeoutCompletionService} uses a supplied {@link Executor}
 * to execute {@link CancellableTask cancellable tasks}.
 *
 * <p> The service submits a {@link CancellableTask cancellable task} for execution
 * and returns a {@link CancellableFuture cancellable future} representing that task.
 * A {@link CancellableFuture cancellable future} can be cancelled with a reason
 * by the {@link CancellableFuture#cancel(boolean, CancellableTask.CancellationReason)} method.
 * The {@link CancellableFuture}'s cancellation affects the {@link CancellableTask} it is built on.
 *
 * <p>It's possible to subscribe to notifications before and after
 * a submitted task is executed to take specific actions with it.
 *
 * @param <U> the cancellation reason type
 */
public class CancellableExecutorCompletionService<U extends CancellableTask.CancellationReason>
        implements CancellableCompletionService<U>, Observable<RunnableTaskListener.EventType, RunnableTaskListener> {
    /**
     * The executor to execute a cancellable task with timeout.
     */
    private final Executor executor;
    /**
     * The {@code EventManager} manages events to notify subscribed listeners about changes.
     */
    private final ObserverManager<RunnableTaskListener.EventType, Runnable, RunnableTaskListener> eventManager;

    /**
     * Instantiates a new {@code CancellableTimeoutExecutorCompletionService} service.
     *
     * @param executor the executor
     * @throws NullPointerException if the {@code executor} is null
     */
    public CancellableExecutorCompletionService(Executor executor) {
        if (executor == null) throw new NullPointerException();
        this.executor = executor;
        this.eventManager = new EventManager<>();
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @param <V>  the type of the task's result
     * @param <C>  the type or the submitted callable cancellable task
     * @return {@inheritDoc}
     */
    @Override
    public <V, C extends Callable<V> & CancellableTask<U>> CancellableFuture<V, U> submit(final C task) {
        final var runnableCancellableFuture = newTaskFor(task);
        executor.execute(runnableCancellableFuture);
        return runnableCancellableFuture;
    }

    /**
     * {@inheritDoc}
     *
     * @param task   the task to submit
     * @param result the result to return
     * @param <V>    the type of the result
     * @param <R>    the type or the submitted runnable cancellable task
     * @return {@inheritDoc}
     */
    @Override
    public <V, R extends Runnable & CancellableTask<U>> CancellableFuture<V, U> submit(final R task, final V result) {
        final var runnableCancellableFuture = newTaskFor(task, result);
        executor.execute(runnableCancellableFuture);
        return runnableCancellableFuture;
    }

    /**
     * Creates {@link CancellableTimeoutExecutorCompletionService.TimeoutCancellableTask} based on submitted task.
     *
     * @param task the task to submit
     * @param <V>  the type of the task's result
     * @param <C>  the type or the submitted callable cancellable task
     * @return a {@link  ObservableCancellableFutureTask} which, when run, will run the
     * underlying runnable and which, as a {@link CancellableFuture}, will yield
     * the given value as its result and provide for reasonable cancellation of
     * the underlying task
     */
    private <V, C extends Callable<V> & CancellableTask<U>>
    RunnableCancellableFuture<V, U> newTaskFor(final C task) {
        return new ObservableCancellableFutureTask<>(task, eventManager);
    }

    /**
     * Creates {@link CancellableTimeoutExecutorCompletionService.TimeoutCancellableTask} based on submitted task.
     *
     * @param task   the task to submit
     * @param result the result to return
     * @param <V>    the type of the task's result
     * @param <R>    the type or the submitted runnable cancellable task
     * @return a {@link  ObservableCancellableFutureTask} which, when run, will run the
     * underlying runnable and which, as a {@link CancellableFuture}, will yield
     * the given value as its result and provide for reasonable cancellation of
     * the underlying task
     */
    private <V, R extends Runnable & CancellableTask<U>>
    RunnableCancellableFuture<V, U> newTaskFor(final R task, final V result) {
        return new ObservableCancellableFutureTask<>(task, result, eventManager);
    }

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    public void subscribe(final RunnableTaskListener.EventType event, final RunnableTaskListener listener) {
        eventManager.subscribe(event, listener);
    }

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    public void unsubscribe(final RunnableTaskListener.EventType event, final RunnableTaskListener listener) {
        eventManager.unsubscribe(event, listener);
    }
}
