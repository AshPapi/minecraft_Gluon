package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientChunkCache.class, priority = 1500)
public abstract class ClientChunkCacheOcclusionMixin {

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void papi$onReplaceChunkPacket(
            int x,
            int z,
            ClientboundLevelChunkPacketData packetData,
            CallbackInfoReturnable<LevelChunk> cir
    ) {
        PapiOcclusionEngine.invalidateLoadedChunkSections(cir.getReturnValue());
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void papi$invalidateOcclusionOnChunkDrop(ChunkPos pos, CallbackInfo ci) {
        if (pos != null) {
            PapiOcclusionEngine.onClientBlockOrChunkChange();
            PapiOcclusionEngine.onClientLightChange();
        }
    }

    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void papi$invalidateParticleLightOnUpdate(LightLayer type, SectionPos pos, CallbackInfo ci) {
        PapiOcclusionEngine.onClientLightChange();
    }
}
