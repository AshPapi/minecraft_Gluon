package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.ashpapi.papioptimization.config.LeavesCullingMode;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = MangroveRootsBlock.class, priority = 1500)
public abstract class MangroveRootsBlockMixin {

    @WrapMethod(method = "skipRendering")
    private boolean papi$cullMangroveRootsWhenConfigured(
            BlockState state,
            BlockState neighborState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (PapiConfig.rawIncludeMangroveRoots()
                && direction.getAxis().isHorizontal()
                && neighborState.getBlock() instanceof MangroveRootsBlock) {
            boolean w1 = state.getValueOrElse(MangroveRootsBlock.WATERLOGGED, Boolean.FALSE);
            boolean w2 = neighborState.getValueOrElse(MangroveRootsBlock.WATERLOGGED, Boolean.FALSE);
            if (w1 == w2 && (!PapiBlockCullingCache.isCutoutLeaves()
                    || PapiConfig.rawSolidLeaves()
                    || PapiConfig.rawLeavesCullingMode() == LeavesCullingMode.FAST)) {
                return true;
            }
        }
        return original.call(state, neighborState, direction);
    }
}
