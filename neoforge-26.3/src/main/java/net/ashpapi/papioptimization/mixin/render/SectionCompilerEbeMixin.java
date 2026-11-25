package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.access.PapiEbeCompiledSection;
import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SectionCompiler.class, priority = 1500)
public abstract class SectionCompilerEbeMixin {

    @Shadow
    private boolean cutoutLeaves;

    @Inject(
        method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
        at = @At("HEAD")
    )
    private void papi$markVanillaSectionCompilerHooked(
            SectionPos sectionPos,
            RenderSectionRegion region,
            com.mojang.blaze3d.vertex.VertexSorting vertexSorting,
            net.minecraft.client.renderer.SectionBufferBuilderPack buffers,
            java.util.List<net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        PapiEbeRuntime.markSectionCompilerHooked();
    }

    @Inject(
        method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"
        )
    )
    private void papi$bakeStaticBlockEntityIntoSection(
            SectionPos sectionPos,
            RenderSectionRegion region,
            com.mojang.blaze3d.vertex.VertexSorting vertexSorting,
            net.minecraft.client.renderer.SectionBufferBuilderPack buffers,
            java.util.List<net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
            CallbackInfoReturnable<SectionCompiler.Results> cir,
            @Local SectionCompiler.Results results,
            @Local ModelBlockRenderer modelBlockRenderer,
            @Local(ordinal = 0) BlockQuadOutput normalOutput,
            @Local(ordinal = 1) BlockQuadOutput forceOpaqueOutput,
            @Local(ordinal = 2) BlockPos blockPos,
            @Local BlockState blockState
    ) {
        if (!PapiEbeRuntime.isActive() || !PapiEbeRuntime.isBakeableBlock(blockState)) {
            return;
        }
        BlockEntity be = region.getBlockEntity(blockPos);
        if (be == null || !PapiEbeRuntime.isBakeableBlockEntity(be, blockState)) {
            return;
        }
        PapiEbeDynamicTracker tracker = (be instanceof PapiEbeDynamicTracker t) ? t : null;
        PapiEbeCompiledSection compiled = ((Object) results instanceof PapiEbeCompiledSection c) ? c : null;
        if (PapiEbeRuntime.isDynamicNow(be, blockState, region)
                || (tracker != null && tracker.papi$wasEbeDynamic())) {
            if (tracker != null && compiled != null) {
                compiled.papi$addEbeUnbakedTracker(tracker);
            }
            return;
        }
        BlockStateModel staticModel = PapiEbeRuntime.getStaticModel(blockState, be);
        if (staticModel == null) {
            if (tracker != null && compiled != null) {
                compiled.papi$addEbeUnbakedTracker(tracker);
            }
            return;
        }
        BlockQuadOutput output = ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? forceOpaqueOutput : normalOutput;
        modelBlockRenderer.tesselateBlock(
                output,
                (float) SectionPos.sectionRelative(blockPos.getX()),
                (float) SectionPos.sectionRelative(blockPos.getY()),
                (float) SectionPos.sectionRelative(blockPos.getZ()),
                region,
                blockPos,
                blockState,
                staticModel,
                blockState.getSeed(blockPos)
        );
        if (tracker != null && compiled != null) {
            compiled.papi$addEbeBakedTracker(tracker);
        }
    }
}
