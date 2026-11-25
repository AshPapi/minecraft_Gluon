package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.client.BlockEntityTypeRendererCache;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockEntityType.class, priority = 1500)
public abstract class BlockEntityTypeMixin implements BlockEntityTypeRendererCache {

    @Unique
    private volatile @Nullable Object papi$rendererSlot;

    @Unique
    private volatile byte papi$cullWhitelistState;

    @Override
    public @Nullable Object papi$getRendererSlot() {
        return this.papi$rendererSlot;
    }

    @Override
    public void papi$setRenderer(@Nullable BlockEntityRenderer<?, ?> renderer) {
        this.papi$rendererSlot = renderer != null ? renderer : PAPI$NO_RENDERER;
    }

    @Override
    public void papi$resetWhitelistCache() {
        this.papi$cullWhitelistState = 0;
    }

    @Override
    public boolean papi$isCullWhitelisted() {
        byte st = this.papi$cullWhitelistState;
        if (st != 0) {
            return st == 2;
        }
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey((BlockEntityType<?>) (Object) this);
        boolean wl = id != null && PapiConfig.blockEntityCullWhitelistSet().contains(id.toString());
        this.papi$cullWhitelistState = (byte) (wl ? 2 : 1);
        return wl;
    }
}
