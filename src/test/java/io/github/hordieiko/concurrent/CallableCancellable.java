package io.github.hordieiko.concurrent;

import static org.awaitility.Awaitility.await;

final class CallableCancellable extends CancellableTaskState
        implements CallableCancellableTask<String, TestCancellationReason> {

    @Override
    public String call() {
        await().until(this::isCancelled);
        return "unexpected result";
    }
}
