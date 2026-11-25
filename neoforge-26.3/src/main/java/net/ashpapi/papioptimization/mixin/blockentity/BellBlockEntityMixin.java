package net.ashpapi.papioptimization.mixin.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BellBlockEntity.class, priority = 1500)
public abstract class BellBlockEntityMixin {

    @Shadow public int ticks;
    @Shadow public boolean shaking;
    @Shadow private boolean resonating;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void papi$sleepIdleServerBell(
            Level level, BlockPos pos, BlockState state, BellBlockEntity bell, CallbackInfo ci) {
        BellBlockEntityMixin access = (BellBlockEntityMixin) (Object) bell;
        if (!access.shaking && !access.resonating && access.ticks == 0) {
            ci.cancel();
        }
    }
}
