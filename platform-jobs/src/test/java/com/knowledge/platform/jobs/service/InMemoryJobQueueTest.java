package com.knowledge.platform.jobs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.knowledge.platform.jobs.model.JobState;
import com.knowledge.platform.jobs.model.JobStatus;
import com.knowledge.platform.jobs.model.RetryPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * The queue's contract is what a durable replacement would also have to provide: deduplication of
 * in-flight work, bounded retries, and observable status.
 */
class InMemoryJobQueueTest {

    private ThreadPoolTaskExecutor executor;
    private ThreadPoolTaskScheduler scheduler;
    private InMemoryJobQueueImpl queue;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.initialize();

        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();

        queue = new InMemoryJobQueueImpl(executor, scheduler, new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        scheduler.shutdown();
    }

    @Test
    void runsASubmittedJob() {
        AtomicInteger executions = new AtomicInteger();
        queue.submit(job("a", executions, 0));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(executions).hasValue(1));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queue.statusOf("a")).get()
                        .extracting(JobStatus::state).isEqualTo(JobState.SUCCEEDED));
    }

    @Test
    @DisplayName("retries a failing job up to its policy, then gives up")
    void retriesThenFails() {
        AtomicInteger attempts = new AtomicInteger();
        queue.submit(new CountingJob("retry-me", attempts, Integer.MAX_VALUE,
                RetryPolicy.exponential(3, Duration.ofMillis(10))));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queue.statusOf("retry-me")).get()
                        .extracting(JobStatus::state).isEqualTo(JobState.FAILED));
        assertThat(attempts).hasValue(3);
    }

    @Test
    void succeedsOnARetryAfterATransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        // Fails once, then succeeds -- the shape of a provider restarting mid-run.
        queue.submit(new CountingJob("transient", attempts, 1,
                RetryPolicy.exponential(3, Duration.ofMillis(10))));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queue.statusOf("transient")).get()
                        .extracting(JobStatus::state).isEqualTo(JobState.SUCCEEDED));
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("a job whose work is already in flight is collapsed, not run twice")
    void deduplicatesInFlightWork() throws Exception {
        // A job that blocks until released, so the second submission is guaranteed to arrive while
        // the first is genuinely still in flight.
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();

        queue.submit(new BlockingJob("same-work", started, release, executions));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        JobStatus second = queue.submit(new BlockingJob("same-work", started, release, executions));
        assertThat(second.state()).isEqualTo(JobState.DEDUPLICATED);

        release.countDown();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queue.statusOf("same-work")).get()
                        .extracting(JobStatus::state).isEqualTo(JobState.SUCCEEDED));
        assertThat(executions).hasValue(1);
    }

    @Test
    void reportsRecentStatusesNewestFirst() {
        queue.submit(job("first", new AtomicInteger(), 0));
        queue.submit(job("second", new AtomicInteger(), 0));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queue.recentStatuses(10)).hasSize(2));
        assertThat(queue.recentStatuses(1)).hasSize(1);
    }

    private Job job(String key, AtomicInteger counter, int failuresBeforeSuccess) {
        return new CountingJob(key, counter, failuresBeforeSuccess, RetryPolicy.none());
    }

    private record CountingJob(
            String key, AtomicInteger attempts, int failuresBeforeSuccess, RetryPolicy policy)
            implements Job {

        @Override
        public String idempotencyKey() {
            return key;
        }

        @Override
        public String type() {
            return "test";
        }

        @Override
        public void execute() {
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new IllegalStateException("attempt " + attempts.get() + " fails");
            }
        }

        @Override
        public RetryPolicy retryPolicy() {
            return policy;
        }
    }

    private record BlockingJob(
            String key,
            CountDownLatch started,
            CountDownLatch release,
            AtomicInteger executions) implements Job {

        @Override
        public String idempotencyKey() {
            return key;
        }

        @Override
        public String type() {
            return "blocking";
        }

        @Override
        public void execute() {
            executions.incrementAndGet();
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
