package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.ashpapi.papioptimization.client.PapiEmptyPreparedText;
import net.ashpapi.papioptimization.client.PapiFontSequenceKey;
import net.ashpapi.papioptimization.client.PapiSignRenderCache;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Font.class, priority = 1500)
public abstract class FontMixin {

    @Unique
    private static final int PAPI$WIDTH_CACHE_SIZE = 512;

    @Unique
    private static final int PAPI$WIDTH_CACHE_MASK = PAPI$WIDTH_CACHE_SIZE - 1;

    @Unique
    private static final int PAPI$PREP_STR_SIZE = 256;

    @Unique
    private static final int PAPI$PREP_STR_MASK = PAPI$PREP_STR_SIZE - 1;

    @Unique
    private static final int PAPI$PREP_SEQ_SIZE = 256;

    @Unique
    private static final int PAPI$PREP_SEQ_MASK = PAPI$PREP_SEQ_SIZE - 1;

    @Unique
    private static final int PAPI$PREP_OUTLINE_SIZE = 128;

    @Unique
    private static final int PAPI$PREP_OUTLINE_MASK = PAPI$PREP_OUTLINE_SIZE - 1;

    @Unique
    private static final Font.PreparedText PAPI$EMPTY_PREPARED = PapiEmptyPreparedText.INSTANCE;

    @Unique
    private final String[] papi$widthKeys = new String[PAPI$WIDTH_CACHE_SIZE];

    @Unique
    private final int[] papi$widthVals = new int[PAPI$WIDTH_CACHE_SIZE];

    @Unique
    private int papi$widthEpoch = -1;

    @Unique
    private final String[] papi$prepStrKeys = new String[PAPI$PREP_STR_SIZE];

    @Unique
    private final float[] papi$prepStrX = new float[PAPI$PREP_STR_SIZE];

    @Unique
    private final float[] papi$prepStrY = new float[PAPI$PREP_STR_SIZE];

    @Unique
    private final int[] papi$prepStrColor = new int[PAPI$PREP_STR_SIZE];

    @Unique
    private final byte[] papi$prepStrFlags = new byte[PAPI$PREP_STR_SIZE];

    @Unique
    private final int[] papi$prepStrBg = new int[PAPI$PREP_STR_SIZE];

    @Unique
    private final Font.PreparedText[] papi$prepStrVals = new Font.PreparedText[PAPI$PREP_STR_SIZE];

    @Unique
    private int papi$prepStrEpoch = -1;

    @Unique
    private final PapiFontSequenceKey[] papi$prepSeqKeys = new PapiFontSequenceKey[PAPI$PREP_SEQ_SIZE];

    @Unique
    private final float[] papi$prepSeqX = new float[PAPI$PREP_SEQ_SIZE];

    @Unique
    private final float[] papi$prepSeqY = new float[PAPI$PREP_SEQ_SIZE];

    @Unique
    private final int[] papi$prepSeqColor = new int[PAPI$PREP_SEQ_SIZE];

    @Unique
    private final byte[] papi$prepSeqFlags = new byte[PAPI$PREP_SEQ_SIZE];

    @Unique
    private final int[] papi$prepSeqBg = new int[PAPI$PREP_SEQ_SIZE];

    @Unique
    private final Font.PreparedText[] papi$prepSeqVals = new Font.PreparedText[PAPI$PREP_SEQ_SIZE];

    @Unique
    private int papi$prepSeqEpoch = -1;

    @Unique
    private final PapiFontSequenceKey[] papi$outlineKeys = new PapiFontSequenceKey[PAPI$PREP_OUTLINE_SIZE];

    @Unique
    private final float[] papi$outlineX = new float[PAPI$PREP_OUTLINE_SIZE];

    @Unique
    private final float[] papi$outlineY = new float[PAPI$PREP_OUTLINE_SIZE];

    @Unique
    private final int[] papi$outlineColor = new int[PAPI$PREP_OUTLINE_SIZE];

    @Unique
    private final Font.PreparedText[] papi$outlineVals = new Font.PreparedText[PAPI$PREP_OUTLINE_SIZE];

    @Unique
    private int papi$outlineEpoch = -1;

    @Unique
    private final PapiFontSequenceKey.Probe papi$seqProbe = new PapiFontSequenceKey.Probe();

    @Unique
    private static boolean papi$containsObfuscatedCode(String s) {
        int len = s.length();
        for (int i = 0; i < len - 1; i++) {
            if (s.charAt(i) == '\u00A7') {
                char next = s.charAt(i + 1);
                if (next == 'k' || next == 'K') {
                    return true;
                }
            }
        }
        return false;
    }

    @WrapMethod(method = "width(Ljava/lang/String;)I")
    private int papi$fastCachedStringWidth(String str, Operation<Integer> original) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        if (str.length() > 128 || !RenderSystem.isOnRenderThread()) {
            return original.call(str);
        }
        int epoch = PapiClientCaches.epoch();
        if (this.papi$widthEpoch != epoch) {
            Arrays.fill(this.papi$widthKeys, null);
            this.papi$widthEpoch = epoch;
        }
        int h = str.hashCode();
        int slot = (h ^ (h >>> 16)) & PAPI$WIDTH_CACHE_MASK;
        String cachedKey = this.papi$widthKeys[slot];
        if (cachedKey != null && cachedKey.equals(str)) {
            return this.papi$widthVals[slot];
        }
        int computed = original.call(str);
        this.papi$widthKeys[slot] = str;
        this.papi$widthVals[slot] = computed;
        return computed;
    }

    @WrapMethod(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;")
    private Font.PreparedText papi$cachedPreparedStringText(
            String text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            int backgroundColor,
            Operation<Font.PreparedText> original
    ) {
        if (text == null || text.isEmpty()) {
            if ((backgroundColor & 0xFF000000) == 0) {
                return PAPI$EMPTY_PREPARED;
            }
            return original.call(text, x, y, color, dropShadow, backgroundColor);
        }
        if (text.length() > 128 || papi$containsObfuscatedCode(text) || !RenderSystem.isOnRenderThread()) {
            return original.call(text, x, y, color, dropShadow, backgroundColor);
        }
        int epoch = PapiClientCaches.epoch();
        if (this.papi$prepStrEpoch != epoch) {
            Arrays.fill(this.papi$prepStrKeys, null);
            Arrays.fill(this.papi$prepStrVals, null);
            this.papi$prepStrEpoch = epoch;
        }
        boolean bidi = ((Font) (Object) this).isBidirectional();
        byte flags = (byte) ((dropShadow ? 1 : 0) | (bidi ? 2 : 0));
        int h = text.hashCode();
        h = 31 * h + Float.floatToRawIntBits(x);
        h = 31 * h + Float.floatToRawIntBits(y);
        h = 31 * h + color;
        h = 31 * h + flags;
        h = 31 * h + backgroundColor;
        int slot = (h ^ (h >>> 16)) & PAPI$PREP_STR_MASK;
        String cachedKey = this.papi$prepStrKeys[slot];
        if (cachedKey != null
                && cachedKey.equals(text)
                && this.papi$prepStrX[slot] == x
                && this.papi$prepStrY[slot] == y
                && this.papi$prepStrColor[slot] == color
                && this.papi$prepStrFlags[slot] == flags
                && this.papi$prepStrBg[slot] == backgroundColor) {
            Font.PreparedText cachedVal = this.papi$prepStrVals[slot];
            if (cachedVal != null) {
                return cachedVal;
            }
        }
        Font.PreparedText built = original.call(text, x, y, color, dropShadow, backgroundColor);
        if (built != null) {
            this.papi$prepStrKeys[slot] = text;
            this.papi$prepStrX[slot] = x;
            this.papi$prepStrY[slot] = y;
            this.papi$prepStrColor[slot] = color;
            this.papi$prepStrFlags[slot] = flags;
            this.papi$prepStrBg[slot] = backgroundColor;
            this.papi$prepStrVals[slot] = built;
        }
        return built;
    }

    @WrapMethod(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;")
    private Font.PreparedText papi$cachedPreparedSequenceText(
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            boolean includeEmpty,
            int backgroundColor,
            Operation<Font.PreparedText> original
    ) {
        if (text == null || text == FormattedCharSequence.EMPTY) {
            if ((backgroundColor & 0xFF000000) == 0) {
                return PAPI$EMPTY_PREPARED;
            }
            return original.call(text, x, y, color, dropShadow, includeEmpty, backgroundColor);
        }
        if (!RenderSystem.isOnRenderThread()) {
            return original.call(text, x, y, color, dropShadow, includeEmpty, backgroundColor);
        }
        if (text instanceof PapiSignRenderCache.PreparedLine signLine) {
            if (signLine.isKnownEmpty() && (backgroundColor & 0xFF000000) == 0) {
                return PAPI$EMPTY_PREPARED;
            }
            return signLine.getOrPrepareMain((Font) (Object) this, x, y, color, dropShadow, includeEmpty, backgroundColor, original);
        }
        PapiFontSequenceKey.Probe probe = this.papi$seqProbe;
        probe.reset();
        try {
            text.accept(probe);
            if (probe.isRejected()) {
                return original.call(text, x, y, color, dropShadow, includeEmpty, backgroundColor);
            }
            if (probe.length() == 0 && (backgroundColor & 0xFF000000) == 0) {
                return PAPI$EMPTY_PREPARED;
            }
            int epoch = PapiClientCaches.epoch();
            if (this.papi$prepSeqEpoch != epoch) {
                Arrays.fill(this.papi$prepSeqKeys, null);
                Arrays.fill(this.papi$prepSeqVals, null);
                this.papi$prepSeqEpoch = epoch;
            }
            byte flags = (byte) ((dropShadow ? 1 : 0) | (includeEmpty ? 2 : 0));
            int h = probe.hash();
            h = 31 * h + Float.floatToRawIntBits(x);
            h = 31 * h + Float.floatToRawIntBits(y);
            h = 31 * h + color;
            h = 31 * h + flags;
            h = 31 * h + backgroundColor;
            int slot = (h ^ (h >>> 16)) & PAPI$PREP_SEQ_MASK;
            PapiFontSequenceKey cachedKey = this.papi$prepSeqKeys[slot];
            if (cachedKey != null
                    && this.papi$prepSeqX[slot] == x
                    && this.papi$prepSeqY[slot] == y
                    && this.papi$prepSeqColor[slot] == color
                    && this.papi$prepSeqFlags[slot] == flags
                    && this.papi$prepSeqBg[slot] == backgroundColor
                    && cachedKey.matches(probe)) {
                Font.PreparedText cachedVal = this.papi$prepSeqVals[slot];
                if (cachedVal != null) {
                    return cachedVal;
                }
            }
            PapiFontSequenceKey newKey = PapiFontSequenceKey.copyFrom(probe);
            Font.PreparedText built = original.call(text, x, y, color, dropShadow, includeEmpty, backgroundColor);
            if (built != null) {
                this.papi$prepSeqKeys[slot] = newKey;
                this.papi$prepSeqX[slot] = x;
                this.papi$prepSeqY[slot] = y;
                this.papi$prepSeqColor[slot] = color;
                this.papi$prepSeqFlags[slot] = flags;
                this.papi$prepSeqBg[slot] = backgroundColor;
                this.papi$prepSeqVals[slot] = built;
            }
            return built;
        } finally {
            probe.clearReferences();
        }
    }

    @WrapMethod(method = "prepare8xTextOutline(Lnet/minecraft/util/FormattedCharSequence;FFI)Lnet/minecraft/client/gui/Font$PreparedText;")
    private Font.PreparedText papi$cachedPreparedOutlineText(
            FormattedCharSequence text,
            float x,
            float y,
            int outlineColor,
            Operation<Font.PreparedText> original
    ) {
        if (text == null || text == FormattedCharSequence.EMPTY) {
            return PAPI$EMPTY_PREPARED;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return original.call(text, x, y, outlineColor);
        }
        if (text instanceof PapiSignRenderCache.PreparedLine signLine) {
            if (signLine.isKnownEmpty()) {
                return PAPI$EMPTY_PREPARED;
            }
            return signLine.getOrPrepareOutline((Font) (Object) this, x, y, outlineColor, original);
        }
        PapiFontSequenceKey.Probe probe = this.papi$seqProbe;
        probe.reset();
        try {
            text.accept(probe);
            if (probe.isRejected()) {
                return original.call(text, x, y, outlineColor);
            }
            if (probe.length() == 0) {
                return PAPI$EMPTY_PREPARED;
            }
            int epoch = PapiClientCaches.epoch();
            if (this.papi$outlineEpoch != epoch) {
                Arrays.fill(this.papi$outlineKeys, null);
                Arrays.fill(this.papi$outlineVals, null);
                this.papi$outlineEpoch = epoch;
            }
            int h = probe.hash();
            h = 31 * h + Float.floatToRawIntBits(x);
            h = 31 * h + Float.floatToRawIntBits(y);
            h = 31 * h + outlineColor;
            int slot = (h ^ (h >>> 16)) & PAPI$PREP_OUTLINE_MASK;
            PapiFontSequenceKey cachedKey = this.papi$outlineKeys[slot];
            if (cachedKey != null
                    && this.papi$outlineX[slot] == x
                    && this.papi$outlineY[slot] == y
                    && this.papi$outlineColor[slot] == outlineColor
                    && cachedKey.matches(probe)) {
                Font.PreparedText cachedVal = this.papi$outlineVals[slot];
                if (cachedVal != null) {
                    return cachedVal;
                }
            }
            PapiFontSequenceKey newKey = PapiFontSequenceKey.copyFrom(probe);
            Font.PreparedText built = original.call(text, x, y, outlineColor);
            if (built != null) {
                this.papi$outlineKeys[slot] = newKey;
                this.papi$outlineX[slot] = x;
                this.papi$outlineY[slot] = y;
                this.papi$outlineColor[slot] = outlineColor;
                this.papi$outlineVals[slot] = built;
            }
            return built;
        } finally {
            probe.clearReferences();
        }
    }
}
