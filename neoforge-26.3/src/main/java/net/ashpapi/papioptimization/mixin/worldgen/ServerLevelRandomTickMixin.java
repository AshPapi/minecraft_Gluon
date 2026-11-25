package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.access.PapiSectionFlags;
import net.ashpapi.papioptimization.mixin.core.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelRandomTickMixin {

    @Unique
    private static final LevelChunkSection[] PAPI$EMPTY_SECTIONS = new LevelChunkSection[0];

    @WrapOperation(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"
            )
    )
    private LevelChunkSection[] papi$fastRandomTickSections(
            LevelChunk chunk,
            Operation<LevelChunkSection[]> original,
            @Local(argsOnly = true) int tickSpeed
    ) {
        LevelChunkSection[] sections = original.call(chunk);
        if (tickSpeed <= 0 || sections.length == 0) {
            return PAPI$EMPTY_SECTIONS;
        }

        ServerLevel level = (ServerLevel) (Object) this;
        LevelAccessor acc = (LevelAccessor) this;
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        ProfilerFiller profiler = Profiler.get();
        int rand = acc.papi$getRandValue();

        for (int sectionIndex = 0; sectionIndex < sections.length; ++sectionIndex) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.isRandomlyTicking()) {
                continue;
            }

            int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
            int minYInSection = SectionPos.sectionToBlockCoord(sectionY);
            PapiSectionFlags flags = (section instanceof PapiSectionFlags sf) ? sf : null;

            for (int i = 0; i < tickSpeed; ++i) {
                rand = rand * 3 + 1013904223;
                int val = rand >> 2;
                int rx = val & 15;
                int ry = (val >> 16) & 15;
                int rz = (val >> 8) & 15;

                if (flags != null && !flags.papi$isRandomTickingAt((ry << 8) | (rz << 4) | rx)) {
                    continue;
                }

                BlockState blockState = section.getBlockState(rx, ry, rz);
                boolean blockTicks = blockState.isRandomlyTicking();
                FluidState fluidState = blockState.getFluidState();
                boolean fluidTicks = fluidState.isRandomlyTicking();

                if (blockTicks || fluidTicks) {
                    acc.papi$setRandValue(rand);
                    BlockPos pos = new BlockPos(minX + rx, minYInSection + ry, minZ + rz);
                    profiler.push("randomTick");
                    if (blockTicks) {
                        blockState.randomTick(level, pos, level.getRandom());
                    }
                    if (fluidTicks) {
                        fluidState.randomTick(level, pos, level.getRandom());
                    }
                    profiler.pop();
                    rand = acc.papi$getRandValue();
                }
            }
        }
        acc.papi$setRandValue(rand);
        return PAPI$EMPTY_SECTIONS;
    }
}
