package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.ashpapi.papioptimization.client.EntityTypeRendererCache;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = EntityRenderDispatcher.class, priority = 1500)
public abstract class EntityRenderDispatcherCacheMixin {

    @Shadow
    private Map<EntityType<?>, EntityRenderer<?, ?>> renderers;

    @Shadow
    private Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>> playerRenderers;

    @Shadow
    private Map<PlayerModelType, AvatarRenderer<ClientMannequin>> mannequinRenderers;

    @Unique
    private @Nullable Map<PlayerModelType, ?> papi$cachedPlayerMap;

    @Unique
    private @Nullable Map<PlayerModelType, ?> papi$cachedMannequinMap;

    @Unique
    private @Nullable AvatarRenderer<?> papi$widePlayerRenderer;

    @Unique
    private @Nullable AvatarRenderer<?> papi$slimPlayerRenderer;

    @Unique
    private @Nullable AvatarRenderer<?> papi$wideMannequinRenderer;

    @Unique
    private @Nullable AvatarRenderer<?> papi$slimMannequinRenderer;

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void papi$populateEntityTypeRendererCache(ResourceManager resourceManager, CallbackInfo ci) {
        Map<EntityType<?>, EntityRenderer<?, ?>> entityMap = this.renderers;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ((EntityTypeRendererCache) type).papi$setRenderer(entityMap != null ? entityMap.get(type) : null);
        }
        Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>> pMap = this.playerRenderers;
        this.papi$cachedPlayerMap = pMap;
        this.papi$widePlayerRenderer = pMap != null ? pMap.get(PlayerModelType.WIDE) : null;
        this.papi$slimPlayerRenderer = pMap != null ? pMap.get(PlayerModelType.SLIM) : null;

        Map<PlayerModelType, AvatarRenderer<ClientMannequin>> mMap = this.mannequinRenderers;
        this.papi$cachedMannequinMap = mMap;
        this.papi$wideMannequinRenderer = mMap != null ? mMap.get(PlayerModelType.WIDE) : null;
        this.papi$slimMannequinRenderer = mMap != null ? mMap.get(PlayerModelType.SLIM) : null;
    }

    @WrapOperation(
        method = {
            "getAvatarRenderer",
            "getRenderer(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"
        },
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"
        )
    )
    private @Nullable Object papi$fastLookupAvatarModelRenderer(
            Map<?, ?> map,
            Object key,
            Operation<Object> original
    ) {
        if (map != null) {
            if (map == this.papi$cachedPlayerMap) {
                if (key == PlayerModelType.WIDE) {
                    return this.papi$widePlayerRenderer;
                }
                if (key == PlayerModelType.SLIM) {
                    return this.papi$slimPlayerRenderer;
                }
            } else if (map == this.papi$cachedMannequinMap) {
                if (key == PlayerModelType.WIDE) {
                    return this.papi$wideMannequinRenderer;
                }
                if (key == PlayerModelType.SLIM) {
                    return this.papi$slimMannequinRenderer;
                }
            }
        }
        return original.call(map, key);
    }

    @SuppressWarnings("unchecked")
    @Inject(
        method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <T extends Entity> void papi$fastGetEntityRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (entity instanceof Avatar) {
            return;
        }
        EntityType<?> type = entity.getType();
        EntityTypeRendererCache cache = (EntityTypeRendererCache) type;
        Object slot = cache.papi$getRendererSlot();
        if (slot != null) {
            if (slot != EntityTypeRendererCache.PAPI$NO_RENDERER) {
                cir.setReturnValue((EntityRenderer<? super T, ?>) slot);
            }
            return;
        }
        Map<EntityType<?>, EntityRenderer<?, ?>> map = this.renderers;
        if (map != null) {
            EntityRenderer<?, ?> resolved = map.get(type);
            cache.papi$setRenderer(resolved);
            if (resolved != null) {
                cir.setReturnValue((EntityRenderer<? super T, ?>) resolved);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(
        method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <S extends EntityRenderState> void papi$fastGetStateRenderer(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        EntityType<?> type = state.entityType;
        if (type != null && type != EntityTypes.PLAYER && !(state instanceof AvatarRenderState)) {
            EntityTypeRendererCache cache = (EntityTypeRendererCache) type;
            Object slot = cache.papi$getRendererSlot();
            if (slot != null) {
                if (slot != EntityTypeRendererCache.PAPI$NO_RENDERER) {
                    cir.setReturnValue((EntityRenderer<?, ? super S>) slot);
                }
                return;
            }
            Map<EntityType<?>, EntityRenderer<?, ?>> map = this.renderers;
            if (map != null) {
                EntityRenderer<?, ?> resolved = map.get(type);
                cache.papi$setRenderer(resolved);
                if (resolved != null) {
                    cir.setReturnValue((EntityRenderer<?, ? super S>) resolved);
                }
            }
        }
    }
}
