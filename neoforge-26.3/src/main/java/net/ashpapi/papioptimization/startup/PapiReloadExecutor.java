package net.ashpapi.papioptimization.startup;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PapiReloadExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gluon/ReloadExecutor");
    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);
    private static final ThreadPoolExecutor EXECUTOR = createExecutor();

    private PapiReloadExecutor() {
    }

    private static ThreadPoolExecutor createExecutor() {
        int cpus = Runtime.getRuntime().availableProcessors();
        int threads = Math.clamp(cpus - 1, 2, 8);
        ClassLoader bootLoader = PapiReloadExecutor.class.getClassLoader();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                threads,
                threads,
                15L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread t = new Thread(runnable, "Gluon-Reload-" + THREAD_ID.getAndIncrement());
                    t.setDaemon(true);
                    t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                    if (bootLoader != null) {
                        t.setContextClassLoader(bootLoader);
                    }
                    t.setUncaughtExceptionHandler((thr, ex) -> LOGGER.error("Uncaught exception in {}", thr.getName(), ex));
                    return t;
                }
        );
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    public static Executor executor() {
        return EXECUTOR;
    }
}
