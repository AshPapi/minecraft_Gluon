package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Allay.class, priority = 1500)
public abstract class AllayClientFastMixin {

    @WrapOperation(
        method = "mobInteract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V"
        )
    )
    private <U> void papi$skipClientSetMemory(
            Brain<?> brain,
            MemoryModuleType<U> type,
            @Nullable U value,
            Operation<Void> original
    ) {
        if (!((Allay) (Object) this).level().isClientSide()) {
            original.call(brain, type, value);
        }
    }

    @WrapOperation(
        method = "mobInteract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;eraseMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)V"
        )
    )
    private <U> void papi$skipClientEraseMemory(
            Brain<?> brain,
            MemoryModuleType<U> type,
            Operation<Void> original
    ) {
        if (!((Allay) (Object) this).level().isClientSide()) {
            original.call(brain, type);
        }
    }
}
