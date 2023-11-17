package io.github.hordieiko.concurrent;

enum TestCancellationReason implements CancellableTask.CancellationReason {
    TIMEOUT, MANUAL
}
