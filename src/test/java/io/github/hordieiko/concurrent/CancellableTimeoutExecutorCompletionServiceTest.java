package io.github.hordieiko.concurrent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CancellableTimeoutExecutorCompletionServiceTest extends AbstractCancellableExecutorTest {

    static CancellableTimeoutCompletionService<TestCancellationReason> completionService;
    static ExecutorService executor;

    @BeforeAll
    static void beforeAll() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        completionService = new CancellableTimeoutExecutorCompletionService<>(executor);
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
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
    void timeoutCancellation() {
        final var task = new CallableCancellable();
        final var timeout = Duration.ofMillis(500);
        final var cancellableFuture = completionService.submit(task, timeout, TestCancellationReason.TIMEOUT);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.TIMEOUT);
    }

    @Test
    void manualCancellation() {
        final var task = new RunnableCancellable();
        final var timeout = Duration.ofSeconds(1);
        final var cancellableFuture = completionService.submit(task, null, timeout, TestCancellationReason.TIMEOUT);
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancellableFuture.cancel(true, TestCancellationReason.MANUAL);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.MANUAL);
    }

    @Test
    void taskCompleted() throws ExecutionException, InterruptedException {
        final var expectedResult = "expected result";
        final var timeout = Duration.ofSeconds(1);
        final var result = completionService.submit(new CallableNonCancellable<>(TASK_DURATION, expectedResult), timeout, TestCancellationReason.TIMEOUT).get();
        assertEquals(expectedResult, result);
    }
}