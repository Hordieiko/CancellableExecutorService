package io.github.hordieiko.concurrent;

import io.github.hordieiko.observer.Observable;
import io.github.hordieiko.observer.ObserverManager;

import java.util.concurrent.Callable;

class ObservableCancellableFutureTask<V,
        T extends CancellableTask.CancellationReason,
        C extends Callable<V> & CancellableTask<T>,
        R extends Runnable & CancellableTask<T>,
        O extends ObserverManager<RunnableTaskListener.EventType, Runnable, RunnableTaskListener>>
        extends CancellableFutureTask<V, T, C, R>
        implements Observable<RunnableTaskListener.EventType, RunnableTaskListener> {
    private final O observerManager;

    ObservableCancellableFutureTask(final C callableCancellableTask, final O observerManager) {
        super(callableCancellableTask);
        this.observerManager = observerManager;
    }

    ObservableCancellableFutureTask(final R runnableCancellableTask, final V value, final O observerManager) {
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