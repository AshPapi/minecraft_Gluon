package net.ashpapi.papioptimization.mixin.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ProtoChunk.class, priority = 1500)
public abstract class ProtoChunkFastBlockMixin extends ChunkAccess {

    public ProtoChunkFastBlockMixin(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory containerFactory,
        long inhabitedTime,
        LevelChunkSection[] sections,
        BlendingData blendingData
    ) {
        super(chunkPos, upgradeData, levelHeightAccessor, containerFactory, inhabitedTime, sections, blendingData);
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void papi$fastProtoGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        int y = pos.getY();
        if (this.isOutsideBuildHeight(y)) {
            cir.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
            return;
        }
        LevelChunkSection section = this.sections[this.getSectionIndex(y)];
        cir.setReturnValue(section.hasOnlyAir()
                ? Blocks.AIR.defaultBlockState()
                : section.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15));
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void papi$fastProtoGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        int y = pos.getY();
        if (this.isOutsideBuildHeight(y)) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        LevelChunkSection section = this.sections[this.getSectionIndex(y)];
        cir.setReturnValue(section.hasOnlyAir()
                ? Fluids.EMPTY.defaultFluidState()
                : section.getFluidState(pos.getX() & 15, y & 15, pos.getZ() & 15));
    }
}
