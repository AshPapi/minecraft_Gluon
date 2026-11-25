package net.ashpapi.papioptimization.mixin.map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.ashpapi.papioptimization.access.PapiMapRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiGraphicsExtractor.class, priority = 1500)
public abstract class MapGuiGraphicsMixin {

    @WrapOperation(
            method = "map",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;IIIIFFFFI)V",
                    ordinal = 0
            )
    )
    private void papi$blitMapFromAtlas(
            GuiGraphicsExtractor instance,
            RenderPipeline pipeline,
            GpuTextureView defaultView,
            GpuSampler defaultSampler,
            int x0,
            int y0,
            int x1,
            int y1,
            float u0,
            float u1,
            float v0,
            float v1,
            int color,
            Operation<Void> original,
            @Local(argsOnly = true) MapRenderState renderState
    ) {
        if (renderState instanceof PapiMapRenderState mrs) {
            Identifier atlasLoc = mrs.papi$getAtlasTexture();
            if (atlasLoc != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    AbstractTexture atlasTex = mc.getTextureManager().getTexture(atlasLoc);
                    original.call(
                            instance,
                            pipeline,
                            atlasTex.getTextureView(),
                            atlasTex.getSampler(),
                            x0,
                            y0,
                            x1,
                            y1,
                            mrs.papi$getAtlasU0(),
                            mrs.papi$getAtlasU1(),
                            mrs.papi$getAtlasV0(),
                            mrs.papi$getAtlasV1(),
                            color
                    );
                    return;
                }
            }
        }
        original.call(instance, pipeline, defaultView, defaultSampler, x0, y0, x1, y1, u0, u1, v0, v1, color);
    }
}
