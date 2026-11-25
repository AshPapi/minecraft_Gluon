package net.ashpapi.papioptimization.mixin.threading;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Util.class, priority = 1500)
public abstract class UtilWorkerPriorityMixin {

    @WrapMethod(method = "maxAllowedExecutorThreads")
    private static int papi$maxAllowedExecutorThreads(Operation<Integer> original) {
        if (System.getProperty("max.bg.threads") != null) {
            return original.call();
        }
        int cpus = Runtime.getRuntime().availableProcessors();
        return cpus > 4 ? Math.clamp(cpus - 2, 2, 10) : Math.max(1, cpus - 1);
    }

    @WrapOperation(
        method = "makeExecutor",
        at = @At(
            value = "NEW",
            target = "(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)Ljava/util/concurrent/ForkJoinPool;"
        )
    )
    private static ForkJoinPool papi$lowerWorkerPriorityAndKeepAlive(
            int parallelism,
            ForkJoinPool.ForkJoinWorkerThreadFactory factory,
            Thread.UncaughtExceptionHandler handler,
            boolean asyncMode,
            Operation<ForkJoinPool> original
    ) {
        ForkJoinPool.ForkJoinWorkerThreadFactory wrappedFactory = pool -> {
            ForkJoinWorkerThread thread = factory.newThread(pool);
            if (thread != null) {
                thread.setPriority(Thread.NORM_PRIORITY - 1);
            }
            return thread;
        };
        return new ForkJoinPool(
                parallelism,
                wrappedFactory,
                handler,
                asyncMode,
                0,
                0x7fff,
                1,
                null,
                365L,
                TimeUnit.DAYS
        );
    }

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "sequence")
    private static <V> CompletableFuture<List<V>> papi$fastSequence(
            List<? extends CompletableFuture<V>> futures,
            Operation<CompletableFuture<List<V>>> original
    ) {
        int size = futures.size();
        if (size == 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (size == 1) {
            CompletableFuture<V> single = futures.get(0);
            if (single.isDone() && !single.isCompletedExceptionally()) {
                return CompletableFuture.completedFuture(Collections.singletonList(single.join()));
            }
            return single.thenApply(Collections::singletonList);
        }
        Object[] results = new Object[size];
        AtomicInteger remaining = new AtomicInteger(size);
        CompletableFuture<List<V>> combined = new CompletableFuture<>();
        for (int i = 0; i < size; i++) {
            final int idx = i;
            CompletableFuture<V> f = futures.get(i);
            if (f.isDone() && !f.isCompletedExceptionally()) {
                results[idx] = f.join();
                if (remaining.decrementAndGet() == 0) {
                    combined.complete((List<V>) Arrays.asList(results));
                }
            } else {
                f.whenComplete((val, ex) -> {
                    if (ex != null) {
                        combined.completeExceptionally(ex);
                    } else {
                        results[idx] = val;
                        if (remaining.decrementAndGet() == 0) {
                            combined.complete((List<V>) Arrays.asList(results));
                        }
                    }
                });
            }
        }
        return combined;
    }
}
