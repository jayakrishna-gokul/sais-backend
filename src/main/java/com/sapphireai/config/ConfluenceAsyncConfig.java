package com.sapphireai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous Confluence sync operations.
 * Uses Spring-managed thread pool to avoid thread leaks and ensure proper cleanup.
 */
@Configuration
@EnableAsync
public class ConfluenceAsyncConfig {

    /**
     * Thread pool for Confluence sync jobs.
     * - Core pool: 2 threads (minimum active)
     * - Max pool: 5 threads (scales up under load)
     * - Queue: 100 pending tasks
     * - Rejection: CallerRuns (blocks caller if queue full)
     */
    @Bean(name = "confluenceSyncExecutor")
    public Executor confluenceSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("confluence-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}