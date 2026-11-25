package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = EnderChestBlockEntity.class, priority = 1500)
public abstract class EnderChestEbeClientMixin {

    @Inject(method = "lidAnimateTick", at = @At("TAIL"))
    private static void papi$syncEnderChestEbeOnTick(
            Level level,
            BlockPos pos,
            BlockState state,
            EnderChestBlockEntity chest,
            CallbackInfo ci
    ) {
        PapiEbeRuntime.syncAnimatedBlockEntity(chest);
    }

    @Inject(method = "triggerEvent", at = @At("RETURN"))
    private void papi$syncEnderChestEbeOnEvent(int id, int type, CallbackInfoReturnable<Boolean> cir) {
        PapiEbeRuntime.syncAnimatedBlockEntity((BlockEntity) (Object) this);
    }
}
