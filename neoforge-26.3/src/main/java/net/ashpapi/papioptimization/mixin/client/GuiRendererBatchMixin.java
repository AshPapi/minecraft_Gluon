package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.ashpapi.papioptimization.client.PapiAnimatedItemAtlasHook;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiRenderer.class, priority = 1500)
public abstract class GuiRendererBatchMixin {

    @Unique
    private static final int PAPI$LOOKAHEAD_WINDOW = 64;

    @Unique
    private static final int PAPI$MAX_COALESCE_ELEMENTS = 16384;

    @Unique
    private static final int PAPI$MIN_ANIMATED_ATLAS_MODELS = 3;

    @Shadow
    @Final
    private GuiRenderState renderState;

    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Unique
    private final ArrayList<GuiElementRenderState> papi$batchInput = new ArrayList<>(128);

    @Unique
    private final Consumer<GuiElementRenderState> papi$inputCollector = this.papi$batchInput::add;

    @Unique
    private boolean papi$coalescing;

    @Unique
    private boolean[] papi$emitted = new boolean[128];

    @Unique
    private final int[] papi$blockL = new int[PAPI$LOOKAHEAD_WINDOW];

    @Unique
    private final int[] papi$blockT = new int[PAPI$LOOKAHEAD_WINDOW];

    @Unique
    private final int[] papi$blockR = new int[PAPI$LOOKAHEAD_WINDOW];

    @Unique
    private final int[] papi$blockB = new int[PAPI$LOOKAHEAD_WINDOW];

    @Unique
    private final ObjectOpenHashSet<Object> papi$animatedIdentities = new ObjectOpenHashSet<>(16);

    @Unique
    private final ObjectOpenHashSet<Object> papi$staticIdentities = new ObjectOpenHashSet<>(64);

    @Unique
    private final Consumer<GuiItemRenderState> papi$itemClassifier = state -> {
        if (state != null && state.oversizedItemBounds() == null) {
            TrackingItemStackRenderState itemState = state.itemStackRenderState();
            Object id = itemState.getModelIdentity();
            if (id != null) {
                if (itemState.isAnimated()) {
                    this.papi$animatedIdentities.add(id);
                } else {
                    this.papi$staticIdentities.add(id);
                }
            }
        }
    };

    @Unique
    private @Nullable GuiItemAtlas papi$animatedItemAtlas;

    @Unique
    private GuiItemAtlas papi$prepareDedicatedAnimatedAtlas(Set<Object> animatedIds, int slotSize) {
        GuiItemAtlas current = this.papi$animatedItemAtlas;
        if (current != null && current.tryPrepareFor(animatedIds)) {
            if (current instanceof PapiAnimatedItemAtlasHook hook) {
                hook.papi$markAsAnimatedAtlas(true);
            }
            return current;
        }
        int texSize = GuiItemAtlas.computeTextureSizeFor(slotSize, animatedIds.size());
        if (current != null && current.textureSize() == texSize) {
            if (current instanceof PapiAnimatedItemAtlasHook hook) {
                hook.papi$markAsAnimatedAtlas(true);
            }
            return current;
        }
        if (current != null) {
            current.close();
        }
        GuiItemAtlas created = new GuiItemAtlas(this.featureRenderDispatcher, texSize, slotSize);
        if (created instanceof PapiAnimatedItemAtlasHook hook) {
            hook.papi$markAsAnimatedAtlas(false);
        }
        this.papi$animatedItemAtlas = created;
        return created;
    }

    @WrapOperation(
        method = "prepareItemElements",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepareItemAtlas(Ljava/util/Set;I)Lnet/minecraft/client/gui/render/GuiItemAtlas;"
        )
    )
    private GuiItemAtlas papi$isolateAnimatedGuiItemAtlas(
            GuiRenderer self,
            Set<Object> identities,
            int slotSize,
            Operation<GuiItemAtlas> original
    ) {
        this.papi$animatedIdentities.clear();
        this.papi$staticIdentities.clear();
        try {
            this.renderState.forEachItem(this.papi$itemClassifier);
            if (this.papi$animatedIdentities.size() >= PAPI$MIN_ANIMATED_ATLAS_MODELS) {
                GuiItemAtlas animAtlas = papi$prepareDedicatedAnimatedAtlas(this.papi$animatedIdentities, slotSize);
                if (this.papi$staticIdentities.isEmpty()) {
                    return animAtlas;
                }
                GuiItemAtlas mainAtlas = original.call(self, this.papi$staticIdentities, slotSize);
                if (mainAtlas instanceof PapiAnimatedItemAtlasHook hook) {
                    hook.papi$setAnimatedDelegate(animAtlas);
                }
                return mainAtlas;
            }
            GuiItemAtlas mainAtlas = original.call(self, identities, slotSize);
            if (mainAtlas instanceof PapiAnimatedItemAtlasHook hook) {
                hook.papi$setAnimatedDelegate(null);
            }
            return mainAtlas;
        } finally {
            this.papi$animatedIdentities.clear();
            this.papi$staticIdentities.clear();
        }
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void papi$endAnimatedItemAtlasFrame(CallbackInfo ci) {
        GuiItemAtlas anim = this.papi$animatedItemAtlas;
        if (anim != null) {
            anim.endFrame();
        }
    }

    @Inject(method = "invalidateItemAtlas", at = @At("TAIL"))
    private void papi$invalidateAnimatedItemAtlas(CallbackInfo ci) {
        GuiItemAtlas anim = this.papi$animatedItemAtlas;
        if (anim != null) {
            anim.close();
            this.papi$animatedItemAtlas = null;
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void papi$closeAnimatedItemAtlas(CallbackInfo ci) {
        GuiItemAtlas anim = this.papi$animatedItemAtlas;
        if (anim != null) {
            anim.close();
            this.papi$animatedItemAtlas = null;
        }
    }

    @WrapOperation(
        method = "addElementsToMeshes",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;forEachElement(Ljava/util/function/Consumer;Lnet/minecraft/client/renderer/state/gui/GuiRenderState$TraverseRange;)V"
        )
    )
    private void papi$coalesceNonOverlappingGuiBatches(
            GuiRenderState renderState,
            Consumer<GuiElementRenderState> consumer,
            GuiRenderState.TraverseRange range,
            Operation<Void> original
    ) {
        if (this.papi$coalescing) {
            original.call(renderState, consumer, range);
            return;
        }
        ArrayList<GuiElementRenderState> input = this.papi$batchInput;
        input.clear();
        this.papi$coalescing = true;
        try {
            original.call(renderState, this.papi$inputCollector, range);
            int n = input.size();
            if (n <= 2 || n > PAPI$MAX_COALESCE_ELEMENTS) {
                for (int i = 0; i < n; i++) {
                    consumer.accept(input.get(i));
                }
                return;
            }
            boolean[] emitted = this.papi$emitted;
            if (emitted.length < n) {
                emitted = new boolean[Math.max(n, emitted.length << 1)];
                this.papi$emitted = emitted;
            } else {
                Arrays.fill(emitted, 0, n, false);
            }

            int[] bL = this.papi$blockL;
            int[] bT = this.papi$blockT;
            int[] bR = this.papi$blockR;
            int[] bB = this.papi$blockB;

            for (int i = 0; i < n; i++) {
                if (emitted[i]) {
                    continue;
                }
                GuiElementRenderState first = input.get(i);
                emitted[i] = true;
                consumer.accept(first);

                RenderPipeline firstPipe = first.pipeline();
                if (firstPipe == null || firstPipe.getPrimitiveTopology().connectedPrimitives) {
                    continue;
                }
                ScreenRectangle firstBounds = first.bounds();
                if (firstBounds == null || firstBounds.left() >= firstBounds.right() || firstBounds.top() >= firstBounds.bottom()) {
                    continue;
                }
                TextureSetup firstTex = first.textureSetup();
                ScreenRectangle firstScissor = first.scissorArea();

                int limit = Math.min(n, i + 1 + PAPI$LOOKAHEAD_WINDOW);
                int pendingCount = 0;
                int uMinX = Integer.MAX_VALUE;
                int uMinY = Integer.MAX_VALUE;
                int uMaxX = Integer.MIN_VALUE;
                int uMaxY = Integer.MIN_VALUE;

                for (int j = i + 1; j < limit; j++) {
                    if (emitted[j]) {
                        continue;
                    }
                    GuiElementRenderState cand = input.get(j);
                    ScreenRectangle cb = cand.bounds();
                    if (cb == null) {
                        break;
                    }
                    int cl = cb.left();
                    int ct = cb.top();
                    int cr = cb.right();
                    int cbBot = cb.bottom();
                    if (cl >= cr || ct >= cbBot) {
                        continue;
                    }

                    if (cand.pipeline() == firstPipe
                            && Objects.equals(cand.scissorArea(), firstScissor)
                            && Objects.equals(cand.textureSetup(), firstTex)) {
                        boolean blocked = false;
                        if (pendingCount > 0 && cl < uMaxX && cr > uMinX && ct < uMaxY && cbBot > uMinY) {
                            for (int k = 0; k < pendingCount; k++) {
                                if (cl < bR[k] && cr > bL[k] && ct < bB[k] && cbBot > bT[k]) {
                                    blocked = true;
                                    break;
                                }
                            }
                        }
                        if (!blocked) {
                            emitted[j] = true;
                            consumer.accept(cand);
                            continue;
                        }
                    }

                    if (pendingCount >= PAPI$LOOKAHEAD_WINDOW) {
                        break;
                    }
                    bL[pendingCount] = cl;
                    bT[pendingCount] = ct;
                    bR[pendingCount] = cr;
                    bB[pendingCount] = cbBot;
                    pendingCount++;
                    if (cl < uMinX) uMinX = cl;
                    if (ct < uMinY) uMinY = ct;
                    if (cr > uMaxX) uMaxX = cr;
                    if (cbBot > uMaxY) uMaxY = cbBot;
                }
            }
        } finally {
            this.papi$coalescing = false;
            input.clear();
        }
    }
}
