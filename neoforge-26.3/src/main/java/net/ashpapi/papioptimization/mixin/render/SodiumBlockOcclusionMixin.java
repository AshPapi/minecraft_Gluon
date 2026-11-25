package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", priority = 1500, remap = false)
public abstract class SodiumBlockOcclusionMixin {

    @Shadow(remap = false)
    protected BlockAndTintGetter level;

    @Shadow(remap = false)
    protected BlockState state;

    @Shadow(remap = false)
    protected BlockPos pos;

    @Inject(
            method = "shouldDrawSide(Lnet/minecraft/core/Direction;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;skipRendering(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void papi$sodiumPositionalLeavesCulling(
            Direction face,
            CallbackInfoReturnable<Boolean> cir,
            @Local(ordinal = 0) BlockState adjState
    ) {
        BlockState selfState = this.state;
        BlockPos selfPos = this.pos;
        BlockAndTintGetter getter = this.level;
        if (getter != null && selfPos != null && selfState != null && adjState != null
                && PapiBlockCullingCache.needsPositionalLeafCheck(selfState, adjState)) {
            byte res = PapiBlockCullingCache.checkPositionalLeaves(
                    getter,
                    selfPos.getX(),
                    selfPos.getY(),
                    selfPos.getZ(),
                    selfState,
                    adjState,
                    face
            );
            if (res == 0) {
                cir.setReturnValue(false);
            } else if (res == 1) {
                cir.setReturnValue(true);
            }
        }
    }
}
