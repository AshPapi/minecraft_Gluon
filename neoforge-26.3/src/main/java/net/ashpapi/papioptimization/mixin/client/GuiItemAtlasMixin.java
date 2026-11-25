package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.ashpapi.papioptimization.client.PapiAnimatedItemAtlasHook;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiItemAtlas.class, priority = 1500)
public abstract class GuiItemAtlasMixin implements PapiAnimatedItemAtlasHook {

    @Shadow
    @Final
    private GpuTexture texture;

    @Shadow
    @Final
    private GpuTexture depthTexture;

    @Unique
    private @Nullable GuiItemAtlas papi$animatedDelegate;

    @Unique
    private boolean papi$isAnimatedAtlas;

    @Unique
    private boolean papi$needsFrameClear;

    @Unique
    private boolean papi$clearedThisFrame;

    @Override
    public void papi$setAnimatedDelegate(@Nullable GuiItemAtlas delegate) {
        this.papi$animatedDelegate = delegate;
    }

    @Override
    public void papi$markAsAnimatedAtlas(boolean needsClearThisFrame) {
        this.papi$isAnimatedAtlas = true;
        this.papi$needsFrameClear = needsClearThisFrame;
        if (!needsClearThisFrame) {
            this.papi$clearedThisFrame = true;
        } else {
            this.papi$clearedThisFrame = false;
        }
    }

    @WrapMethod(method = "getOrUpdate")
    private GuiItemAtlas.@Nullable SlotView papi$routeAnimatedOrLazyClear(
            TrackingItemStackRenderState state,
            Operation<GuiItemAtlas.SlotView> original
    ) {
        GuiItemAtlas delegate = this.papi$animatedDelegate;
        if (delegate != null && state != null && state.isAnimated()) {
            return delegate.getOrUpdate(state);
        }
        if (this.papi$isAnimatedAtlas && this.papi$needsFrameClear && !this.papi$clearedThisFrame) {
            this.papi$clearedThisFrame = true;
            this.papi$needsFrameClear = false;
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .clearColorAndDepthTextures(this.texture, GuiRenderer.CLEAR_COLOR, this.depthTexture, 0.0);
        }
        return original.call(state);
    }

    @WrapOperation(
        method = "drawToSlot",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;DIIIII)V"
        )
    )
    private void papi$skipRedundantPerSlotClear(
            CommandEncoder encoder,
            GpuTexture colorTex,
            Vector4fc clearColor,
            GpuTexture depthTex,
            double clearDepth,
            int x,
            int y,
            int width,
            int height,
            int mipLevel,
            Operation<Void> original
    ) {
        if (this.papi$isAnimatedAtlas && this.papi$clearedThisFrame) {
            return;
        }
        original.call(encoder, colorTex, clearColor, depthTex, clearDepth, x, y, width, height, mipLevel);
    }
}
