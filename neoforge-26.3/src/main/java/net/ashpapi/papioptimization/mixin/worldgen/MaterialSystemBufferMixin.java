package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.ashpapi.papioptimization.worldgen.PapiDensityBufferArena;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MaterialSystem.class, priority = 1500)
public abstract class MaterialSystemBufferMixin {

    @WrapMethod(method = "buildSurface")
    private void papi$scopedArenaBuildSurface(
        RandomState randomState,
        BiomeManager biomeManager,
        WorldGenerationContext context,
        ChunkAccess chunk,
        NoiseChunk noiseChunk,
        MaterialRule rule,
        Set<Holder<Biome>> possibleBiomes,
        Operation<Void> original
    ) {
        PapiDensityBufferArena.enterScope();
        try {
            original.call(randomState, biomeManager, context, chunk, noiseChunk, rule, possibleBiomes);
        } finally {
            PapiDensityBufferArena.exitScope();
        }
    }
}
