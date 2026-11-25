package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.RandomAccess;
import net.ashpapi.papioptimization.access.PapiGuiNodeBounds;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiRenderState.class, priority = 1500)
public abstract class GuiRenderStateMixin {

    @Unique
    private @Nullable List<? extends ScreenArea> papi$knownHitList;

    @Unique
    private <T extends ScreenArea> @Nullable List<T> papi$pruneContour(
            Object node,
            Operation<List<T>> original,
            @Nullable ScreenRectangle bounds,
            int kind
    ) {
        List<T> list = original.call(node);
        if (list == null || list.isEmpty() || bounds == null) {
            return null;
        }
        int left = bounds.left();
        int top = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        if (left >= right || top >= bottom) {
            return null;
        }
        if (node instanceof PapiGuiNodeBounds nodeBounds) {
            int res = nodeBounds.papi$checkIntersection(kind, list.size(), left, top, right, bottom);
            if (res == 0) {
                return null;
            }
            if (res > 0) {
                this.papi$knownHitList = list;
                return list;
            }
        }
        return list;
    }

    @WrapOperation(
        method = "navigateToAboveHighestElementWithIntersectingBounds",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;elementStates:Ljava/util/List;", opcode = Opcodes.GETFIELD)
    )
    private @Nullable List<GuiElementRenderState> papi$pruneElementContour(
            @Coerce Object node,
            Operation<List<GuiElementRenderState>> original,
            @Local(argsOnly = true) ScreenRectangle bounds
    ) {
        return papi$pruneContour(node, original, bounds, PapiGuiNodeBounds.PAPI$CONTOUR_ELEMENTS);
    }

    @WrapOperation(
        method = "navigateToAboveHighestElementWithIntersectingBounds",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;itemStates:Ljava/util/List;", opcode = Opcodes.GETFIELD)
    )
    private @Nullable List<GuiItemRenderState> papi$pruneItemContour(
            @Coerce Object node,
            Operation<List<GuiItemRenderState>> original,
            @Local(argsOnly = true) ScreenRectangle bounds
    ) {
        return papi$pruneContour(node, original, bounds, PapiGuiNodeBounds.PAPI$CONTOUR_ITEMS);
    }

    @WrapOperation(
        method = "navigateToAboveHighestElementWithIntersectingBounds",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;textStates:Ljava/util/List;", opcode = Opcodes.GETFIELD)
    )
    private @Nullable List<GuiTextRenderState> papi$pruneTextContour(
            @Coerce Object node,
            Operation<List<GuiTextRenderState>> original,
            @Local(argsOnly = true) ScreenRectangle bounds
    ) {
        return papi$pruneContour(node, original, bounds, PapiGuiNodeBounds.PAPI$CONTOUR_TEXTS);
    }

    @WrapOperation(
        method = "navigateToAboveHighestElementWithIntersectingBounds",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;picturesInPictureStates:Ljava/util/List;", opcode = Opcodes.GETFIELD)
    )
    private @Nullable List<PictureInPictureRenderState> papi$prunePipContour(
            @Coerce Object node,
            Operation<List<PictureInPictureRenderState>> original,
            @Local(argsOnly = true) ScreenRectangle bounds
    ) {
        return papi$pruneContour(node, original, bounds, PapiGuiNodeBounds.PAPI$CONTOUR_PIP);
    }

    @WrapMethod(method = "hasIntersection")
    private boolean papi$fastIndexedHasIntersection(
            @Nullable ScreenRectangle bounds,
            @Nullable List<? extends ScreenArea> list,
            Operation<Boolean> original
    ) {
        List<? extends ScreenArea> knownHit = this.papi$knownHitList;
        this.papi$knownHitList = null;
        if (list == null || list.isEmpty() || bounds == null) {
            return false;
        }
        int left = bounds.left();
        int top = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        if (left >= right || top >= bottom) {
            return false;
        }
        if (list == knownHit) {
            return true;
        }
        if (list instanceof RandomAccess) {
            for (int i = list.size() - 1; i >= 0; i--) {
                ScreenArea area = list.get(i);
                if (area == null) {
                    continue;
                }
                ScreenRectangle other = area.bounds();
                if (other != null) {
                    int ol = other.left();
                    int ot = other.top();
                    int or = other.right();
                    int ob = other.bottom();
                    if (ol < or && ot < ob && ol < right && or > left && ot < bottom && ob > top) {
                        return true;
                    }
                }
            }
            return false;
        }
        for (ScreenArea area : list) {
            if (area == null) {
                continue;
            }
            ScreenRectangle other = area.bounds();
            if (other != null) {
                int ol = other.left();
                int ot = other.top();
                int or = other.right();
                int ob = other.bottom();
                if (ol < or && ot < ob && ol < right && or > left && ot < bottom && ob > top) {
                    return true;
                }
            }
        }
        return false;
    }
}
