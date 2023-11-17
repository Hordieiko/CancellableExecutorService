package io.github.hordieiko.concurrent;

import static org.awaitility.Awaitility.await;

final class RunnableCancellable extends CancellableTaskState
        implements RunnableCancellableTask<TestCancellationReason> {

    @Override
    public void run() {
        await().until(this::isCancelled);
    }
}
