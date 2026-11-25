package net.ashpapi.papioptimization.mixin.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import net.ashpapi.papioptimization.access.PapiLevelBlockEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelChunk.class, priority = 1500)
public abstract class LevelChunkFastBlockEntityMixin extends ChunkAccess {

    public LevelChunkFastBlockEntityMixin(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory palettedContainerFactory,
        long inhabitedTime,
        LevelChunkSection[] sections,
        BlendingData blendingData
    ) {
        super(chunkPos, upgradeData, levelHeightAccessor, palettedContainerFactory, inhabitedTime, sections, blendingData);
    }

    @Shadow
    private BlockEntity promotePendingBlockEntity(BlockPos pos, CompoundTag tag) {
        return null;
    }

    @Shadow
    private BlockEntity createBlockEntity(BlockPos pos) {
        return null;
    }

    @Shadow @Final Level level;

    @Shadow @Final @Mutable
    private Map<BlockPos, ?> tickersInLevel;

    @Shadow
    public abstract void addAndRegisterBlockEntity(BlockEntity blockEntity);

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
        at = @At("RETURN")
    )
    private void papi$compactTickersMap(CallbackInfo ci) {
        if (this.tickersInLevel.isEmpty()) {
            this.tickersInLevel = new Object2ObjectOpenHashMap<>(4);
        }
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (this.level.isDebug()) {
            return;
        }
        int y = pos.getY();
        int secIdx = this.getSectionIndex(y);
        LevelChunkSection[] secs = this.sections;
        if (Integer.compareUnsigned(secIdx, secs.length) < 0) {
            LevelChunkSection sec = secs[secIdx];
            cir.setReturnValue(sec.hasOnlyAir()
                    ? Blocks.AIR.defaultBlockState()
                    : sec.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15));
            return;
        }
        cir.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
    }

    @Inject(method = "getFluidState(III)Lnet/minecraft/world/level/material/FluidState;", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetFluidState(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        int secIdx = this.getSectionIndex(y);
        LevelChunkSection[] secs = this.sections;
        if (Integer.compareUnsigned(secIdx, secs.length) < 0) {
            LevelChunkSection sec = secs[secIdx];
            cir.setReturnValue(sec.hasOnlyAir()
                    ? Fluids.EMPTY.defaultFluidState()
                    : sec.getFluidState(x & 15, y & 15, z & 15));
            return;
        }
        cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void papi$notifyBlockEpochOnStateChange(
            BlockPos pos,
            BlockState state,
            int flags,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (cir.getReturnValue() != null && this.level != null) {
            ((PapiLevelBlockEpoch) this.level).papi$bumpBlockEpoch(pos.asLong());
        }
    }

    @Inject(
        method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastGetBlockEntity(
        BlockPos pos,
        LevelChunk.EntityCreationType creationType,
        CallbackInfoReturnable<BlockEntity> cir
    ) {
        BlockEntity blockEntity = null;
        if (!this.blockEntities.isEmpty()) {
            blockEntity = this.blockEntities.get(pos);
            if (blockEntity != null && blockEntity.isRemoved()) {
                this.blockEntities.remove(pos);
                blockEntity = null;
            }
        }
        if (blockEntity == null && !this.pendingBlockEntities.isEmpty()) {
            CompoundTag tag = this.pendingBlockEntities.remove(pos);
            if (tag != null) {
                BlockEntity promoted = this.promotePendingBlockEntity(pos, tag);
                if (promoted != null) {
                    cir.setReturnValue(promoted);
                    return;
                }
            }
        }
        if (blockEntity == null && creationType == LevelChunk.EntityCreationType.IMMEDIATE) {
            blockEntity = this.createBlockEntity(pos);
            if (blockEntity != null) {
                this.addAndRegisterBlockEntity(blockEntity);
            }
        }
        cir.setReturnValue(blockEntity);
    }
}
