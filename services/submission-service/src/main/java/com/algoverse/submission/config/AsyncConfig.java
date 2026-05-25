package com.algoverse.submission.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async thread-pool executor configuration for background grading tasks.
 *
 * <p>Pool sizing is driven by {@code application.yml} under the {@code async.executor}
 * namespace so that it can be tuned per environment without code changes.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${async.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.executor.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${async.executor.queue-capacity:100}")
    private int queueCapacity;

    /**
     * Named executor used by {@code @Async("submissionExecutor")}.
     * Rejections are logged; the submission will be marked RUNTIME_ERROR
     * by the caller's catch block.
     */
    @Bean("submissionExecutor")
    public Executor submissionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("sub-grade-");
        executor.setRejectedExecutionHandler((runnable, pool) ->
                log.error("AsyncConfig: submission grading task rejected — pool exhausted " +
                          "(active={}, queue={})", pool.getActiveCount(), pool.getQueue().size())
        );
        executor.initialize();

        log.info("AsyncConfig: submissionExecutor initialised core={} max={} queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
