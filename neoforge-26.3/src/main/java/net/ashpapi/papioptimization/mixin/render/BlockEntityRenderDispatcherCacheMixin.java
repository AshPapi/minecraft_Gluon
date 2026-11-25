package net.ashpapi.papioptimization.mixin.render;

import java.util.Map;
import net.ashpapi.papioptimization.client.BlockEntityTypeRendererCache;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockEntityRenderDispatcher.class, priority = 1500)
public abstract class BlockEntityRenderDispatcherCacheMixin {

    @Shadow
    private Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> renderers;

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void papi$populateBlockEntityTypeRendererCache(ResourceManager resourceManager, CallbackInfo ci) {
        PapiClientCaches.invalidateAll();
        Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> map = this.renderers;
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            BlockEntityTypeRendererCache cache = (BlockEntityTypeRendererCache) type;
            cache.papi$resetWhitelistCache();
            cache.papi$setRenderer(map != null ? map.get(type) : null);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(
        method = "getRenderer(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void papi$fastGetBlockEntityRenderer(
            E blockEntity, CallbackInfoReturnable<@Nullable BlockEntityRenderer<E, S>> cir) {
        BlockEntityType<?> type = blockEntity.getType();
        BlockEntityTypeRendererCache cache = (BlockEntityTypeRendererCache) type;
        Object slot = cache.papi$getRendererSlot();
        if (slot != null) {
            cir.setReturnValue(slot == BlockEntityTypeRendererCache.PAPI$NO_RENDERER ? null : (BlockEntityRenderer<E, S>) slot);
            return;
        }
        Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> map = this.renderers;
        if (map == null) {
            cir.setReturnValue(null);
            return;
        }
        BlockEntityRenderer<?, ?> r = map.get(type);
        cache.papi$setRenderer(r);
        cir.setReturnValue((BlockEntityRenderer<E, S>) r);
    }

    @SuppressWarnings("unchecked")
    @Inject(
        method = "getRenderer(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;)Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void papi$fastGetStateRenderer(
            S state, CallbackInfoReturnable<@Nullable BlockEntityRenderer<E, S>> cir) {
        BlockEntityType<?> type = state.blockEntityType;
        if (type != null) {
            BlockEntityTypeRendererCache cache = (BlockEntityTypeRendererCache) type;
            Object slot = cache.papi$getRendererSlot();
            if (slot != null) {
                cir.setReturnValue(slot == BlockEntityTypeRendererCache.PAPI$NO_RENDERER ? null : (BlockEntityRenderer<E, S>) slot);
                return;
            }
            Map<BlockEntityType<?>, BlockEntityRenderer<?, ?>> map = this.renderers;
            if (map == null) {
                cir.setReturnValue(null);
                return;
            }
            BlockEntityRenderer<?, ?> r = map.get(type);
            cache.papi$setRenderer(r);
            cir.setReturnValue((BlockEntityRenderer<E, S>) r);
        }
    }
}
