package com.algoverse.execution.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration for the submission judging pipeline.
 *
 * <p>The {@code judgingExecutor} is sized to handle up to 50 concurrent
 * sandbox sessions (matching {@code execution.max-concurrent}) with a generous
 * queue for burst absorption. Each thread is named {@code judging-N} for
 * easy identification in thread dumps and APM traces.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "judgingExecutor")
    public Executor judgingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("judging-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
