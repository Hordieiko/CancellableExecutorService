package io.github.hordieiko.concurrent;

import io.github.hordieiko.concurrent.CancellableTask.CancellationReason;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * An {@link ExecutorService} that is responsive to the {@link CancellableTask}.
 * <p>
 * Many blocking library methods respond to interruption by returning early and throwing InterruptedException,
 * which makes it easier to build tasks that are responsive to cancellation. However, not all blocking methods
 * or blocking mechanisms are responsive to interruption; if a thread is blocked performing synchronous socket
 * I/O or waiting to acquire an intrinsic lock, interruption has no effect other than setting the thread’s
 * interrupted status. We can sometimes convince threads blocked in noninterruptible activities to stop by means
 * similar to interruption, but this requires greater awareness of why the thread is blocked.
 *
 * <p><b>Usage Example</b></p>
 * <pre> {@code
 * class Main {
 *     public static void main(String args[]) {
 *         final Socket socket = ...;
 *
 *         final ExecutorService executor = new CancellableThreadPoolExecutor(1, 1,
 *                 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
 *         final CancellableFuture<String> cancellableFuture =
 *                             executor.submitCancellable(new SocketReader(socket));
 *         executor.shutdown();
 *
 *         String result = "";
 *         final long timeout = ...;
 *         final TimeUnit unit = ...;
 *         try {
 *             result = cancellableFuture.get(timeout, unit);
 *         } catch (InterruptedException e) {
 *             // Interrupted while waiting
 *         } catch (ExecutionException e) {
 *             // Task computation threw an exception
 *         } catch (TimeoutException e) {
 *             // Task will be canceled in finally block
 *         } finally {
 *             // No affect if the task is already completed
 *             cancellableFuture.cancel(true,
 *             new CancellableTask.CancellationReason() {
 *                  @Override
 *                  public String toString() {
 *                        return "the cancellation reason";
 *                  }
 *              }
 *             );
 *             // it's also possible to cancel the task as a Future#cancel(boolean),
 *             // but in this case it's impossible to define a cancellation reason.
 *         }
 *     }
 * }
 *
 * class SocketReader implements Callable<String>, CancellableTask<CancellableTask.CancellationReason> {
 *
 *    private final Socket socket;
 *    private volatile CancellableTask.CancellationReason reason;
 *
 *    public SocketReader(Socket socket) {
 *        this.socket = socket;
 *    }
 *
 *    @Override
 *    public String call() throws Exception { // IOException is allows to interrupt the task
 *        try (InputStream in = socket.getInputStream()) {
 *            return new BufferedReader(new InputStreamReader(in))
 *                    .lines().collect(Collectors.joining("\n"));
 *        } catch (IOException e) {
 *            if (reason != null) throws new IllegalStateException(reason.toString(), e);
 *            else throws e;
 *        }
 *    }
 *
 *    @Override
 *    public void cancel(CancellableTask.CancellationReason reason) {
 *        try {
 *            this.reason = reason;
 *            socket.close();
 *        } catch (IOException ignore) {
 *            Thread.currentThread().interrupt();
 *        }
 *    }
 * }
 *
 * }</pre>
 *
 * <p><b>Extension notes:</b> This class overrides the {@link ThreadPoolExecutor#execute(Runnable) execute},
 * {@link ThreadPoolExecutor#newTaskFor(Callable) newTaskFor(Callable)} and
 * {@link ThreadPoolExecutor#newTaskFor(Runnable, Object) newTaskFor(Runnable, defaultValue)}
 * methods to generate internal {@link CancellableFutureTask} objects to control per-task custom cancellation,
 * if an entered task is a {@link CancellableTask}. To preserve functionality, any further overrides of these
 * methods in subclasses are forbidden. However, this class provides alternative protected extension methods
 * {@code #decorateNewTaskFor(Runnable, RunnableFuture)} and {@code #decorateNewTaskFor(Callable, RunnableFuture)}
 * that can be used to customize the concrete task types used to execute commands entered via {@code #execute}
 * and {@code submit}. By default, a {@code CancellableThreadPoolExecutor} uses a task type
 * {@link CancellableFutureTask} for entered {@link CancellableTask}. However, this may be modified or replaced
 * using subclasses of the form:
 *
 * <pre> {@code
 * public class CustomCancellableExecutor extends CancellableThreadPoolExecutor {
 *
 *   static class CustomTask<V> implements RunnableFuture<V> { ... }
 *
 *   protected <V> RunnableFuture<V> decorateNewTaskFor(Callable<V> callable,
 *                                                      RunnableFuture<V> task) {
 *       return new CustomTask<V>(callable, task);
 *   }
 *
 *   protected <V> RunnableFuture<V> decorateNewTaskFor(Runnable runnable,
 *                                                      RunnableFuture<V> task) {
 *       return new CustomTask<V>(runnable, task);
 *   }
 *   // ... add constructors, etc.
 * }}</pre>
 */

public class CancellableThreadPoolExecutor<U extends CancellationReason>
        extends ThreadPoolExecutor
        implements CancellableExecutorService<U> {

    /**
     * Creates a new {@code CancellableThreadPoolExecutor} with the given initial
     * parameters, the
     * {@linkplain Executors#defaultThreadFactory default thread factory}
     * and the {@linkplain ThreadPoolExecutor.AbortPolicy
     * default rejected execution handler}.
     *
     * @param corePoolSize    the number of threads to keep in the pool, even
     *                        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *                        pool
     * @param keepAliveTime   when the number of threads is greater than
     *                        the core, this is the maximum time that excess idle threads
     *                        will wait for new tasks before terminating.
     * @param unit            the time unit for the {@code keepAliveTime} argument
     * @param workQueue       the queue to use for holding tasks before they are
     *                        executed.  This queue will hold only the {@code Runnable}
     *                        tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code corePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException     if {@code workQueue} is null
     */
    public CancellableThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize,
                                         final long keepAliveTime, final TimeUnit unit,
                                         final BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * Creates a new {@code CancellableThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize    the number of threads to keep in the pool, even
     *                        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *                        pool
     * @param keepAliveTime   when the number of threads is greater than
     *                        the core, this is the maximum time that excess idle threads
     *                        will wait for new tasks before terminating.
     * @param unit            the time unit for the {@code keepAliveTime} argument
     * @param workQueue       the queue to use for holding tasks before they are
     *                        executed.  This queue will hold only the {@code Runnable}
     *                        tasks submitted by the {@code execute} method.
     * @param threadFactory   the factory to use when the executor
     *                        creates a new thread
     * @param handler         the handler to use when execution is blocked
     *                        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code corePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException     if {@code workQueue}
     *                                  or {@code threadFactory} or {@code handler} is null
     */
    public CancellableThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize,
                                         final long keepAliveTime, final TimeUnit unit,
                                         final BlockingQueue<Runnable> workQueue,
                                         final ThreadFactory threadFactory,
                                         final RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the given runnable is a {@link CancellableTask}, it will be wrapped in a {@link RunnableFuture}
     * that is responsive to cancellation as defined in the {@link CancellableTask}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of {@code RejectedExecutionHandler},
     *                                    if the task cannot be accepted for execution
     * @throws NullPointerException       if the {@code command} is null
     * @implNote All submitted tasks that are {@code CancellableTask} will be wrapped in a {@code CancellableFutureTask}
     * and executed by this method, except those executed directly. Thus, if this runnable is a {@code CancellableTask},
     * it needs to be wrapped in a {@code CancellableFutureTask} that is responsive to the custom cancellation.
     */
    @Override
    public void execute(final Runnable command) {
        super.execute((command instanceof CancellableTask) ? newTaskFor(command, null) : command);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the given runnable is a {@link CancellableTask}, it will be wrapped in a RunnableFuture
     * that is responsive to cancellation as defined in the {@link CancellableTask}.
     *
     * @param callable the callable task being wrapped
     * @return {@inheritDoc}, and if it is a {@code CancellableTask},
     * will be responsive to the custom cancellation.
     */
    @Override
    protected final <V> RunnableFuture<V> newTaskFor(final Callable<V> callable) {
        return decorateNewTaskFor(callable,
                (callable instanceof CancellableTask)
                        ? new CancellableFutureTask<>((CancellableTask<U> & Callable<V>) callable)
                        : super.newTaskFor(callable));
    }

    /**
     * Modifies or replaces the task used to execute a callable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     *
     * @param callable the submitted Callable
     * @param task     the task created to execute the runnable
     * @param <V>      the type of the task's result
     * @return a task that can execute the runnable
     */
    protected <V> RunnableFuture<V> decorateNewTaskFor(final Callable<V> callable, final RunnableFuture<V> task) {
        return task;
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the given runnable is a {@link CancellableTask}, it will be wrapped in a RunnableFuture
     * that is responsive to cancellation as defined in the {@link CancellableTask}.
     *
     * @param runnable the runnable task being wrapped
     * @param value    the default value for the returned future
     * @return {@inheritDoc}, and if it is a {@code CancellableTask},
     * will be responsive to the custom cancellation.
     */
    @Override
    protected final <V> RunnableFuture<V> newTaskFor(final Runnable runnable, final V value) {
        return decorateNewTaskFor(runnable, value,
                (runnable instanceof CancellableTask)
                        ? new CancellableFutureTask<>((CancellableTask<U> & Runnable) runnable, value)
                        : super.newTaskFor(runnable, value));
    }

    /**
     * Modifies or replaces the task used to execute a runnable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     *
     * @param runnable the submitted Runnable
     * @param value    the submitted default value for the returned future
     * @param task     the task created to execute the runnable
     * @param <V>      the type of the task's result
     * @return a task that can execute the runnable
     */
    protected <V> RunnableFuture<V> decorateNewTaskFor(final Runnable runnable, final V value, final RunnableFuture<V> task) {
        return task;
    }

    /**
     * {@inheritDoc}
     *
     * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
     * @throws NullPointerException       if the {@code task} is null
     */
    @Override
    public <V, C extends Callable<V> & CancellableTask<U>> CancellableFuture<V, U> submitCancellable(final C task) {
        return (CancellableFuture<V, U>) this.submit(task);
    }

    /**
     * {@inheritDoc}
     *
     * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
     * @throws NullPointerException       if the {@code task} is null
     */
    @Override
    public <V, R extends Runnable & CancellableTask<U>> CancellableFuture<V, U> submitCancellable(final R task, final V result) {
        return (CancellableFuture<V, U>) this.submit(task, result);
    }

    /**
     * {@inheritDoc}
     *
     * @throws RejectedExecutionException if the {@code task} cannot be scheduled for execution
     * @throws NullPointerException       if the {@code task} is null
     */
    @Override
    public <R extends Runnable & CancellableTask<U>> CancellableFuture<?, U> submitCancellable(final R task) {
        return (CancellableFuture<?, U>) this.submit(task);
    }
}
