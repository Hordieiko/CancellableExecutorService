package io.github.hordieiko.concurrent;

import java.time.Duration;

record RunnableNonCancellable(Duration taskDuration)
        implements RunnableCancellableTask<TestCancellationReason>, AwaitableTask {

    @Override
    public void run() {
        awaitTask(taskDuration);
    }

    @Override
    public void cancel(final TestCancellationReason reason) {
        throw new IllegalStateException("Cancellation is not expected");
    }
}
