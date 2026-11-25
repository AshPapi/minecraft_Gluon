package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.client.PapiEntityCullingBox;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = EntityRenderer.class, priority = 1500)
public abstract class EntityRendererCullingBoxMixin<T extends Entity, S extends EntityRenderState> implements PapiEntityCullingBox {

    @Shadow
    protected abstract boolean affectedByCulling(T entity);

    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity, float partialTick);

    @Shadow
    protected abstract boolean shouldShowName(T entity, double distanceToCameraSq);

    @Shadow
    protected abstract @Nullable Component getNameTag(T entity);

    @WrapOperation(
        method = "shouldRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getBoundingBoxForCulling(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/AABB;",
            ordinal = 0
        )
    )
    private AABB papi$captureCullingBoxInShouldRender(
            EntityRenderer<T, S> self,
            T entity,
            float partialTick,
            Operation<AABB> original
    ) {
        AABB box = original.call(self, entity, partialTick);
        if (self == (Object) this && entity != null) {
            PapiOcclusionEngine.captureEntityCullingBox(entity.getId(), box);
        }
        return box;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean papi$isAffectedByCulling(Entity entity) {
        return this.affectedByCulling((T) entity);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AABB papi$getCullingBox(Entity entity, float partialTick) {
        return this.getBoundingBoxForCulling((T) entity, partialTick);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean papi$shouldShowName(Entity entity, double distanceToCameraSq) {
        return this.shouldShowName((T) entity, distanceToCameraSq);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable Component papi$getNameTag(Entity entity) {
        return this.getNameTag((T) entity);
    }
}
