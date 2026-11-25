package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.ai.memory.MemorySlot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MemorySlot.class, priority = 1500)
public abstract class MemorySlotFastMixin<T> {

    @Shadow
    private @Nullable T value;

    @Shadow
    private long timeToLive;

    @Inject(method = "set(Ljava/lang/Object;J)V", at = @At("RETURN"))
    private void papi$normalizeEmptySlotTtl(T val, long ttl, CallbackInfo ci) {
        if (this.value == null) {
            this.timeToLive = Long.MAX_VALUE;
        }
    }
}
