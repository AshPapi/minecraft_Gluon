package net.ashpapi.papioptimization.startup;

import com.mojang.datafixers.DataFixerUpper;
import com.mojang.datafixers.types.Type;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PapiLazyDfuBridge {

    private static final long IDLE_EVICT_NANOS = 60_000_000_000L;
    private static final AtomicLong LAST_ACTIVITY_NANOS = new AtomicLong(0L);
    private static final AtomicBoolean SWEEPER_ACTIVE = new AtomicBoolean(false);
    private static volatile Object trackedFixer;

    private static final Map<?, ?> REWRITE_CACHE;
    private static final Map<?, CompletableFuture<?>> PENDING_REWRITE_CACHE;
    private static final VarHandle RULES_HANDLE;

    static {
        Map<?, ?> rewrite = null;
        Map<?, CompletableFuture<?>> pending = null;
        VarHandle rulesVh = null;
        try {
            MethodHandles.Lookup typeLookup = MethodHandles.privateLookupIn(Type.class, MethodHandles.lookup());
            VarHandle rcVh = typeLookup.findStaticVarHandle(Type.class, "REWRITE_CACHE", Map.class);
            VarHandle prcVh = typeLookup.findStaticVarHandle(Type.class, "PENDING_REWRITE_CACHE", Map.class);
            rewrite = (Map<?, ?>) rcVh.get();
            @SuppressWarnings("unchecked")
            Map<?, CompletableFuture<?>> p = (Map<?, CompletableFuture<?>>) prcVh.get();
            pending = p;
        } catch (Throwable ignored) {
        }
        try {
            MethodHandles.Lookup dfuLookup = MethodHandles.privateLookupIn(DataFixerUpper.class, MethodHandles.lookup());
            rulesVh = dfuLookup.findVarHandle(
                    DataFixerUpper.class,
                    "rules",
                    it.unimi.dsi.fastutil.longs.Long2ObjectMap.class
            );
        } catch (Throwable ignored) {
        }
        REWRITE_CACHE = rewrite;
        PENDING_REWRITE_CACHE = pending;
        RULES_HANDLE = rulesVh;
    }

    private PapiLazyDfuBridge() {
    }

    public static void registerFixer(Object fixerInstance) {
        if (fixerInstance != null) {
            trackedFixer = fixerInstance;
        }
    }

    public static void recordLegacyUpdate(Object fixerInstance) {
        if (fixerInstance != null && trackedFixer == null) {
            trackedFixer = fixerInstance;
        }
        LAST_ACTIVITY_NANOS.set(System.nanoTime());
        if (SWEEPER_ACTIVE.compareAndSet(false, true)) {
            Thread sweeper = Thread.ofPlatform()
                    .name("Gluon-DFU-CacheEvictor")
                    .daemon(true)
                    .priority(Thread.MIN_PRIORITY)
                    .unstarted(PapiLazyDfuBridge::runIdleEvictionLoop);
            sweeper.start();
        }
    }

    private static void runIdleEvictionLoop() {
        try {
            while (true) {
                long last = LAST_ACTIVITY_NANOS.get();
                long elapsed = System.nanoTime() - last;
                long remainingNanos = IDLE_EVICT_NANOS - elapsed;
                if (remainingNanos <= 0L) {
                    evictCachesNow();
                    return;
                }
                long sleepMillis = Math.max(1000L, remainingNanos / 1_000_000L);
                Thread.sleep(Math.min(sleepMillis, 15_000L));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            SWEEPER_ACTIVE.set(false);
        }
    }

    public static void evictCachesNow() {
        try {
            if (REWRITE_CACHE != null) {
                REWRITE_CACHE.clear();
            }
            if (PENDING_REWRITE_CACHE != null) {
                PENDING_REWRITE_CACHE.entrySet().removeIf(entry -> {
                    CompletableFuture<?> f = entry.getValue();
                    return f == null || f.isDone();
                });
            }
            Object fixer = trackedFixer;
            if (fixer instanceof DataFixerUpper && RULES_HANDLE != null) {
                it.unimi.dsi.fastutil.longs.Long2ObjectMap<?> rules =
                        (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) RULES_HANDLE.get(fixer);
                if (rules != null) {
                    synchronized (rules) {
                        rules.clear();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
