package com.knowledge.platform.app.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Bounded in-process execution for the jobs module. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * The executor every background job runs on.
     *
     * <p>The default {@code AbortPolicy} is kept deliberately. When the bounded queue fills, the job
     * queue records a rejection and logs it -- far more diagnosable than a caller-runs policy, which
     * would silently block an HTTP thread on embedding generation and turn a queueing problem into a
     * mysterious latency problem.
     */
    @Bean
    public AsyncTaskExecutor jobTaskExecutor(JobExecutionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("kp-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.awaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    /** Re-dispatches retries after a backoff delay. Not for long-running work. */
    @Bean
    public TaskScheduler jobTaskScheduler(JobExecutionProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.schedulerPoolSize());
        scheduler.setThreadNamePrefix("kp-job-retry-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    /** Backs {@code @Async} with the same bounded pool rather than a second, unbounded one. */
    @Bean
    public Executor applicationTaskExecutor(AsyncTaskExecutor jobTaskExecutor) {
        return jobTaskExecutor;
    }
}
