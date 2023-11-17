package io.github.hordieiko.concurrent;

import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractCancellableExecutorTest {
    protected static final Duration TASK_DURATION = Duration.ofMillis(500);

    protected void testTaskCancellation(final Future<?> cancellableFuture,
                                        final CancellableTask.State<?> taskState,
                                        final TestCancellationReason reason) {
        Assertions.assertThrows(CancellationException.class, cancellableFuture::get);
        assertTrue(taskState.isCancelled());
        assertEquals(reason, taskState.getCancellationReason());
        assertTrue(cancellableFuture.isCancelled());
        assertTrue(cancellableFuture.isDone());
    }
}
