package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = PowderSnowBlock.class, priority = 1500)
public abstract class PowderSnowBlockMixin {

    @WrapMethod(method = "skipRendering")
    private boolean papi$cullPowderSnowAgainstOpaque(
            BlockState state,
            BlockState adjacentState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (adjacentState == state
                || (adjacentState.isSolidRender()
                        && adjacentState.canOcclude()
                        && adjacentState.getRenderShape() == RenderShape.MODEL)) {
            return true;
        }
        return original.call(state, adjacentState, direction);
    }
}
