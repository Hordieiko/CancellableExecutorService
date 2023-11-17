package io.github.hordieiko.concurrent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.github.hordieiko.concurrent.AbstractCancellableExecutorTest.TASK_DURATION;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class CancellableFutureTaskTest {

    static ExecutorService executor;

    @BeforeAll
    static void beforeAll() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
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
    void cancel() {
        final var cancellableTaskSpy = spy(new RunnableCancellable());
        final var cancellableFutureTask = new CancellableFutureTask<>(cancellableTaskSpy, null);

        testTaskCancellation(cancellableFutureTask, cancellableTaskSpy, RunnableCancellable::run,
                t -> t.cancel(true), null);
    }

    @Test
    void cancelWithReason() {
        final var cancellableTaskSpy = spy(new CallableCancellable());
        final var cancellableFutureTask = new CancellableFutureTask<>(cancellableTaskSpy);

        testTaskCancellation(cancellableFutureTask, cancellableTaskSpy, CallableCancellable::call,
                t -> t.cancel(true, TestCancellationReason.MANUAL), TestCancellationReason.MANUAL);
    }

    private static <T extends CancellableTaskState, U extends CancellableTask.CancellationReason>
    void testTaskCancellation(final CancellableFutureTask<?, U> cancellableFutureTask,
                              final T cancellableTaskSpy,
                              final Consumer<T> verifyCancellableTaskRun,
                              final Consumer<CancellableFutureTask<?, U>> cancelFutureAction,
                              final TestCancellationReason expectedReason) {
        executor.execute(cancellableFutureTask);
        await().pollDelay(TASK_DURATION).until(() -> true);
        cancelFutureAction.accept(cancellableFutureTask);

        verifyCancellableTaskRun.accept(verify(cancellableTaskSpy));
        assertTrue(cancellableTaskSpy.isCancelled());
        assertEquals(expectedReason, cancellableTaskSpy.getCancellationReason());
    }
}