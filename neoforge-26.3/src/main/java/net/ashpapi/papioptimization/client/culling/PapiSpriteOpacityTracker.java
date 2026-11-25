package net.ashpapi.papioptimization.client.culling;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.InputStream;
import java.util.Optional;
import net.ashpapi.papioptimization.access.PapiSpriteOpacity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiSpriteOpacityTracker {

    public static final byte FLAG_FULLY_OPAQUE = 1;
    public static final byte FLAG_HAS_TRANSLUCENT = 2;

    private static volatile boolean beaconBeamOpaque = true;

    private PapiSpriteOpacityTracker() {}

    public static void analyze(SpriteContents contents, NativeImage image) {
        if (!(contents instanceof PapiSpriteOpacity target)) {
            return;
        }
        Transparency trans = contents.transparency();
        if (trans.isOpaque()) {
            target.papi$setOpacityData(-1L, -1L, -1L, -1L, FLAG_FULLY_OPAQUE);
            return;
        }
        byte baseFlags = trans.hasTranslucent() ? FLAG_HAS_TRANSLUCENT : 0;
        if (image == null || image.isClosed()) {
            target.papi$setOpacityData(0L, 0L, 0L, 0L, baseFlags);
            return;
        }
        int fw = contents.width();
        int fh = contents.height();
        int iw = image.getWidth();
        int ih = image.getHeight();
        if (fw <= 0 || fh <= 0 || iw < fw || ih < fh) {
            target.papi$setOpacityData(0L, 0L, 0L, 0L, baseFlags);
            return;
        }

        int cols = Math.max(1, iw / fw);
        IntList uniqueFrames = contents.isAnimated() ? contents.getUniqueFrames() : null;
        int frameCount = (uniqueFrames != null && !uniqueFrames.isEmpty()) ? uniqueFrames.size() : 1;

        long w0 = 0L;
        long w1 = 0L;
        long w2 = 0L;
        long w3 = 0L;
        boolean sawTranslucent = trans.hasTranslucent();

        for (int sv = 0; sv < 16; sv++) {
            int pyStart = (sv * fh) >>> 4;
            int pyEnd = Math.min(fh, Math.max(pyStart + 1, ((sv + 1) * fh + 15) >>> 4));
            for (int su = 0; su < 16; su++) {
                int pxStart = (su * fw) >>> 4;
                int pxEnd = Math.min(fw, Math.max(pxStart + 1, ((su + 1) * fw + 15) >>> 4));
                boolean cellOpaque = true;

                outer:
                for (int f = 0; f < frameCount; f++) {
                    int frameIdx = uniqueFrames != null ? uniqueFrames.getInt(f) : 0;
                    int baseX = (frameIdx % cols) * fw;
                    int baseY = (frameIdx / cols) * fh;
                    if (baseX < 0 || baseY < 0 || baseX + fw > iw || baseY + fh > ih) {
                        cellOpaque = false;
                        break;
                    }
                    for (int py = pyStart; py < pyEnd; py++) {
                        int absY = baseY + py;
                        for (int px = pxStart; px < pxEnd; px++) {
                            int alpha = image.getPixel(baseX + px, absY) >>> 24;
                            if (alpha != 255) {
                                cellOpaque = false;
                                if (alpha > 0) {
                                    sawTranslucent = true;
                                }
                                break outer;
                            }
                        }
                    }
                }

                if (cellOpaque) {
                    int bitIndex = (sv << 4) | su;
                    long bit = 1L << (bitIndex & 63);
                    switch (bitIndex >>> 6) {
                        case 0 -> w0 |= bit;
                        case 1 -> w1 |= bit;
                        case 2 -> w2 |= bit;
                        default -> w3 |= bit;
                    }
                }
            }
        }

        byte flags = sawTranslucent ? FLAG_HAS_TRANSLUCENT : 0;
        if ((w0 & w1 & w2 & w3) == -1L && !sawTranslucent) {
            flags |= FLAG_FULLY_OPAQUE;
        }
        target.papi$setOpacityData(w0, w1, w2, w3, flags);
    }

    public static boolean isFullyOpaque(TextureAtlasSprite sprite) {
        if (sprite.contents() instanceof PapiSpriteOpacity op) {
            return (op.papi$getOpacityFlags() & FLAG_FULLY_OPAQUE) != 0;
        }
        return sprite.contents().transparency().isOpaque();
    }

    public static boolean hasTranslucentPixels(TextureAtlasSprite sprite) {
        if (sprite.contents() instanceof PapiSpriteOpacity op) {
            return (op.papi$getOpacityFlags() & FLAG_HAS_TRANSLUCENT) != 0;
        }
        return sprite.contents().transparency().hasTranslucent();
    }

    public static boolean isUvBoxOpaque(
            TextureAtlasSprite sprite,
            float minU,
            float minV,
            float maxU,
            float maxV
    ) {
        if (!(sprite.contents() instanceof PapiSpriteOpacity op)) {
            return sprite.contents().transparency().isOpaque();
        }
        if ((op.papi$getOpacityFlags() & FLAG_FULLY_OPAQUE) != 0) {
            return true;
        }
        float u0 = sprite.getU0();
        float uSpan = sprite.getU1() - u0;
        float v0 = sprite.getV0();
        float vSpan = sprite.getV1() - v0;
        if (uSpan <= 1.0E-7F || vSpan <= 1.0E-7F) {
            return false;
        }
        int su0 = Mth.clamp(Mth.floor(((minU - u0) / uSpan) * 16.0F + 1.0E-4F), 0, 15);
        int su1 = Mth.clamp(Mth.ceil(((maxU - u0) / uSpan) * 16.0F - 1.0E-4F) - 1, su0, 15);
        int sv0 = Mth.clamp(Mth.floor(((minV - v0) / vSpan) * 16.0F + 1.0E-4F), 0, 15);
        int sv1 = Mth.clamp(Mth.ceil(((maxV - v0) / vSpan) * 16.0F - 1.0E-4F) - 1, sv0, 15);

        int rowMask = ((1 << (su1 - su0 + 1)) - 1) << su0;
        for (int sv = sv0; sv <= sv1; sv++) {
            int wordIdx = sv >>> 2;
            int shift = (sv & 3) << 4;
            long word = op.papi$getOpacityWord(wordIdx);
            int rowBits = (int) (word >>> shift) & 0xFFFF;
            if ((rowBits & rowMask) != rowMask) {
                return false;
            }
        }
        return true;
    }

    public static boolean isUvPointOpaque(TextureAtlasSprite sprite, float u, float v) {
        if (!(sprite.contents() instanceof PapiSpriteOpacity op)) {
            return sprite.contents().transparency().isOpaque();
        }
        if ((op.papi$getOpacityFlags() & FLAG_FULLY_OPAQUE) != 0) {
            return true;
        }
        float u0 = sprite.getU0();
        float uSpan = sprite.getU1() - u0;
        float v0 = sprite.getV0();
        float vSpan = sprite.getV1() - v0;
        if (uSpan <= 1.0E-7F || vSpan <= 1.0E-7F) {
            return false;
        }
        int su = Mth.clamp(Mth.floor(((u - u0) / uSpan) * 16.0F), 0, 15);
        int sv = Mth.clamp(Mth.floor(((v - v0) / vSpan) * 16.0F), 0, 15);
        int bitIdx = (sv << 4) | su;
        long word = op.papi$getOpacityWord(bitIdx >>> 6);
        return (word & (1L << (bitIdx & 63))) != 0L;
    }

    public static void refreshStandaloneTextures() {
        boolean opaque = true;
        Minecraft mc = Minecraft.getInstance();
        ResourceManager rm = mc != null ? mc.getResourceManager() : null;
        if (rm != null) {
            try {
                Optional<Resource> res = rm.getResource(BeaconRenderer.BEAM_LOCATION);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                        opaque = img.computeTransparency().isOpaque();
                    }
                }
            } catch (Throwable ignored) {
                opaque = true;
            }
        }
        beaconBeamOpaque = opaque;
    }

    public static boolean isBeaconBeamOpaque() {
        return beaconBeamOpaque;
    }
}
