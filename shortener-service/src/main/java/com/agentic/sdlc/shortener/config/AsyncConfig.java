package com.agentic.sdlc.shortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Excluded under the "test" profile (see {@code src/test/resources/application.yml}) rather
 * than overridden with a test-only bean of the same name: an earlier attempt to override
 * {@code clickTrackingExecutor} via {@code allow-bean-definition-overriding} silently picked
 * this production bean anyway (Spring's "last definition registered wins" rule is an
 * implementation detail, not something to depend on), so {@code @Async} calls in tests kept
 * running for real on the production thread pool. Without {@code @EnableAsync} active at all
 * under "test", {@code @Async} is simply inert -- {@code recordClickAsync} runs as an ordinary
 * synchronous call on the calling (test) thread, which is also what makes it participate
 * correctly in the test's {@code @Transactional} rollback.
 */
@Configuration
@EnableAsync
@Profile("!test")
public class AsyncConfig {

    /**
     * A small, bounded pool specifically for click tracking -- deliberately not the JVM-wide
     * common ForkJoinPool or an unbounded executor, so a burst of redirects can't spawn
     * unbounded threads. Click writes are not latency-sensitive; a queue backing up under load
     * delays analytics, not redirects.
     */
    @Bean(name = "clickTrackingExecutor")
    public TaskExecutor clickTrackingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("click-tracking-");
        executor.initialize();
        return executor;
    }
}
