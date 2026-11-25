package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = EndGatewayBlock.class, priority = 1500)
public abstract class EndGatewayBlockMixin extends BaseEntityBlock {

    protected EndGatewayBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        if (PapiConfig.rawEndGatewayCulling() && adjacentState.is(Blocks.END_GATEWAY)) {
            return true;
        }
        return super.skipRendering(state, adjacentState, direction);
    }
}
