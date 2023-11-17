package io.github.hordieiko.concurrent;

import org.awaitility.Awaitility;

import java.time.Duration;

sealed interface AwaitableTask permits CallableNonCancellable, RunnableNonCancellable {
    default void awaitTask(final Duration taskDuration) {
        Awaitility.await().pollDelay(taskDuration).until(() -> true);
    }

    default <T> T awaitTaskResult(final Duration taskDuration, T result) {
        this.awaitTask(taskDuration);
        return result;
    }
}
