package io.github.hordieiko.concurrent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CancellableThreadPoolExecutorServiceTest extends AbstractCancellableExecutorTest {

    static CancellableExecutorService<TestCancellationReason> executor;

    @BeforeAll
    static void beforeAll() {
        executor = new CancellableThreadPoolExecutorService<>(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    }

    @AfterAll
    static void afterEach() throws InterruptedException {
        executor.shutdown();
        if (!executor.isShutdown())
            throw new IllegalStateException("Executor is not shut down");
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        if (!executor.awaitTermination(2, TimeUnit.SECONDS))
            throw new IllegalStateException("Executor is not terminated");
        if (!executor.isTerminated())
            throw new IllegalStateException("Executor is not terminated");
    }

    @Test
    void submitRunnable() throws ExecutionException, InterruptedException {
        final var cancellableFuture = executor.submit(new RunnableNonCancellable(TASK_DURATION));
        final var nullValue = cancellableFuture.get();
        assertNull(nullValue);
        assertFalse(cancellableFuture.isCancelled());
    }

    @Test
    void submitRunnableWithResult() throws ExecutionException, InterruptedException {
        final var expectedResult = "expected result";
        final var cancellableFuture = executor.submit(new RunnableNonCancellable(TASK_DURATION), expectedResult);
        final var result = cancellableFuture.get();
        assertEquals(expectedResult, result);
        assertFalse(cancellableFuture.isCancelled());
    }

    @Test
    void submitCallable() throws ExecutionException, InterruptedException {
        final var expectedResult = "expected result";
        final var cancellableFuture = executor.submit(new CallableNonCancellable<>(TASK_DURATION, expectedResult));
        final var result = cancellableFuture.get();
        assertEquals(expectedResult, result);
        assertFalse(cancellableFuture.isCancelled());
    }

    @Test
    void cancelRunnable() {
        final var task = new RunnableCancellable();
        final var cancellableFuture = executor.submit(task);
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancellableFuture.cancel(true, TestCancellationReason.MANUAL);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.MANUAL);
    }

    @Test
    void cancelRunnableWithResult() {
        final var task = new RunnableCancellable();
        final var cancellableFuture = executor.submit(task, "unexpected result");
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancellableFuture.cancel(true, TestCancellationReason.MANUAL);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.MANUAL);
    }

    @Test
    void cancelCallable() {
        final var task = new CallableCancellable();
        final var cancellableFuture = executor.submit(task);
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancellableFuture.cancel(true, TestCancellationReason.MANUAL);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.MANUAL);
    }
}