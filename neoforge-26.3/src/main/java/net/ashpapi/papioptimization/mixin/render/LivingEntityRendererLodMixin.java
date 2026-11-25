package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.access.PapiLodEntityRenderState;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.client.occlusion.PapiSkeletalLod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LivingEntityRenderer.class, priority = 1500)
public abstract class LivingEntityRendererLodMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void papi$markFarLodState(T entity, S state, float partialTick, CallbackInfo ci) {
        if (PapiOcclusionEngine.isShadowPass()
                || state.isInvisible
                || entity instanceof Avatar
                || entity instanceof ArmorStand
                || entity instanceof Sheep
                || entity instanceof AbstractCubeMob
                || entity instanceof Creaking
                || entity instanceof Warden
                || entity instanceof WitherBoss
                || entity instanceof EnderDragon
                || (entity instanceof Creeper creeper && creeper.isPowered())
                || entity.hasCustomName()
                || entity.hurtTime > 0
                || entity.isCurrentlyGlowing()
                || !entity.isAlive()) {
            ((PapiLodEntityRenderState) state).papi$setFarLod(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
        boolean far = camera != null && PapiSkeletalLod.isBeyondLayerLod(camera.position(), state.x, state.y, state.z, camera.getFov());
        ((PapiLodEntityRenderState) state).papi$setFarLod(far);
    }

    @Inject(method = "shouldRenderLayers", at = @At("HEAD"), cancellable = true)
    private void papi$skipFarDistantLayers(S state, CallbackInfoReturnable<Boolean> cir) {
        if (!PapiOcclusionEngine.isShadowPass() && ((PapiLodEntityRenderState) state).papi$isFarLod()) {
            cir.setReturnValue(false);
        }
    }
}
