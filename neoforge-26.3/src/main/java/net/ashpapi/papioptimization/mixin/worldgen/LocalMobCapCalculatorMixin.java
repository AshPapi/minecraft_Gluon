package net.ashpapi.papioptimization.mixin.worldgen;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalMobCapCalculator.class, priority = 1500)
public abstract class LocalMobCapCalculatorMixin {

    @Shadow @Final @Mutable
    private Map<ServerPlayer, ?> playerMobCounts;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$useReferenceMap(ChunkMap chunkMap, CallbackInfo ci) {
        this.playerMobCounts = new Reference2ObjectOpenHashMap<>();
    }
}
