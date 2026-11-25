package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.physics.PapiFluidSectionCursor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EntityFluidInteraction.class, priority = 1500)
public abstract class EntityFluidInteractionMixin {

    @WrapMethod(method = "update(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Z")
    private boolean papi$scopedFluidSectionCursor(
            Entity entity,
            Predicate<FluidType> predicate,
            Operation<Boolean> original
    ) {
        PapiFluidSectionCursor cursor = PapiFluidSectionCursor.LOCAL.get();
        cursor.reset();
        try {
            return original.call(entity, predicate);
        } finally {
            cursor.reset();
        }
    }

    @WrapOperation(
        method = "update(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState papi$fastSectionFluidState(BlockGetter getter, BlockPos pos, Operation<FluidState> original) {
        if (!(getter instanceof Level level)) {
            return original.call(getter, pos);
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int cx = x >> 4;
        int cz = z >> 4;
        int sy = y >> 4;
        PapiFluidSectionCursor cursor = PapiFluidSectionCursor.LOCAL.get();
        if (cursor.level != level || cursor.chunkX != cx || cursor.chunkZ != cz) {
            cursor.level = level;
            cursor.chunkX = cx;
            cursor.chunkZ = cz;
            cursor.secY = Integer.MIN_VALUE;
            cursor.chunk = level.getChunkSource().getChunkNow(cx, cz);
            cursor.section = null;
        }
        LevelChunk chunk = cursor.chunk;
        if (chunk == null) {
            return Fluids.EMPTY.defaultFluidState();
        }
        if (cursor.secY != sy) {
            cursor.secY = sy;
            int idx = chunk.getSectionIndexFromSectionY(sy);
            LevelChunkSection[] sections = chunk.getSections();
            cursor.section = (idx >= 0 && idx < sections.length) ? sections[idx] : null;
        }
        LevelChunkSection section = cursor.section;
        if (section == null || section.hasOnlyAir()) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return section.getFluidState(x & 15, y & 15, z & 15);
    }
}
