package io.github.hordieiko.concurrent;

import io.github.hordieiko.observer.EventManager;
import io.github.hordieiko.observer.Observable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A {@link TimeoutExecutorService} that executes each submitted task
 * within a specified period and cancels the task if it does not complete on time.
 *
 * <p>An internal executor is used to track the timeout of the submitted task,
 * which schedules the cancellation task before the submitted task execution.{@code task}
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
        implements TimeoutExecutorService, CancellableExecutorService<U>,
        Observable<RunnableTaskListener.EventType, RunnableTaskListener> {

    /**
     * The main {@code TimeoutExecutor} to whom all the requests are delegated.
     */
    private final TimeoutExecutor<U> timeoutExecutor;

    /**
     * Creates cached timeout thread pool to execute the tasks.
     *
     * @param timeout                   the time available to execute the task
     * @param timeoutCancellationReason the timeout cancellation reason
     * @throws NullPointerException if {@code timeout} or {@code timeoutCancellationReason} is null
     */
    public TimeoutThreadPoolExecutorService(final Duration timeout, final U timeoutCancellationReason) {
        if (timeout == null || timeoutCancellationReason == null) throw new NullPointerException();
        this.timeoutExecutor = new CachedThreadPoolTimeoutExecutor<>(timeout, timeoutCancellationReason);
    }

    /**
     * Creates timeout thread pool based on the given executor
     *
     * @param sourceExecutor            the thread pool executor whose initial parameters
     *                                  will be used to create a timeout executor
     * @param timeout                   the time available to execute the task
     * @param timeoutCancellationReason the timeout cancellation reason
     * @throws NullPointerException if {@code sourceExecutor} or {@code timeout}
     *                              or {@code timeoutCancellationReason} is null
     */
    public TimeoutThreadPoolExecutorService(final ThreadPoolExecutor sourceExecutor, final Duration timeout, final U timeoutCancellationReason) {
        if (sourceExecutor == null || timeout == null || timeoutCancellationReason == null)
            throw new NullPointerException();
        this.timeoutExecutor = new CustomTimeoutExecutor<>(timeout, timeoutCancellationReason, sourceExecutor);
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @param <T>  the type of the task's result
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return timeoutExecutor.submit(task);
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @param <V>  the type of the task's result
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public <V, C extends Callable<V> & CancellableTask<U>>
    CancellableFuture<V, U> submitCancellable(final C task) {
        return timeoutExecutor.submitCancellable(task);
    }

    /**
     * {@inheritDoc}
     *
     * @param task   the task to submit
     * @param result the result to return
     * @param <T>    the type of the result
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        return timeoutExecutor.submit(task, result);
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
     */
    @Override
    public <V, R extends Runnable & CancellableTask<U>>
    CancellableFuture<V, U> submitCancellable(final R task, final V result) {
        return timeoutExecutor.submitCancellable(task, result);
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public Future<?> submit(final Runnable task) {
        return timeoutExecutor.submit(task);
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @return {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public <R extends Runnable & CancellableTask<U>>
    CancellableFuture<?, U> submitCancellable(final R task) {
        return timeoutExecutor.submitCancellable(task);
    }

    /**
     * {@inheritDoc}
     *
     * @param command the runnable task
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @implNote It is strongly advised not to directly invoke the
     * {@link TimeoutExecutor#execute(Runnable) execute(Runnable)} method, so the
     * {@link TimeoutExecutor#submit(Runnable) submit(Runnable)} is used instead.
     */
    @Override
    public void execute(final Runnable command) {
        timeoutExecutor.submit(command);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        timeoutExecutor.shutdown();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public List<Runnable> shutdownNow() {
        return timeoutExecutor.shutdownNow();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return timeoutExecutor.isShutdown();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return timeoutExecutor.isTerminated();
    }

    /**
     * {@inheritDoc}
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return {@inheritDoc}
     * @throws InterruptedException {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return timeoutExecutor.awaitTermination(timeout, unit);
    }

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    public void subscribe(final RunnableTaskListener.EventType event, final RunnableTaskListener listener) {
        timeoutExecutor.subscribe(event, listener);
    }

    /**
     * {@inheritDoc}
     *
     * @param event    the event
     * @param listener the listener for the specified event
     */
    @Override
    public void unsubscribe(final RunnableTaskListener.EventType event, final RunnableTaskListener listener) {
        timeoutExecutor.unsubscribe(event, listener);
    }

    private static final class CachedThreadPoolTimeoutExecutor<U extends CancellableTask.CancellationReason> extends TimeoutExecutor<U> {
        /**
         * Creates a thread pool that creates new threads as needed,
         * but will reuse previously constructed threads when they are available.
         * <p>
         * The tasks that are not finished on time will be canceled.
         *
         * @param timeout                   the time available to execute the task
         * @param timeoutCancellationReason the timeout cancellation reason
         */
        CachedThreadPoolTimeoutExecutor(final Duration timeout, final U timeoutCancellationReason) {
            super(timeout, timeoutCancellationReason, 0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
        }
    }

    private static final class CustomTimeoutExecutor<U extends CancellableTask.CancellationReason> extends TimeoutExecutor<U> {
        /**
         * Creates a new {@code TimeoutExecutor} with the given initial parameters.
         *
         * @param timeout                   the time available to execute the task
         * @param timeoutCancellationReason the timeout cancellation reason
         * @param executor                  the executor used as the source for initial parameters
         */
        CustomTimeoutExecutor(final Duration timeout, final U timeoutCancellationReason, final ThreadPoolExecutor executor) {
            super(timeout, timeoutCancellationReason, executor.getCorePoolSize(), executor.getMaximumPoolSize(),
                    executor.getKeepAliveTime(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS,
                    executor.getQueue(), executor.getThreadFactory(), executor.getRejectedExecutionHandler());
        }
    }

    /**
     * A {@link TimeoutExecutor} that executes each submitted task
     * within a specified period and cancels the task if it does not complete on time.
     *
     * <p>An internal executor is used to track the timeout of the submitted task,
     * which schedules the cancellation task before the submitted task execution.
     * If the submitted task is completed on time, the cancellation task is destroyed.
     *
     * <p>The {@link TimeoutExecutor} is responsive to the {@link CancellableTask}.
     *
     * @param <U> the cancellation reason type
     */
    abstract static class TimeoutExecutor<U extends CancellableTask.CancellationReason>
            extends CancellableThreadPoolExecutor<U>
            implements Observable<RunnableTaskListener.EventType, RunnableTaskListener> {
        /**
         * Time available to execute the task
         */
        private final long timeoutNanos;
        /**
         * The reason for task cancellation by timeout.
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
         * Instantiates a new Timeout executor.
         *
         * @param timeout                   the time available to execute the task
         * @param timeoutCancellationReason the timeout cancellation reason
         * @param corePoolSize              the core pool size
         * @param maximumPoolSize           the maximum pool size
         * @param keepAliveTime             the keep alive time
         * @param unit                      the unit
         * @param workQueue                 the work queue
         */
        TimeoutExecutor(final Duration timeout, final U timeoutCancellationReason,
                        final int corePoolSize, final int maximumPoolSize,
                        final long keepAliveTime, final TimeUnit unit,
                        final BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
            this.timeoutNanos = timeout.toNanos();
            this.timeoutCancellationReason = timeoutCancellationReason;
        }

        /**
         * Instantiates a new Timeout executor.
         *
         * @param timeout                   the time available to execute the task
         * @param timeoutCancellationReason the timeout cancellation reason
         * @param corePoolSize              the core pool size
         * @param maximumPoolSize           the maximum pool size
         * @param keepAliveTime             the keep alive time
         * @param unit                      the unit
         * @param workQueue                 the work queue
         * @param threadFactory             the thread factory
         * @param handler                   the handler
         */
        TimeoutExecutor(final Duration timeout, final U timeoutCancellationReason,
                        final int corePoolSize, final int maximumPoolSize,
                        final long keepAliveTime, final TimeUnit unit,
                        final BlockingQueue<Runnable> workQueue,
                        final ThreadFactory threadFactory,
                        final RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
            this.timeoutNanos = timeout.toNanos();
            this.timeoutCancellationReason = timeoutCancellationReason;
        }

        private ScheduledExecutorService constructScheduler() {
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
                if (Objects.nonNull(runningTaskToCancellationTaskMap.remove(runningTask))) {
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
            if (Objects.nonNull(cancellationTask)) cancellationTask.cancel(true);

            eventManager.notify(RunnableTaskListener.EventType.AFTER_EXECUTE, runningTask);
        }

        /**
         * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
         * @throws NullPointerException       if the {@code task} is null
         * @implNote If a task has been successfully submitted to the {@code executor},
         * it has to be registered in the {@code taskTracker}.
         */
        @Override
        public <T> Future<T> submit(final Callable<T> task) {
            return taskTracker.submitAndRegister(() -> super.submit(task));
        }

        /**
         * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
         * @throws NullPointerException       if the {@code task} is null
         * @implNote If a task has been successfully submitted to the {@code executor},
         * it has to be registered in the {@code taskTracker}.
         */
        @Override
        public <T> Future<T> submit(final Runnable task, final T result) {
            return taskTracker.submitAndRegister(() -> super.submit(task, result));
        }

        /**
         * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
         * @throws NullPointerException       if the {@code task} is null
         * @implNote If a task has been successfully submitted to the {@code executor},
         * it has to be registered in the {@code taskTracker}.
         */
        @Override
        public Future<?> submit(final Runnable task) {
            return taskTracker.submitAndRegister(() -> super.submit(task));
        }

        /**
         * @throws RejectedExecutionException at discretion of {@code RejectedExecutionHandler},
         *                                    if the task cannot be accepted for execution
         * @throws NullPointerException       if the {@code task} is null
         * @implNote Direct invocation is not advised, use {@link #submit(Runnable)} instead.
         * The {@link #execute(Runnable)} method is invoked by the
         * {@link #submit} methods, which registers tasks in the {@code taskTracker}.
         * Thus, it is strongly advised not to directly invoke the method to
         * prevent incorrect task registration.
         */
        @Override
        public final void execute(final Runnable task) {
            super.execute(task);
        }

        /**
         * The {@code shutdown} action is available only when all submitted
         * tasks have their scheduled cancellation task.
         *
         * @implNote Only the first call to the {@code shutdown()} method
         * will invoke the shutdown action, which requires waiting until
         * all submitted tasks have been processed, the others will skip
         * the action since the invocation has no additional effect if
         * already shut down.<br/>
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
             * Shutdown when possible.
             *
             * @param shutdown the shutdown action
             * @implNote If some thread has already acquired the {@code shutdownLock}, then he is responsible for the shutdown action, the others needn't await.
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
}
