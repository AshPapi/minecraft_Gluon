package net.ashpapi.papioptimization.client.ebe;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiBannerTintSource implements BlockTintSource {

    public static final BlockTintSource INSTANCE = new PapiBannerTintSource();

    private PapiBannerTintSource() {
    }

    @Override
    public int color(BlockState state) {
        if (state.getBlock() instanceof AbstractBannerBlock banner) {
            return banner.getColor().getTextureDiffuseColor();
        }
        return -1;
    }

    @Override
    public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return -1;
    }
}
