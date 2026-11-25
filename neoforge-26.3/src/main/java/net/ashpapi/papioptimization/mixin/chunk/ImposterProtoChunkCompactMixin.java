package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ImposterProtoChunk.class, priority = 1500)
public abstract class ImposterProtoChunkCompactMixin extends ProtoChunk {

    @Shadow
    @Final
    private LevelChunk wrapped;

    @Shadow
    @Final
    private boolean allowWrites;

    private ImposterProtoChunkCompactMixin(
            ChunkPos chunkPos,
            UpgradeData upgradeData,
            LevelHeightAccessor levelHeightAccessor,
            PalettedContainerFactory factory,
            @Nullable BlendingData blendingData
    ) {
        super(chunkPos, upgradeData, levelHeightAccessor, factory, blendingData);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$shareWrappedStorage(LevelChunk wrapped, boolean allowWrites, CallbackInfo ci) {
        LevelChunkSection[] wrappedSections = wrapped.getSections();
        int copyLen = Math.min(this.sections.length, wrappedSections.length);
        System.arraycopy(wrappedSections, 0, this.sections, 0, copyLen);
        this.skyLightSources = wrapped.getSkyLightSources();
    }

    @ModifyExpressionValue(
        method = "getBlockEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
        )
    )
    private @Nullable BlockEntity papi$guardReadOnlyBlockEntity(@Nullable BlockEntity entity, BlockPos pos) {
        if (!this.allowWrites && entity != null && entity.getLevel() != null) {
            return null;
        }
        return entity;
    }
}
