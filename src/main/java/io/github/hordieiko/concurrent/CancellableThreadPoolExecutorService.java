package io.github.hordieiko.concurrent;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * An {@link CancellableExecutorService} that is responsive to the {@link CancellableTask}.
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
 *         final CancellableExecutorService executor = new CancellableThreadPoolExecutor<CancellableTask.CancellationReason>(1, 1,
 *                 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
 *         final CancellableFuture<String> cancellableFuture =
 *                             executor.submit(new SocketReader(socket));
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
 * class SocketReader implements CallableCancellableTask<String, CancellableTask.CancellationReason> {
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
 * {@code ThreadPoolExecutor#newTaskFor(Callable) newTaskFor(Callable)} and
 * {@code ThreadPoolExecutor#newTaskFor(Runnable, Object) newTaskFor(Runnable, defaultValue)}
 * methods to generate internal {@link CancellableFuture} objects to control per-task custom cancellation,
 * based on entered {@link CancellableTask task}. To preserve functionality, any further overrides of these
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
 *
 * @param <U> the cancellation reason type
 */
public class CancellableThreadPoolExecutorService<U extends CancellableTask.CancellationReason>
        implements CancellableExecutorService<U>, AutoCloseable {
    /**
     * The cancellable thread pool executor
     */
    private final CancellableThreadPoolExecutor cancellableExecutor;

    /**
     * Instantiates a new Cancellable thread pool executor service.
     *
     * @param corePoolSize    the core pool size
     * @param maximumPoolSize the maximum pool size
     * @param keepAliveTime   the keep alive time
     * @param unit            the unit
     * @param workQueue       the work queue
     */
    public CancellableThreadPoolExecutorService(final int corePoolSize, final int maximumPoolSize,
                                                final long keepAliveTime, final TimeUnit unit,
                                                final BlockingQueue<Runnable> workQueue) {
        this.cancellableExecutor = new CancellableThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * Instantiates a new Cancellable thread pool executor service.
     *
     * @param corePoolSize    the core pool size
     * @param maximumPoolSize the maximum pool size
     * @param keepAliveTime   the keep alive time
     * @param unit            the unit
     * @param workQueue       the work queue
     * @param threadFactory   the thread factory
     * @param handler         the handler
     */
    public CancellableThreadPoolExecutorService(final int corePoolSize, final int maximumPoolSize,
                                                final long keepAliveTime, final TimeUnit unit,
                                                final BlockingQueue<Runnable> workQueue,
                                                final ThreadFactory threadFactory,
                                                final RejectedExecutionHandler handler) {
        this.cancellableExecutor = new CancellableThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(final Thread t, final Runnable r) {
        // for extension
    }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     * <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}*</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if          execution completed normally
     */
    protected void afterExecute(final Runnable r, final Throwable t) {
        // for extension
    }

    /**
     * Modifies or replaces the task used to execute a callable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     *
     * @param <V>      the type of the task's result
     * @param callable the submitted Callable
     * @param task     the task created to execute the runnable
     * @return a task that can execute the runnable
     */
    protected <V> RunnableCancellableFuture<V, U> decorateNewTaskFor(final Callable<V> callable,
                                                                     final RunnableCancellableFuture<V, U> task) {
        // for extension, by default simply returns the given task
        return task;
    }

    /**
     * Modifies or replaces the task used to execute a runnable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     *
     * @param <V>      the type of the task's result
     * @param runnable the submitted Runnable
     * @param value    the submitted default value for the returned future
     * @param task     the task created to execute the runnable
     * @return a task that can execute the runnable
     */
    protected <V> RunnableCancellableFuture<V, U> decorateNewTaskFor(final Runnable runnable, final V value,
                                                                     final RunnableCancellableFuture<V, U> task) {
        // for extension, by default simply returns the given task
        return task;
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        return cancellableExecutor.isTerminating();
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @param <V>  the type of the task's result
     * @return {@inheritDoc}
     */
    @Override
    public <V> CancellableFuture<V, U> submit(final CallableCancellableTask<V, U> task) {
        return cancellableExecutor.submit(task);
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @param <V>  the type of the task's result
     * @return {@inheritDoc}
     */
    @Override
    public <V> CancellableFuture<V, U> submit(final RunnableCancellableTask<U> task, final V result) {
        return cancellableExecutor.submit(task, result);
    }

    /**
     * {@inheritDoc}
     *
     * @param task the task to submit
     * @return {@inheritDoc}
     */
    @Override
    public CancellableFuture<Void, U> submit(final RunnableCancellableTask<U> task) {
        return cancellableExecutor.submit(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        cancellableExecutor.shutdown();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public List<Runnable> shutdownNow() {
        return cancellableExecutor.shutdownNow();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return cancellableExecutor.isShutdown();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return cancellableExecutor.isTerminated();
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
        return cancellableExecutor.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        cancellableExecutor.close();
    }

    /**
     * The cancellable thread pool executor implementation.
     */
    private final class CancellableThreadPoolExecutor extends ThreadPoolExecutor implements CancellableExecutorService<U> {

        /**
         * Instantiates a new {@link CancellableThreadPoolExecutor}.
         *
         * @param corePoolSize    the core pool size
         * @param maximumPoolSize the maximum pool size
         * @param keepAliveTime   the keep alive time
         * @param unit            the unit
         * @param workQueue       the work queue
         */
        public CancellableThreadPoolExecutor(final int corePoolSize, final int maximumPoolSize,
                                             final long keepAliveTime, final TimeUnit unit,
                                             final BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        /**
         * Instantiates a new {@link CancellableThreadPoolExecutor}.
         *
         * @param corePoolSize    the core pool size
         * @param maximumPoolSize the maximum pool size
         * @param keepAliveTime   the keep alive time
         * @param unit            the unit
         * @param workQueue       the work queue
         * @param threadFactory   the thread factory
         * @param handler         the handler
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
         *
         * @param t the thread that will run task {@code r}
         * @param r the task that will be executed
         * @implNote this implementation delegates the invocation
         * to the outer {@link CancellableThreadPoolExecutorService}
         * to let subclasses override it.
         */
        @Override
        protected void beforeExecute(final Thread t, final Runnable r) {
            CancellableThreadPoolExecutorService.this.beforeExecute(t, r);
        }

        /**
         * {@inheritDoc}
         *
         * @param r the runnable that has completed
         * @param t the exception that caused termination, or null if
         *          execution completed normally
         * @implNote this implementation delegates the invocation
         * to the outer {@link CancellableThreadPoolExecutorService}
         * to let subclasses override it.
         */
        @Override
        protected void afterExecute(final Runnable r, final Throwable t) {
            CancellableThreadPoolExecutorService.this.afterExecute(r, t);
        }

        /**
         * {@inheritDoc}
         * <p>
         * The given runnable is a {@link CancellableTask} so it will be wrapped in a RunnableFuture
         * that is responsive to cancellation as defined in the {@link CancellableTask}.
         *
         * @param callable the callable task being wrapped
         * @return {@inheritDoc}, and if it is a {@code CancellableTask},
         * will be responsive to the custom cancellation.
         */
        @Override
        protected <V> RunnableFuture<V> newTaskFor(final Callable<V> callable) {
            final var callableCancellableTask = (CallableCancellableTask<V, U>) callable;
            return decorateNewTaskFor(callable, new CancellableFutureTask<>(callableCancellableTask));
        }

        /**
         * {@inheritDoc}
         * <p>
         * The given runnable is a {@link CancellableTask} so it will be wrapped in a RunnableFuture
         * that is responsive to cancellation as defined in the {@link CancellableTask}.
         *
         * @param runnable the runnable task being wrapped
         * @param value    the default value for the returned future
         * @return {@inheritDoc}, and if it is a {@code CancellableTask},
         * will be responsive to the custom cancellation.
         */
        @Override
        protected <V> RunnableFuture<V> newTaskFor(final Runnable runnable, final V value) {
            @SuppressWarnings("unchecked") final var runnableCancellableTask = (RunnableCancellableTask<U>) runnable;
            return decorateNewTaskFor(runnable, value, new CancellableFutureTask<>(runnableCancellableTask, value));
        }

        /**
         * Modifies or replaces the task used to execute a callable.
         * This method can be used to override the concrete
         * class used for managing internal tasks.
         * The default implementation simply returns the given task.
         *
         * @param <V>      the type of the task's result
         * @param callable the submitted Callable
         * @param task     the task created to execute the runnable
         * @return a task that can execute the runnable
         * @implNote this implementation delegates the invocation to the outer
         * {@link CancellableThreadPoolExecutorService} to let subclasses override it.
         */
        <V> RunnableCancellableFuture<V, U> decorateNewTaskFor(final Callable<V> callable, final RunnableCancellableFuture<V, U> task) {
            return CancellableThreadPoolExecutorService.this.decorateNewTaskFor(callable, task);
        }

        /**
         * Modifies or replaces the task used to execute a runnable.
         * This method can be used to override the concrete
         * class used for managing internal tasks.
         * The default implementation simply returns the given task.
         *
         * @param <V>      the type of the task's result
         * @param runnable the submitted Runnable
         * @param value    the submitted default value for the returned future
         * @param task     the task created to execute the runnable
         * @return a task that can execute the runnable
         * @implNote this implementation delegates the invocation to the outer {@link CancellableThreadPoolExecutorService} to let subclasses override it.
         */
        <V> RunnableCancellableFuture<V, U> decorateNewTaskFor(final Runnable runnable, final V value, final RunnableCancellableFuture<V, U> task) {
            return CancellableThreadPoolExecutorService.this.decorateNewTaskFor(runnable, value, task);
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
        public <V> CancellableFuture<V, U> submit(final CallableCancellableTask<V, U> task) {
            return (CancellableFuture<V, U>) super.submit(task);
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
        public <V> CancellableFuture<V, U> submit(final RunnableCancellableTask<U> task, final V result) {
            return (CancellableFuture<V, U>) super.submit(task, result);
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
        public CancellableFuture<Void, U> submit(final RunnableCancellableTask<U> task) {
            return this.submit(task, null);
        }
    }
}
