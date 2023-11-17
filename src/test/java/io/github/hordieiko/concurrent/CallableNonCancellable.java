package io.github.hordieiko.concurrent;

import java.time.Duration;

record CallableNonCancellable<V>(Duration taskDuration, V result)
        implements CallableCancellableTask<V, TestCancellationReason>, AwaitableTask {
    @Override
    public V call() {
        return awaitTaskResult(taskDuration, result);
    }

    @Override
    public void cancel(final TestCancellationReason reason) {
        throw new IllegalStateException("Cancellation is not expected");
    }
}
