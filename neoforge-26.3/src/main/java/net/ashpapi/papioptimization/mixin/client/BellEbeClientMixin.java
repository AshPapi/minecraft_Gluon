package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BellBlockEntity.class, priority = 1500)
public abstract class BellEbeClientMixin {

    @Shadow public int ticks;
    @Shadow public boolean shaking;
    @Shadow private boolean resonating;

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private static void papi$sleepIdleClientBell(
            Level level,
            BlockPos pos,
            BlockState state,
            BellBlockEntity bell,
            CallbackInfo ci
    ) {
        BellEbeClientMixin access = (BellEbeClientMixin) (Object) bell;
        if (!access.shaking && !access.resonating && access.ticks == 0) {
            PapiEbeRuntime.syncAnimatedBlockEntity(bell);
            ci.cancel();
        }
    }

    @Inject(method = "clientTick", at = @At("TAIL"))
    private static void papi$syncBellEbeOnClientTickEnd(
            Level level,
            BlockPos pos,
            BlockState state,
            BellBlockEntity bell,
            CallbackInfo ci
    ) {
        PapiEbeRuntime.syncAnimatedBlockEntity(bell);
    }

    @Inject(method = "triggerEvent", at = @At("RETURN"))
    private void papi$syncBellEbeOnEvent(int id, int type, CallbackInfoReturnable<Boolean> cir) {
        PapiEbeRuntime.syncAnimatedBlockEntity((BlockEntity) (Object) this);
    }
}
