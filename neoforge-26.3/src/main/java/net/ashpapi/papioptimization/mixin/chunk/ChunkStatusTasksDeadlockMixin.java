package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ChunkStatusTasks.class, priority = 1500)
public abstract class ChunkStatusTasksDeadlockMixin {

    @WrapOperation(
        method = "full",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private static CompletableFuture<ChunkAccess> papi$runInlineOnMainThread(
            Supplier<ChunkAccess> supplier,
            Executor executor,
            Operation<CompletableFuture<ChunkAccess>> original,
            @Local(argsOnly = true) WorldGenContext worldGenContext
    ) {
        if (worldGenContext != null && worldGenContext.level().getServer().isSameThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        return original.call(supplier, executor);
    }
}
