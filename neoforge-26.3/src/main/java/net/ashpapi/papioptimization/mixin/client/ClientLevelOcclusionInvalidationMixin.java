package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientLevel.class, priority = 1500)
public abstract class ClientLevelOcclusionInvalidationMixin {

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void papi$onSetBlock(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            PapiOcclusionEngine.invalidateBlockSectionAt((ClientLevel) (Object) this, pos);
        }
    }

    @Inject(method = "setServerVerifiedBlockState", at = @At("RETURN"))
    private void papi$onServerVerifiedBlock(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        PapiOcclusionEngine.invalidateBlockSectionAt((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("RETURN"))
    private void papi$onSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        PapiOcclusionEngine.invalidateSectionCoords((ClientLevel) (Object) this, sectionX, sectionY, sectionZ);
    }

    @Inject(method = "setSectionRangeDirty", at = @At("RETURN"))
    private void papi$onSectionRangeDirty(
            int minY,
            int minX,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            CallbackInfo ci
    ) {
        PapiOcclusionEngine.onClientLightChange();
    }

    @Inject(method = "unload", at = @At("RETURN"))
    private void papi$invalidateOcclusionOnChunkUnload(LevelChunk chunk, CallbackInfo ci) {
        PapiOcclusionEngine.invalidateLoadedChunkSections(chunk);
    }
}
