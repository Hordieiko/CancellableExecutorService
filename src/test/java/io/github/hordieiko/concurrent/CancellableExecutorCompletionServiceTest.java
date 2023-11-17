package io.github.hordieiko.concurrent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CancellableExecutorCompletionServiceTest extends AbstractCancellableExecutorTest {

    static CancellableExecutorCompletionService<TestCancellationReason> completionService;
    static ExecutorService executor;

    @BeforeAll
    static void beforeAll() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        completionService = new CancellableExecutorCompletionService<>(executor);
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
    void cancelRunnable() {
        final var task = new RunnableCancellable();
        final var cancellableFuture = completionService.submit(task);
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancellableFuture.cancel(true, TestCancellationReason.MANUAL);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.MANUAL);
    }

    @Test
    void cancelRunnableWithResult() {
        final var task = new RunnableCancellable();
        final var cancellableFuture = completionService.submit(task, "unexpected result");
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancellableFuture.cancel(true, TestCancellationReason.MANUAL);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.MANUAL);
    }

    @Test
    void cancelCallable() {
        final var task = new CallableCancellable();
        final var cancellableFuture = completionService.submit(task);
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancellableFuture.cancel(true, TestCancellationReason.MANUAL);
        testTaskCancellation(cancellableFuture, task, TestCancellationReason.MANUAL);
    }

    @Test
    void taskCompleted() throws ExecutionException, InterruptedException {
        final var expectedResult = "expected result";
        final var result = completionService.submit(new CallableNonCancellable<>(TASK_DURATION, expectedResult)).get();
        assertEquals(expectedResult, result);
    }

}