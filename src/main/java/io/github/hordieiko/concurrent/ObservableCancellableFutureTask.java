package io.github.hordieiko.concurrent;

import io.github.hordieiko.observer.Observable;
import io.github.hordieiko.observer.ObserverManager;

sealed class ObservableCancellableFutureTask<V,
        U extends CancellableTask.CancellationReason,
        O extends ObserverManager<RunnableTaskListener.EventType, Runnable, RunnableTaskListener>>
        extends CancellableFutureTask<V, U>
        implements Observable<RunnableTaskListener.EventType, RunnableTaskListener>
        permits CancellableTimeoutExecutorCompletionService.TimeoutCancellableTask {
    private final O observerManager;

    ObservableCancellableFutureTask(final CallableCancellableTask<V, U> callableCancellableTask, final O observerManager) {
        super(callableCancellableTask);
        this.observerManager = observerManager;
    }

    ObservableCancellableFutureTask(final RunnableCancellableTask<U> runnableCancellableTask, final V value, final O observerManager) {
        super(runnableCancellableTask, value);
        this.observerManager = observerManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void run() {
        observerManager.notify(RunnableTaskListener.EventType.BEFORE_EXECUTE, this);
        beforeExecute();
        super.run();
    }

    protected void beforeExecute() {
        // for extension
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void done() {
        afterExecute();
        observerManager.notify(RunnableTaskListener.EventType.AFTER_EXECUTE, this);
    }

    protected void afterExecute() {
        // for extension
    }

    @Override
    public void subscribe(final RunnableTaskListener.EventType event, final RunnableTaskListener listener) {
        observerManager.subscribe(event, listener);
    }

    @Override
    public void unsubscribe(final RunnableTaskListener.EventType event, final RunnableTaskListener listener) {
        observerManager.unsubscribe(event, listener);
    }
}