package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Set;
import net.ashpapi.papioptimization.access.PapiNoisiumSection;
import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.ashpapi.papioptimization.worldgen.PapiWorldgenScratch;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NoiseBasedChunkGenerator.class, priority = 1500)
public abstract class NoiseBasedChunkGeneratorMixin {

    @Shadow @Final private static BlockState AIR;
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    protected abstract BlockState debugPreliminarySurfaceLevel(NoiseChunk noiseChunk, int x, int y, int z, BlockState state);

    @Unique
    private BlockState papi$defaultBlock;

    @Unique
    private int papi$seaLevel = Integer.MIN_VALUE;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$cacheSettingsFields(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, CallbackInfo ci) {
        if (settings != null && settings.isBound()) {
            NoiseGeneratorSettings val = settings.value();
            this.papi$defaultBlock = val.defaultBlock();
            this.papi$seaLevel = val.seaLevel();
        }
    }

    @Inject(method = "getSeaLevel", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetSeaLevel(CallbackInfoReturnable<Integer> cir) {
        int cached = this.papi$seaLevel;
        if (cached != Integer.MIN_VALUE) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "buildSurface", at = @At("HEAD"), cancellable = true)
    private void papi$skipEmptyBuildSurface(
            ChunkAccess chunkAccess,
            NoiseChunk noiseChunk,
            RandomState randomState,
            BiomeManager biomeManager,
            Set<Holder<Biome>> biomes,
            MaterialRule surfaceRule,
            CallbackInfo ci
    ) {
        if (!SharedConstants.DEBUG_DISABLE_SURFACE && chunkAccess.getHighestFilledSectionIndex() == -1) {
            ci.cancel();
        }
    }

    @WrapOperation(
        method = "createNoiseChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/Beardifier;forStructuresInChunk(Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/levelgen/Beardifier;"
        )
    )
    private Beardifier papi$skipEmptyBeardifier(
            StructureManager structureManager,
            ChunkPos chunkPos,
            Operation<Beardifier> original,
            @Local(argsOnly = true) ChunkAccess chunkAccess
    ) {
        if (chunkAccess != null && chunkAccess.getAllReferences().isEmpty()) {
            return Beardifier.EMPTY;
        }
        return original.call(structureManager, chunkPos);
    }

    @WrapMethod(method = "doFill")
    private void papi$fastDoFill(NoiseChunk noiseChunk, ChunkAccess chunkAccess, Operation<Void> original) {
        if (((Object) this).getClass() != NoiseBasedChunkGenerator.class) {
            original.call(noiseChunk, chunkAccess);
            return;
        }
        Heightmap oceanFloor = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Aquifer aquifer = noiseChunk.aquifer();
        boolean isNoiseAquifer = aquifer.getClass() == Aquifer.NoiseBasedAquifer.class;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        DensityVolume volume = noiseChunk.volume();
        NoiseGeneratorSettings genSettings = this.settings.value();
        DensitySampler.Bound finalDensitySampler = noiseChunk.cachingSamplers().get(genSettings.noiseRouter().finalDensity());

        LevelChunkSection[] sections = chunkAccess.getSections();
        int sectionCount = sections.length;
        boolean writeOnly = !chunkAccess.isUpgrading() && chunkAccess.getBelowZeroRetrogen() == null;
        if (writeOnly) {
            for (int i = 0; i < sectionCount; i++) {
                LevelChunkSection sec = sections[i];
                if (sec == null || !sec.hasOnlyAir() || !(sec instanceof PapiNoisiumSection)) {
                    writeOnly = false;
                    break;
                }
            }
        }

        PapiWorldgenScratch scratch = PapiWorldgenScratch.get();
        NoisiumPaletteCursor[] cursors = scratch.acquireCursors(Math.max(sectionCount, 1));
        int minSectionY = chunkAccess.getMinSectionY();

        try (ScopedDensityBuffer densityBuffer = finalDensitySampler.sampleVolume(volume)) {
            BlockState defaultBlock = this.papi$defaultBlock != null ? this.papi$defaultBlock : genSettings.defaultBlock();
            boolean debugAquifers = SharedConstants.DEBUG_AQUIFERS;
            int sizeZ = volume.sizeZ();
            int sizeX = volume.sizeX();
            int sizeY = volume.sizeY();

            for (int zIdx = 0; zIdx < sizeZ; zIdx++) {
                int blockZ = volume.blockZ(zIdx);
                for (int xIdx = 0; xIdx < sizeX; xIdx++) {
                    int blockX = volume.blockX(xIdx);
                    boolean oceanDone = false;
                    boolean surfaceDone = false;

                    for (int yIdx = sizeY - 1; yIdx >= 0; yIdx--) {
                        int blockY = volume.blockY(yIdx);
                        float density = densityBuffer.get(volume.indexUnchecked(xIdx, yIdx, zIdx));
                        BlockState state;
                        boolean scheduleFluidUpdate;
                        if (isNoiseAquifer && density > 0.0F) {
                            state = defaultBlock;
                            scheduleFluidUpdate = false;
                        } else {
                            state = aquifer.computeSubstance(blockX, blockY, blockZ, (double) density);
                            if (state == null) {
                                state = defaultBlock;
                            }
                            scheduleFluidUpdate = aquifer.shouldScheduleFluidUpdate();
                        }
                        if (debugAquifers) {
                            state = this.debugPreliminarySurfaceLevel(noiseChunk, blockX, blockY, blockZ, state);
                        }
                        if (state == AIR) {
                            continue;
                        }
                        int secIdx = (blockY >> 4) - minSectionY;
                        LevelChunkSection section = sections[secIdx];
                        int relY = blockY & 15;
                        if (writeOnly) {
                            ((PapiNoisiumSection) section).papi$setWriteOnlyNoisium(xIdx, relY, zIdx, state, cursors[secIdx]);
                        } else if (section instanceof PapiNoisiumSection ns) {
                            ns.papi$setBlockStateNoisium(xIdx, relY, zIdx, state, cursors[secIdx]);
                        } else {
                            section.setBlockState(xIdx, relY, zIdx, state, false);
                        }
                        if (!oceanDone && oceanFloor.update(xIdx, blockY, zIdx, state)) {
                            oceanDone = true;
                        }
                        if (!surfaceDone && worldSurface.update(xIdx, blockY, zIdx, state)) {
                            surfaceDone = true;
                        }
                        if (scheduleFluidUpdate && !state.getFluidState().isEmpty()) {
                            mutablePos.set(blockX, blockY, blockZ);
                            chunkAccess.markPosForPostProcessing(mutablePos);
                        }
                    }
                }
            }

            if (writeOnly) {
                for (int i = 0; i < sectionCount; i++) {
                    NoisiumPaletteCursor c = cursors[i];
                    if (c.nonEmptyCount != 0 || c.tickingBlockCount != 0 || c.fluidCount != 0 || c.tickingFluidCount != 0) {
                        ((PapiNoisiumSection) sections[i]).papi$flushNoisiumCounts(c);
                    }
                }
            }
        } finally {
            scratch.releaseCursors(cursors, Math.max(sectionCount, 1));
        }
    }
}
