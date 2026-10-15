package io.github.springdbpooldoctor.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns a daemon scheduler thread so the library never requires
 * Spring scheduling to be enabled in the host application.
 */
public class PoolMonitorScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PoolMonitorScheduler.class);

    private final Runnable scanTask;
    private final long intervalMs;
    private final ScheduledExecutorService executor;

    public PoolMonitorScheduler(Runnable scanTask, long intervalMs) {
        this.scanTask = scanTask;
        this.intervalMs = intervalMs;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spring-db-pool-doctor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        executor.scheduleWithFixedDelay(() -> {
            try {
                scanTask.run();
            } catch (RuntimeException e) {
                log.warn("spring-db-pool-doctor scan failed; monitoring continues", e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
