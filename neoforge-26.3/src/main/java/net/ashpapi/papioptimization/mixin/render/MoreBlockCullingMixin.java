package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Block.class, priority = 1500)
public abstract class MoreBlockCullingMixin {

    @WrapMethod(method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z")
    private static boolean papi$positionalLeafAndFaceCulling(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            BlockState adjacentState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (!original.call(level, pos, state, adjacentState, direction)) {
            return false;
        }
        if (PapiBlockCullingCache.needsPositionalLeafCheck(state, adjacentState)) {
            byte res = PapiBlockCullingCache.checkPositionalLeaves(
                    level,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    state,
                    adjacentState,
                    direction
            );
            if (res == 0) {
                return false;
            }
        }
        return true;
    }
}
