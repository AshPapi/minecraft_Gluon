package net.ashpapi.papioptimization.mixin.entity.villager;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SleepInBed.class, priority = 1500)
public abstract class SleepInBedSyncMixin {

    @Inject(
            method = "checkExtraStartConditions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            ),
            cancellable = true
    )
    private void papi$preventUnloadedBedSyncLoad(
            ServerLevel level,
            LivingEntity owner,
            CallbackInfoReturnable<Boolean> cir,
            @Local GlobalPos homePos
    ) {
        BlockPos pos = homePos.pos();
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            cir.setReturnValue(false);
        }
    }
}
