package io.github.hordieiko.concurrent;

import io.github.hordieiko.observer.EventManager;
import io.github.hordieiko.observer.Observable;
import io.github.hordieiko.observer.ObserverManager;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A {@link CancellableTimeoutCompletionService} uses a supplied {@link Executor}
 * to execute {@link CancellableTask cancellable tasks} within a specified period
 * and cancels the task with a specified {@link CancellableTask.CancellationReason cancellation timeout reason}
 * if it is not completed on time.
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
public class CancellableTimeoutExecutorCompletionService<U extends CancellableTask.CancellationReason>
        implements CancellableTimeoutCompletionService<U>, Observable<RunnableTaskListener.EventType, RunnableTaskListener> {
    /**
     * The executor to execute a cancellable task with timeout.
     */
    private final Executor executor;
    /**
     * The schedule executor is used to cancel the task after a timeout
     */
    private final ScheduledExecutorService scheduledExecutor;
    /**
     * Map to track the running tasks and related cancellation tasks
     */
    private final Map<? super Runnable, Future<?>> runningTaskToCancellationTaskMap;
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
    public CancellableTimeoutExecutorCompletionService(Executor executor) {
        if (executor == null) throw new NullPointerException();
        this.executor = executor;
        this.scheduledExecutor = constructScheduler();
        this.runningTaskToCancellationTaskMap = new ConcurrentHashMap<>();
        this.eventManager = new EventManager<>();
    }

    /**
     * Creates a new, {@link ScheduledExecutorService single thread pool scheduled executor service}
     * and sets the policy to immediately remove cancelled tasks
     * from the work queue at the time of cancellation.
     *
     * @return a new {@link ScheduledExecutorService} with policy {@code removeOnCancel=true}
     */
    private static ScheduledExecutorService constructScheduler() {
        final var scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
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

    /**
     * {@inheritDoc}
     *
     * @param task                      the task to submit
     * @param timeout                   the time available to execute the {@code task}
     * @param timeoutCancellationReason the timeout cancellation reason
     * @param <V>                       the type of the task's result
     * @param <C>                       the type or the submitted callable cancellable task
     * @return {@inheritDoc}
     */
    @Override
    public <V, C extends Callable<V> & CancellableTask<U>>
    CancellableFuture<V, U> submit(final C task, final Duration timeout, U timeoutCancellationReason) {
        final var runnableCancellableFutureTask = newTaskFor(task, timeout, timeoutCancellationReason, eventManager);
        executor.execute(runnableCancellableFutureTask);
        return runnableCancellableFutureTask;
    }

    /**
     * {@inheritDoc}
     *
     * @param task                      the task to submit
     * @param result                    the result to return
     * @param timeout                   the time available to execute the {@code task}
     * @param timeoutCancellationReason the timeout cancellation reason
     * @param <V>                       the type of the result
     * @param <R>                       the type or the submitted runnable cancellable task
     * @return {@inheritDoc}
     */
    @Override
    public <V, R extends Runnable & CancellableTask<U>>
    CancellableFuture<V, U> submit(final R task, final V result, final Duration timeout, U timeoutCancellationReason) {
        final var cancellableRunnableFutureTask = newTaskFor(task, result, timeout, timeoutCancellationReason, eventManager);
        executor.execute(cancellableRunnableFutureTask);
        return cancellableRunnableFutureTask;
    }

    /**
     * Creates {@link TimeoutCancellableTask} based on submitted task.
     *
     * @param task                      the task to submit
     * @param timeout                   the time available to execute the {@code task}
     * @param timeoutCancellationReason the timeout cancellation reason
     * @param <V>                       the type of the task's result
     * @param <C>                       the type or the submitted callable cancellable task
     * @param <T>                       the {@link Callable} that is {@link CancellableFuture}
     * @return a {@link  TimeoutCancellableTask} which, when run, will run the
     * underlying runnable and which, as a {@link CancellableFuture}, will yield
     * the given value as its result and provide for reasonable cancellation of
     * the underlying task and which, as a {@code timeout task}, will be cancelled
     * with the specified {@code timeoutCancellationReason} if the task is not completed on time
     */
    @SuppressWarnings("unchecked")
    private <V, C extends Callable<V> & CancellableTask<U>,
            O extends ObserverManager<RunnableTaskListener.EventType, Runnable, RunnableTaskListener>,
            T extends CancellableFuture<V, U> & Runnable>
    T newTaskFor(C task, final Duration timeout, final U timeoutCancellationReason, final O observerManager) {
        return (T) new TimeoutCancellableTask<>(task, timeout, timeoutCancellationReason, observerManager);
    }

    /**
     * Creates {@link TimeoutCancellableTask} based on submitted task.
     *
     * @param task                      the task to submit
     * @param result                    the result to return
     * @param timeout                   the time available to execute the {@code task}
     * @param timeoutCancellationReason the timeout cancellation reason
     * @param <V>                       the type of the task's result
     * @param <R>                       the type or the submitted runnable cancellable task
     * @param <T>                       the {@link Runnable} that is {@link CancellableFuture}
     * @return a {@link  TimeoutCancellableTask} which, when run, will run the
     * underlying runnable and which, as a {@link CancellableFuture}, will yield
     * the given value as its result and provide for reasonable cancellation of
     * the underlying task and which, as a {@code timeout task}, will be cancelled
     * with the specified {@code timeoutCancellationReason} if the task is not completed on time
     */
    @SuppressWarnings("unchecked")
    private <V, R extends Runnable & CancellableTask<U>,
            O extends ObserverManager<RunnableTaskListener.EventType, Runnable, RunnableTaskListener>,
            T extends CancellableFuture<V, U> & Runnable>
    T newTaskFor(R task, V result, final Duration timeout, final U timeoutCancellationReason, final O observerManager) {
        return (T) new TimeoutCancellableTask<>(task, result, timeout, timeoutCancellationReason, observerManager);
    }

    /**
     * The {@link TimeoutCancellableTask} is a {@link RunnableFuture} that is {@link CancellableFuture} which,
     * when run, will run the underlying runnable and which, as a {@link CancellableFuture}, will yield
     * the given value as its result and provide for reasonable cancellation of the underlying task and which,
     * as a {@code timeout task}, will be cancelled with the specified {@code timeoutCancellationReason}
     * if the task is not completed on time.
     *
     * @param <V> the result type returned by this CancellableFuture's {@code get} methods
     * @param <C> the callable cancellable task
     * @param <R> the runnable cancellable task
     */
    final class TimeoutCancellableTask<V,
            C extends Callable<V> & CancellableTask<U>,
            R extends Runnable & CancellableTask<U>,
            O extends ObserverManager<RunnableTaskListener.EventType, Runnable, RunnableTaskListener>>
            extends ObservableCancellableFutureTask<V, U, C, R, O> {
        /**
         * The timeout in nanos.
         */
        final long timeoutNanos;
        /**
         * The timeout cancellation reason.
         */
        final U timeoutCancellationReason;

        /**
         * Instantiates a new {@link TimeoutCancellableTask}.
         *
         * @param cancellableCallable       the cancellable callable
         * @param timeout                   the timeout
         * @param timeoutCancellationReason the timeout cancellation reason
         */
        TimeoutCancellableTask(final C cancellableCallable,
                               final Duration timeout, final U timeoutCancellationReason,
                               final O observerManager) {
            super(cancellableCallable, observerManager);
            this.timeoutNanos = timeout.toNanos();
            this.timeoutCancellationReason = timeoutCancellationReason;
        }

        /**
         * Instantiates a new {@link TimeoutCancellableTask}.
         *
         * @param cancellableRunnable       the cancellable runnable
         * @param value                     the value to return
         * @param timeout                   the timeout
         * @param timeoutCancellationReason the timeout cancellation reason
         */
        TimeoutCancellableTask(final R cancellableRunnable, final V value,
                               final Duration timeout, final U timeoutCancellationReason,
                               final O observerManager) {
            super(cancellableRunnable, value, observerManager);
            this.timeoutNanos = timeout.toNanos();
            this.timeoutCancellationReason = timeoutCancellationReason;
        }

        /**
         * Schedules the running task cancellation once the time is over.
         * If the task is finished on time, the cancellation task ought to
         * be declined.
         */
        protected void beforeExecute() {
            final var cancellationCommand = (Runnable) () -> {
                if (Objects.nonNull(runningTaskToCancellationTaskMap.remove(this)))
                    this.cancel(true, timeoutCancellationReason);
            };
            final var cancellationTask = scheduledExecutor.schedule(cancellationCommand, timeoutNanos, TimeUnit.NANOSECONDS);
            runningTaskToCancellationTaskMap.put(this, cancellationTask);
        }

        /**
         * Declines the cancellation task associated with the running task since it was completed on time.
         */
        protected void afterExecute() {
            final var cancellationTask = runningTaskToCancellationTaskMap.remove(this);
            if (Objects.nonNull(cancellationTask)) cancellationTask.cancel(true);
        }
    }
}
