package net.ashpapi.papioptimization.mixin.map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ashpapi.papioptimization.access.PapiMapOpacity;
import net.ashpapi.papioptimization.access.PapiMapRenderState;
import net.ashpapi.papioptimization.client.MapDecorationCache;
import net.ashpapi.papioptimization.client.map.PapiMapAtlasManager;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = MapRenderer.class, priority = 1500)
public abstract class MapRendererMixin {

    @Shadow
    @Final
    private TextureAtlas decorationSprites;

    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MapRenderer;extractDecorationRenderState(Lnet/minecraft/world/level/saveddata/maps/MapDecoration;)Lnet/minecraft/client/renderer/state/MapRenderState$MapDecorationRenderState;"
            )
    )
    private MapRenderState.MapDecorationRenderState papi$cachedDecorationRenderState(
            MapRenderer renderer,
            MapDecoration decoration,
            Operation<MapRenderState.MapDecorationRenderState> original
    ) {
        return MapDecorationCache.getOrCreate(decoration, this.decorationSprites);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void papi$extractMapOpacityAndAtlas(
            MapId mapId,
            MapItemSavedData mapData,
            MapRenderState renderState,
            CallbackInfo ci
    ) {
        if (renderState instanceof PapiMapRenderState mrs) {
            boolean trans = !(mapData instanceof PapiMapOpacity op) || op.papi$hasTransparency();
            mrs.papi$setHasTransparency(trans);
            if (mapId != null && PapiConfig.mapAtlas()) {
                PapiMapAtlasManager.populateRenderState(mapId.id(), mrs);
            } else {
                mrs.papi$setAtlasRegion(null, 0.0F, 0.0F, 1.0F, 1.0F);
            }
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V",
                    ordinal = 0
            )
    )
    private void papi$submitMapQuadFromAtlas(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            RenderType defaultRenderType,
            SubmitNodeCollector.CustomGeometryRenderer defaultGeometry,
            Operation<Void> original,
            @Local(argsOnly = true) MapRenderState renderState,
            @Local(argsOnly = true) int packedLight
    ) {
        if (renderState instanceof PapiMapRenderState mrs) {
            Identifier atlasLoc = mrs.papi$getAtlasTexture();
            if (atlasLoc != null) {
                float u0 = mrs.papi$getAtlasU0();
                float v0 = mrs.papi$getAtlasV0();
                float u1 = mrs.papi$getAtlasU1();
                float v1 = mrs.papi$getAtlasV1();
                original.call(
                        collector,
                        poseStack,
                        RenderTypes.text(atlasLoc),
                        (SubmitNodeCollector.CustomGeometryRenderer) (pose, vc) -> {
                            vc.addVertex(pose, 0.0F, 128.0F, -0.01F).setColor(-1).setUv(u0, v1).setLight(packedLight);
                            vc.addVertex(pose, 128.0F, 128.0F, -0.01F).setColor(-1).setUv(u1, v1).setLight(packedLight);
                            vc.addVertex(pose, 128.0F, 0.0F, -0.01F).setColor(-1).setUv(u1, v0).setLight(packedLight);
                            vc.addVertex(pose, 0.0F, 0.0F, -0.01F).setColor(-1).setUv(u0, v0).setLight(packedLight);
                        }
                );
                return;
            }
        }
        original.call(collector, poseStack, defaultRenderType, defaultGeometry);
    }
}
