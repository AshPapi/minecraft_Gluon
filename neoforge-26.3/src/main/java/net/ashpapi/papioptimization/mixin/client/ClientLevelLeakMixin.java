package net.ashpapi.papioptimization.mixin.client;

import java.util.List;
import net.ashpapi.papioptimization.client.PapiClientLevelLeakCleaner;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientLevel.class, priority = 1500)
public abstract class ClientLevelLeakMixin implements PapiClientLevelLeakCleaner {

    @Shadow
    @Final
    private ClientChunkCache chunkSource;

    @Override
    public void papi$clearRetainedLevelReferences() {
        if (this instanceof LevelLeakAccessor levelAccessor) {
            List<TickingBlockEntity> tickers = levelAccessor.papi$getBlockEntityTickers();
            if (tickers != null) {
                tickers.clear();
            }
            List<TickingBlockEntity> pending = levelAccessor.papi$getPendingBlockEntityTickers();
            if (pending != null) {
                pending.clear();
            }
        }
        if (this.chunkSource instanceof PapiClientLevelLeakCleaner cleaner) {
            cleaner.papi$clearRetainedLevelReferences();
        }
    }
}
