package net.ashpapi.papioptimization.mixin.client;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.ashpapi.papioptimization.client.PapiClientLevelLeakCleaner;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage", priority = 1500)
public abstract class ClientChunkStorageLeakMixin implements PapiClientLevelLeakCleaner {

    @Shadow
    @Final
    private AtomicReferenceArray<LevelChunk> chunks;

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientChunkCache;I)V", at = @At("RETURN"))
    private void papi$bindToChunkCache(ClientChunkCache outer, int chunkRadius, CallbackInfo ci) {
        if (outer instanceof PapiClientLevelLeakCleaner holder) {
            holder.papi$setActiveStorage(this);
        }
    }

    @Override
    public void papi$clearRetainedLevelReferences() {
        AtomicReferenceArray<LevelChunk> array = this.chunks;
        if (array != null) {
            int len = array.length();
            for (int i = 0; i < len; i++) {
                array.set(i, null);
            }
        }
    }
}
