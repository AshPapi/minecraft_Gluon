package net.ashpapi.papioptimization.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.client.ebe.PapiBannerTintSource;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockColors.class, priority = 1500)
public abstract class BlockColorsEbeMixin {

    @Inject(method = "createDefault", at = @At("RETURN"))
    private static void papi$registerBannerTints(CallbackInfoReturnable<BlockColors> cir) {
        BlockColors colors = cir.getReturnValue();
        if (colors == null) {
            return;
        }
        List<Block> banners = new ArrayList<>(32);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof AbstractBannerBlock) {
                Identifier key = BuiltInRegistries.BLOCK.getKey(block);
                if (key != null && "minecraft".equals(key.getNamespace())) {
                    banners.add(block);
                }
            }
        }
        if (!banners.isEmpty()) {
            colors.register(List.of(PapiBannerTintSource.INSTANCE), banners.toArray(new Block[0]));
        }
    }
}
