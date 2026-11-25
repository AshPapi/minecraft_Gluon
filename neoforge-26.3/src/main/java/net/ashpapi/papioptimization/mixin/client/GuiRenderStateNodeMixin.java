package net.ashpapi.papioptimization.mixin.client;

import java.util.Arrays;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiGuiNodeBounds;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.renderer.state.gui.GuiRenderState$Node", priority = 1500)
public abstract class GuiRenderStateNodeMixin implements PapiGuiNodeBounds {

    @Unique
    private static final int PAPI$SOA_THRESHOLD = 16;

    @Shadow
    public @Nullable List<GuiElementRenderState> elementStates;

    @Shadow
    public @Nullable List<GuiItemRenderState> itemStates;

    @Shadow
    public @Nullable List<GuiTextRenderState> textStates;

    @Shadow
    public @Nullable List<PictureInPictureRenderState> picturesInPictureStates;

    @Unique
    private byte papi$poisonedMask;

    @Unique
    private int papi$tracked0;
    @Unique
    private int papi$minX0 = Integer.MAX_VALUE;
    @Unique
    private int papi$minY0 = Integer.MAX_VALUE;
    @Unique
    private int papi$maxX0 = Integer.MIN_VALUE;
    @Unique
    private int papi$maxY0 = Integer.MIN_VALUE;
    @Unique
    private long papi$tileMask0;
    @Unique
    private int @Nullable [] papi$soaCoords0;
    @Unique
    private long @Nullable [] papi$batchMasks0;
    @Unique
    private int papi$soaCount0;

    @Unique
    private int papi$tracked1;
    @Unique
    private int papi$minX1 = Integer.MAX_VALUE;
    @Unique
    private int papi$minY1 = Integer.MAX_VALUE;
    @Unique
    private int papi$maxX1 = Integer.MIN_VALUE;
    @Unique
    private int papi$maxY1 = Integer.MIN_VALUE;
    @Unique
    private long papi$tileMask1;
    @Unique
    private int @Nullable [] papi$soaCoords1;
    @Unique
    private long @Nullable [] papi$batchMasks1;
    @Unique
    private int papi$soaCount1;

    @Unique
    private int papi$tracked2;
    @Unique
    private int papi$minX2 = Integer.MAX_VALUE;
    @Unique
    private int papi$minY2 = Integer.MAX_VALUE;
    @Unique
    private int papi$maxX2 = Integer.MIN_VALUE;
    @Unique
    private int papi$maxY2 = Integer.MIN_VALUE;
    @Unique
    private long papi$tileMask2;
    @Unique
    private int @Nullable [] papi$soaCoords2;
    @Unique
    private long @Nullable [] papi$batchMasks2;
    @Unique
    private int papi$soaCount2;

    @Unique
    private int papi$tracked3;
    @Unique
    private int papi$minX3 = Integer.MAX_VALUE;
    @Unique
    private int papi$minY3 = Integer.MAX_VALUE;
    @Unique
    private int papi$maxX3 = Integer.MIN_VALUE;
    @Unique
    private int papi$maxY3 = Integer.MIN_VALUE;
    @Unique
    private long papi$tileMask3;
    @Unique
    private int @Nullable [] papi$soaCoords3;
    @Unique
    private long @Nullable [] papi$batchMasks3;
    @Unique
    private int papi$soaCount3;

    @Unique
    private static int @Nullable [] papi$appendSoaCoord(int @Nullable [] coords, int idx, int l, int t, int r, int b) {
        int base = idx << 2;
        if (coords == null) {
            coords = new int[128];
        } else if (base + 4 > coords.length) {
            coords = Arrays.copyOf(coords, coords.length << 1);
        }
        coords[base] = l;
        coords[base + 1] = t;
        coords[base + 2] = r;
        coords[base + 3] = b;
        return coords;
    }

    @Unique
    private static long @Nullable [] papi$appendBatchMask(long @Nullable [] masks, int idx, long tileMask) {
        int batch = idx >>> 4;
        if (masks == null) {
            masks = new long[4];
        } else if (batch >= masks.length) {
            masks = Arrays.copyOf(masks, masks.length << 1);
        }
        masks[batch] |= tileMask;
        return masks;
    }

    @Unique
    private void papi$backfillSoa(int kind, @Nullable List<? extends ScreenArea> list, int countBeforeNew) {
        if (list == null) {
            return;
        }
        int limit = Math.min(countBeforeNew, list.size());
        for (int i = 0; i < limit; i++) {
            ScreenArea area = list.get(i);
            if (area == null) {
                continue;
            }
            ScreenRectangle rect = area.bounds();
            if (rect == null) {
                continue;
            }
            int l = rect.left();
            int t = rect.top();
            int r = rect.right();
            int b = rect.bottom();
            if (l >= r || t >= b) {
                continue;
            }
            long m = PapiGuiNodeBounds.papi$computeTileMask(l, t, r, b);
            papi$pushValidSoa(kind, l, t, r, b, m);
        }
    }

    @Unique
    private void papi$pushValidSoa(int kind, int l, int t, int r, int b, long m) {
        switch (kind) {
            case PAPI$CONTOUR_ELEMENTS -> {
                int idx = this.papi$soaCount0++;
                this.papi$soaCoords0 = papi$appendSoaCoord(this.papi$soaCoords0, idx, l, t, r, b);
                this.papi$batchMasks0 = papi$appendBatchMask(this.papi$batchMasks0, idx, m);
            }
            case PAPI$CONTOUR_ITEMS -> {
                int idx = this.papi$soaCount1++;
                this.papi$soaCoords1 = papi$appendSoaCoord(this.papi$soaCoords1, idx, l, t, r, b);
                this.papi$batchMasks1 = papi$appendBatchMask(this.papi$batchMasks1, idx, m);
            }
            case PAPI$CONTOUR_TEXTS -> {
                int idx = this.papi$soaCount2++;
                this.papi$soaCoords2 = papi$appendSoaCoord(this.papi$soaCoords2, idx, l, t, r, b);
                this.papi$batchMasks2 = papi$appendBatchMask(this.papi$batchMasks2, idx, m);
            }
            default -> {
                int idx = this.papi$soaCount3++;
                this.papi$soaCoords3 = papi$appendSoaCoord(this.papi$soaCoords3, idx, l, t, r, b);
                this.papi$batchMasks3 = papi$appendBatchMask(this.papi$batchMasks3, idx, m);
            }
        }
    }

    @Unique
    private void papi$recordArea(int kind, @Nullable List<? extends ScreenArea> existingList, @Nullable ScreenArea area) {
        int bit = 1 << kind;
        if ((this.papi$poisonedMask & bit) != 0) {
            return;
        }
        int prevSize = existingList == null ? 0 : existingList.size();
        int tracked = switch (kind) {
            case PAPI$CONTOUR_ELEMENTS -> this.papi$tracked0;
            case PAPI$CONTOUR_ITEMS -> this.papi$tracked1;
            case PAPI$CONTOUR_TEXTS -> this.papi$tracked2;
            default -> this.papi$tracked3;
        };
        if (prevSize != tracked) {
            this.papi$poisonedMask |= (byte) bit;
            return;
        }
        int newTracked = tracked + 1;
        switch (kind) {
            case PAPI$CONTOUR_ELEMENTS -> this.papi$tracked0 = newTracked;
            case PAPI$CONTOUR_ITEMS -> this.papi$tracked1 = newTracked;
            case PAPI$CONTOUR_TEXTS -> this.papi$tracked2 = newTracked;
            default -> this.papi$tracked3 = newTracked;
        }
        if (newTracked == PAPI$SOA_THRESHOLD + 1) {
            papi$backfillSoa(kind, existingList, tracked);
        }
        if (area == null) {
            return;
        }
        ScreenRectangle rect = area.bounds();
        if (rect == null) {
            return;
        }
        int left = rect.left();
        int top = rect.top();
        int right = rect.right();
        int bottom = rect.bottom();
        if (left >= right || top >= bottom) {
            return;
        }
        long mask = PapiGuiNodeBounds.papi$computeTileMask(left, top, right, bottom);
        switch (kind) {
            case PAPI$CONTOUR_ELEMENTS -> {
                if (this.papi$tileMask0 == 0L) {
                    this.papi$minX0 = left;
                    this.papi$minY0 = top;
                    this.papi$maxX0 = right;
                    this.papi$maxY0 = bottom;
                } else {
                    if (left < this.papi$minX0) this.papi$minX0 = left;
                    if (top < this.papi$minY0) this.papi$minY0 = top;
                    if (right > this.papi$maxX0) this.papi$maxX0 = right;
                    if (bottom > this.papi$maxY0) this.papi$maxY0 = bottom;
                }
                this.papi$tileMask0 |= mask;
            }
            case PAPI$CONTOUR_ITEMS -> {
                if (this.papi$tileMask1 == 0L) {
                    this.papi$minX1 = left;
                    this.papi$minY1 = top;
                    this.papi$maxX1 = right;
                    this.papi$maxY1 = bottom;
                } else {
                    if (left < this.papi$minX1) this.papi$minX1 = left;
                    if (top < this.papi$minY1) this.papi$minY1 = top;
                    if (right > this.papi$maxX1) this.papi$maxX1 = right;
                    if (bottom > this.papi$maxY1) this.papi$maxY1 = bottom;
                }
                this.papi$tileMask1 |= mask;
            }
            case PAPI$CONTOUR_TEXTS -> {
                if (this.papi$tileMask2 == 0L) {
                    this.papi$minX2 = left;
                    this.papi$minY2 = top;
                    this.papi$maxX2 = right;
                    this.papi$maxY2 = bottom;
                } else {
                    if (left < this.papi$minX2) this.papi$minX2 = left;
                    if (top < this.papi$minY2) this.papi$minY2 = top;
                    if (right > this.papi$maxX2) this.papi$maxX2 = right;
                    if (bottom > this.papi$maxY2) this.papi$maxY2 = bottom;
                }
                this.papi$tileMask2 |= mask;
            }
            default -> {
                if (this.papi$tileMask3 == 0L) {
                    this.papi$minX3 = left;
                    this.papi$minY3 = top;
                    this.papi$maxX3 = right;
                    this.papi$maxY3 = bottom;
                } else {
                    if (left < this.papi$minX3) this.papi$minX3 = left;
                    if (top < this.papi$minY3) this.papi$minY3 = top;
                    if (right > this.papi$maxX3) this.papi$maxX3 = right;
                    if (bottom > this.papi$maxY3) this.papi$maxY3 = bottom;
                }
                this.papi$tileMask3 |= mask;
            }
        }
        if (newTracked > PAPI$SOA_THRESHOLD) {
            papi$pushValidSoa(kind, left, top, right, bottom, mask);
        }
    }

    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void papi$onAddGuiElement(GuiElementRenderState state, CallbackInfo ci) {
        papi$recordArea(PAPI$CONTOUR_ELEMENTS, this.elementStates, state);
    }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void papi$onAddItem(GuiItemRenderState state, CallbackInfo ci) {
        papi$recordArea(PAPI$CONTOUR_ITEMS, this.itemStates, state);
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void papi$onAddText(GuiTextRenderState state, CallbackInfo ci) {
        papi$recordArea(PAPI$CONTOUR_TEXTS, this.textStates, state);
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void papi$onAddPip(PictureInPictureRenderState state, CallbackInfo ci) {
        papi$recordArea(PAPI$CONTOUR_PIP, this.picturesInPictureStates, state);
    }

    @Unique
    private static int papi$scanSoa(
            int @Nullable [] coords,
            long @Nullable [] batchMasks,
            int validCount,
            int left,
            int top,
            int right,
            int bottom,
            long qMask
    ) {
        if (coords == null || batchMasks == null || validCount <= 0) {
            return 0;
        }
        int lastBatch = (validCount - 1) >>> 4;
        for (int b = lastBatch; b >= 0; b--) {
            if ((batchMasks[b] & qMask) == 0L) {
                continue;
            }
            int start = b << 4;
            int end = Math.min(validCount, start + 16);
            for (int i = end - 1; i >= start; i--) {
                int base = i << 2;
                if (coords[base] < right
                        && coords[base + 2] > left
                        && coords[base + 1] < bottom
                        && coords[base + 3] > top) {
                    return 1;
                }
            }
        }
        return 0;
    }

    @Override
    public int papi$checkIntersection(int kind, int actualListSize, int left, int top, int right, int bottom) {
        if ((this.papi$poisonedMask & (1 << kind)) != 0) {
            return -1;
        }
        int tracked;
        int minX;
        int minY;
        int maxX;
        int maxY;
        long tileMask;
        int @Nullable [] coords;
        long @Nullable [] batchMasks;
        int soaCount;
        switch (kind) {
            case PAPI$CONTOUR_ELEMENTS -> {
                tracked = this.papi$tracked0;
                minX = this.papi$minX0;
                minY = this.papi$minY0;
                maxX = this.papi$maxX0;
                maxY = this.papi$maxY0;
                tileMask = this.papi$tileMask0;
                coords = this.papi$soaCoords0;
                batchMasks = this.papi$batchMasks0;
                soaCount = this.papi$soaCount0;
            }
            case PAPI$CONTOUR_ITEMS -> {
                tracked = this.papi$tracked1;
                minX = this.papi$minX1;
                minY = this.papi$minY1;
                maxX = this.papi$maxX1;
                maxY = this.papi$maxY1;
                tileMask = this.papi$tileMask1;
                coords = this.papi$soaCoords1;
                batchMasks = this.papi$batchMasks1;
                soaCount = this.papi$soaCount1;
            }
            case PAPI$CONTOUR_TEXTS -> {
                tracked = this.papi$tracked2;
                minX = this.papi$minX2;
                minY = this.papi$minY2;
                maxX = this.papi$maxX2;
                maxY = this.papi$maxY2;
                tileMask = this.papi$tileMask2;
                coords = this.papi$soaCoords2;
                batchMasks = this.papi$batchMasks2;
                soaCount = this.papi$soaCount2;
            }
            default -> {
                tracked = this.papi$tracked3;
                minX = this.papi$minX3;
                minY = this.papi$minY3;
                maxX = this.papi$maxX3;
                maxY = this.papi$maxY3;
                tileMask = this.papi$tileMask3;
                coords = this.papi$soaCoords3;
                batchMasks = this.papi$batchMasks3;
                soaCount = this.papi$soaCount3;
            }
        }
        if (tracked != actualListSize) {
            return -1;
        }
        if (tileMask == 0L) {
            return 0;
        }
        if (left >= maxX || right <= minX || top >= maxY || bottom <= minY) {
            return 0;
        }
        long qMask = PapiGuiNodeBounds.papi$computeTileMask(left, top, right, bottom);
        if ((tileMask & qMask) == 0L) {
            return 0;
        }
        if (tracked > PAPI$SOA_THRESHOLD) {
            return papi$scanSoa(coords, batchMasks, soaCount, left, top, right, bottom, qMask);
        }
        return -1;
    }
}
