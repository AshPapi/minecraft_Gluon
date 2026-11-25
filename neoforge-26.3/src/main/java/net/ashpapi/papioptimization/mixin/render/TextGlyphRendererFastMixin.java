package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ashpapi.papioptimization.client.PapiTextBatchContext;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.renderer.feature.TextFeatureRenderer$GlyphRenderer", priority = 1500)
public abstract class TextGlyphRendererFastMixin {

    @Unique
    private int papi$cachedGen;

    @Unique
    private @Nullable RenderType papi$cachedRenderType;

    @Unique
    private @Nullable VertexConsumer papi$cachedConsumer;

    @WrapOperation(
        method = "acceptRenderable",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
        )
    )
    private VertexConsumer papi$cacheLastGlyphVertexConsumer(
            TextFeatureRenderer renderer,
            RenderType renderType,
            Operation<VertexConsumer> original
    ) {
        int gen = PapiTextBatchContext.currentGeneration();
        if (gen != 0 && gen == this.papi$cachedGen && renderType == this.papi$cachedRenderType && renderType != null) {
            VertexConsumer cached = this.papi$cachedConsumer;
            if (cached != null) {
                return cached;
            }
        }
        VertexConsumer consumer = original.call(renderer, renderType);
        if (gen != 0 && renderType != null && consumer != null && renderType.canConsolidateConsecutiveGeometry()) {
            this.papi$cachedGen = gen;
            this.papi$cachedRenderType = renderType;
            this.papi$cachedConsumer = consumer;
        } else {
            this.papi$cachedRenderType = null;
            this.papi$cachedConsumer = null;
        }
        return consumer;
    }
}
