package io.github.hordieiko.concurrent;

sealed abstract class CancellableTaskState
        implements CancellableTask<TestCancellationReason>, CancellableTask.State<TestCancellationReason>
        permits RunnableCancellable, CallableCancellable {
    protected volatile boolean cancelled;
    protected volatile TestCancellationReason cancellationReason;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public TestCancellationReason getCancellationReason() {
        return cancellationReason;
    }

    @Override
    public void cancel(final TestCancellationReason reason) {
        this.cancelled = true;
        this.cancellationReason = reason;
    }
}
