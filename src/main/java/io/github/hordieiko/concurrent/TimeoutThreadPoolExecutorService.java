package io.github.hordieiko.concurrent;

import io.github.hordieiko.observer.EventManager;
import io.github.hordieiko.observer.Observable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@link CancellableExecutorService} that executes each submitted task
 * within a specified period and cancels the task if it does not complete on time.
 *
 * <p>An internal executor is used to track the timeout of the submitted task,
 * which schedules the cancellation task before the submitted task execution.
 * If the submitted task is completed on time, the cancellation task is destroyed.
 *
 * <p>The {@link TimeoutThreadPoolExecutorService} is responsive to the {@link CancellableTask}.
 *
 * <p>It's possible to subscribe to notifications before and after
 * a submitted task is executed to take specific actions with it.
 *
 * @param <U> the cancellation reason type
 */
public class TimeoutThreadPoolExecutorService<U extends CancellableTask.CancellationReason>
        extends CancellableThreadPoolExecutorService<U>
        implements Observable<RunnableTaskListener.EventType, RunnableTaskListener> {
    /**
     * Time available to execute the task
     */
    private final long timeoutNanos;
    /**
     * The reason to cancel a task by timeout
     */
    private final U timeoutCancellationReason;
    /**
     * Controls the submitted and processed tasks to get {@code #shutdown()} action
     * available only when all submitted tasks have their scheduled cancellation task.
     */
    private final TaskTracker taskTracker = new TaskTracker();
    /**
     * The schedule executor is used to cancel the task after a timeout
     */
    private final ScheduledExecutorService scheduledExecutor = constructScheduler();
    /**
     * Map to track the running tasks and related cancellation tasks
     */
    private final Map<Runnable, Future<?>> runningTaskToCancellationTaskMap = new ConcurrentHashMap<>();
    /**
     * The {@code EventManager} manages events to notify subscribed listeners about changes.
     */
    private final EventManager<RunnableTaskListener.EventType, Runnable, RunnableTaskListener> eventManager = new EventManager<>();


    /**
     * Creates a thread pool that creates new threads as needed,
     * but will reuse previously constructed threads when they are available.
     * <p>
     * The tasks that are not finished on time will be canceled.
     *
     * @param timeout                   the time available to execute the task
     * @param timeoutCancellationReason the timeout cancellation reason
     */
    public TimeoutThreadPoolExecutorService(final Duration timeout, final U timeoutCancellationReason) {
        // TimeoutCachedThreadPoolExecutor
        super(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
        this.timeoutNanos = timeout.toNanos();
        this.timeoutCancellationReason = timeoutCancellationReason;
    }

    /**
     * Creates timeout thread pool based on the given executor
     *
     * @param sourceExecutor            the thread pool executor whose initial parameters
     *                                  will be used to create a timeout executor
     * @param timeout                   the time available to execute the task
     * @param timeoutCancellationReason the timeout cancellation reason
     */
    public TimeoutThreadPoolExecutorService(final ThreadPoolExecutor sourceExecutor, final Duration timeout, final U timeoutCancellationReason) {
        super(sourceExecutor.getCorePoolSize(), sourceExecutor.getMaximumPoolSize(),
                sourceExecutor.getKeepAliveTime(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS,
                sourceExecutor.getQueue(), sourceExecutor.getThreadFactory(), sourceExecutor.getRejectedExecutionHandler());
        this.timeoutNanos = timeout.toNanos();
        this.timeoutCancellationReason = timeoutCancellationReason;
    }

    private static ScheduledExecutorService constructScheduler() {
        final var scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * Creates the cancellation task to stop the running task.
     *
     * @param runningTaskThread the thread that will run task {@code runningTask}
     * @param runningTask       the task that will be executed
     * @implNote Tasks that are supplied to the Executor Service will be
     * wrapped in a RunnableFuture, except for tasks that are supplied
     * directly via {@link Executor#execute} method. So, if the running
     * task is a {@link Future}, it will be canceled using {@link Future#cancel},
     * otherwise, the thread running the task will be interrupted to stop
     * the running task after the available time has expired.
     * <p>If the task is finished on time, the cancellation task ought to
     * be declined.
     * <p>To track the running task and its cancellation task they are
     * mapped via {@link #runningTaskToCancellationTaskMap}.
     * <p>Once a cancellation task has been created for the entered task,
     * it has to be deregistered from the {@code taskTracker} to note that
     * the task is processed.
     */
    @Override
    protected final void beforeExecute(final Thread runningTaskThread, final Runnable runningTask) {
        eventManager.notify(RunnableTaskListener.EventType.BEFORE_EXECUTE, runningTask);

        final var cancellationCommand = (Runnable) () -> {
            if (runningTaskToCancellationTaskMap.remove(runningTask) != null) {
                if (runningTask instanceof CancellableFuture) {
                    @SuppressWarnings("unchecked") final var task = (CancellableFuture<?, U>) runningTask;
                    task.cancel(true, timeoutCancellationReason);
                } else if (runningTask instanceof Future) ((Future<?>) runningTask).cancel(true);
                else runningTaskThread.interrupt();
            }
        };
        final var cancellationTask = scheduledExecutor.schedule(cancellationCommand, timeoutNanos, TimeUnit.NANOSECONDS);
        runningTaskToCancellationTaskMap.put(runningTask, cancellationTask);
        taskTracker.deregisterAndSignal();
    }

    /**
     * Declines the cancellation task associated with the running task.
     *
     * @param runningTask the runnable task that has completed
     * @param t           the exception that caused termination, or null if
     *                    execution completed normally
     * @implNote Since the task was completed, the cancellation task must be declined.
     */
    @Override
    protected final void afterExecute(final Runnable runningTask, final Throwable t) {
        final var cancellationTask = runningTaskToCancellationTaskMap.remove(runningTask);
        if (cancellationTask != null) cancellationTask.cancel(true);

        eventManager.notify(RunnableTaskListener.EventType.AFTER_EXECUTE, runningTask);
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
     * @param task the task to submit
     * @param <V>  the type of the task's result
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @implNote If a task has been successfully submitted to the {@code executor},
     * it has to be registered in the {@code taskTracker}.
     */
    @Override
    public <V> CancellableFuture<V, U> submit(CallableCancellableTask<V, U> task) {
        return taskTracker.submitAndRegister(() -> super.submit(task));
    }

    /**
     * {@inheritDoc}
     *
     * @param task   the task to submit
     * @param result the result to return
     * @param <V>    the type of the result
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @implNote If a task has been successfully submitted to the {@code executor},
     * it has to be registered in the {@code taskTracker}.
     */
    @Override
    public <V> CancellableFuture<V, U> submit(RunnableCancellableTask<U> task, V result) {
        return taskTracker.submitAndRegister(() -> super.submit(task, result));
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @implNote If a task has been successfully submitted to the {@code executor},
     * it has to be registered in the {@code taskTracker}.
     */
    @Override
    public CancellableFuture<Void, U> submit(final RunnableCancellableTask<U> task) {
        return taskTracker.submitAndRegister(() -> super.submit(task));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@code shutdown} action is available only when all submitted
     * tasks have their scheduled cancellation task.
     *
     * @implNote Only the first call to the {@code shutdown()} method
     * will invoke the shutdown action, which requires waiting until
     * all submitted tasks have been processed, the others will skip
     * the action since the invocation has no additional effect if
     * already shut down.
     */
    @Override
    public void shutdown() {
        if (super.isShutdown()) return;
        taskTracker.shutdownWhenPossible(() -> {
            try {
                scheduledExecutor.shutdown();
            } finally {
                super.shutdown();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public List<Runnable> shutdownNow() {
        try {
            scheduledExecutor.shutdownNow();
        } catch (Exception ignore) {
            // The shutdown of the additional scheduled executor
            // mustn't affect the main executor shutdown.
        }
        return super.shutdownNow();
    }

    @FunctionalInterface
    public interface RunnableTaskObserver extends Consumer<Runnable> {
        enum Type {
            BEFORE_EXECUTE, AFTER_EXECUTE
        }
    }

    /**
     * Controls the submitted and processed tasks.
     */
    private static final class TaskTracker {
        /**
         * The Lock is used to track submitted tasks and processed tasks.
         */
        private final Lock lock = new ReentrantLock();
        /**
         * The condition is used to check if the shutdown is available.
         * The shutdown is available when all submitted tasks are processed
         * (for each submitted task has been created cancellation task).
         */
        private final Condition allProcessed = lock.newCondition();
        /**
         * The Lock is used to make the shutdown action possible only for
         * the first invocation.
         */
        private final Lock shutdownLock = new ReentrantLock();
        /**
         * Used to count submitted and processed tasks.
         */
        private int inProcess = 0;

        /**
         * Given supplier submits a task and then
         * the {@link TaskTracker} register task submission.
         *
         * @param <T>    the type of submitted task
         * @param submit the supplier that submits the task
         * @return the t
         */
        public <T> T submitAndRegister(Supplier<T> submit) {
            lock.lock();
            try {
                final var result = submit.get();
                inProcess++;
                return result;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Deregister and signal.
         */
        public void deregisterAndSignal() {
            lock.lock();
            try {
                inProcess--;
                allProcessed.signal();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Invokes the shutdown action once it allowed
         *
         * @param shutdown the shutdown action
         * @implNote If some thread has already acquired the {@code shutdownLock},
         * then he is responsible for the shutdown action,
         * the others needn't await.
         */
        public void shutdownWhenPossible(final Runnable shutdown) {
            if (shutdownLock.tryLock()) {
                try {
                    lock.lock();
                    try {
                        while (inProcess != 0)
                            allProcessed.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        try {
                            shutdown.run();
                        } finally {
                            lock.unlock();
                        }
                    }
                } finally {
                    shutdownLock.unlock();
                }
            }
        }
    }
}
