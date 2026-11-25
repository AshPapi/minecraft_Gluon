package net.ashpapi.papioptimization.mixin.chunk;

import java.util.List;
import java.util.Queue;
import net.ashpapi.papioptimization.core.OrderedTickBucketQueue;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelChunkTicks.class, priority = 1500)
public abstract class LevelChunkTicksFastMixin<T> {

    @Shadow @Final @Mutable
    private Queue<ScheduledTick<T>> tickQueue;

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void papi$initFastQueueDefault(CallbackInfo ci) {
        if (this.tickQueue.isEmpty()) {
            this.tickQueue = new OrderedTickBucketQueue<>();
        }
    }

    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("RETURN"))
    private void papi$initFastQueueFromList(List<SavedTick<T>> pending, CallbackInfo ci) {
        if (this.tickQueue.isEmpty()) {
            this.tickQueue = new OrderedTickBucketQueue<>();
        }
    }
}
