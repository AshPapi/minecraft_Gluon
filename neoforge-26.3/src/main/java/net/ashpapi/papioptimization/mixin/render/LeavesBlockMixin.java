package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiCullableBlockState;
import net.ashpapi.papioptimization.client.culling.PapiBlockCullingCache;
import net.ashpapi.papioptimization.config.LeavesCullingMode;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LeavesBlock.class, priority = 1500)
public abstract class LeavesBlockMixin extends Block {

    @Shadow
    private static volatile boolean cutoutLeaves;

    public LeavesBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "setCutoutLeaves", at = @At("RETURN"))
    private static void papi$syncCutoutLeavesFlag(boolean cutout, CallbackInfo ci) {
        PapiBlockCullingCache.setCutoutLeaves(cutout);
    }

    @WrapMethod(method = "skipRendering")
    private boolean papi$cullLeafFaces(
            BlockState state,
            BlockState adjacentState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (adjacentState.getBlock() instanceof LeavesBlock) {
            int sFlags = ((PapiCullableBlockState) state).papi$getCullFlags();
            if ((sFlags & PapiBlockCullingCache.FLAG_READY) != 0) {
                int aFlags = ((PapiCullableBlockState) adjacentState).papi$getCullFlags();
                if (((sFlags & aFlags) & PapiBlockCullingCache.FLAG_IS_LEAVES) == 0) {
                    return original.call(state, adjacentState, direction);
                }
                if (((sFlags ^ aFlags) & PapiBlockCullingCache.FLAG_WATERLOGGED) != 0) {
                    return original.call(state, adjacentState, direction);
                }
            } else {
                boolean w1 = state.getValueOrElse(LeavesBlock.WATERLOGGED, Boolean.FALSE);
                boolean w2 = adjacentState.getValueOrElse(LeavesBlock.WATERLOGGED, Boolean.FALSE);
                if (w1 != w2) {
                    return original.call(state, adjacentState, direction);
                }
            }
            if (!cutoutLeaves || PapiConfig.rawSolidLeaves()) {
                return true;
            }
            LeavesCullingMode mode = PapiConfig.rawLeavesCullingMode();
            if (mode == LeavesCullingMode.FAST) {
                return true;
            }
            if (mode == LeavesCullingMode.VERTICAL && direction.getAxis() == Direction.Axis.Y) {
                return true;
            }
            if (mode == LeavesCullingMode.STATE) {
                int sDist = state.getValueOrElse(LeavesBlock.DISTANCE, 7);
                int aDist = adjacentState.getValueOrElse(LeavesBlock.DISTANCE, 7);
                if (aDist > 0 && aDist < sDist) {
                    return true;
                }
            }
        }
        return original.call(state, adjacentState, direction);
    }
}
