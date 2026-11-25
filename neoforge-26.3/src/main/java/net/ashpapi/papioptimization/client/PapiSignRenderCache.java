package net.ashpapi.papioptimization.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiSignTextCache;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PapiSignRenderCache {

    private final FormattedCharSequence[] sourceRef;
    private final boolean filtered;
    private final FormattedCharSequence[] wrappedLines;

    private PapiSignRenderCache(FormattedCharSequence[] raw, boolean filtered) {
        this.sourceRef = raw;
        this.filtered = filtered;
        int len = raw.length;
        FormattedCharSequence[] wrapped = new FormattedCharSequence[len];
        for (int i = 0; i < len; i++) {
            FormattedCharSequence seq = raw[i];
            if (seq == null || seq == FormattedCharSequence.EMPTY) {
                wrapped[i] = FormattedCharSequence.EMPTY;
            } else {
                wrapped[i] = new PreparedLine(seq);
            }
        }
        this.wrappedLines = wrapped;
    }

    public static FormattedCharSequence[] getOrWrap(
            PapiSignTextCache owner,
            FormattedCharSequence[] raw,
            boolean filtered
    ) {
        Object existing = owner.papi$getClientRenderCache();
        if (existing instanceof PapiSignRenderCache cached
                && cached.sourceRef == raw
                && cached.filtered == filtered) {
            return cached.wrappedLines;
        }
        PapiSignRenderCache created = new PapiSignRenderCache(raw, filtered);
        owner.papi$setClientRenderCache(created);
        return created.wrappedLines;
    }

    @OnlyIn(Dist.CLIENT)
    public static final class PreparedLine implements FormattedCharSequence {

        private static final byte STATE_UNPROBED = 0;
        private static final byte STATE_EMPTY = 1;
        private static final byte STATE_STATIC = 2;
        private static final byte STATE_OBFUSCATED = 3;

        private final FormattedCharSequence delegate;
        private byte state = STATE_UNPROBED;

        private int widthEpoch = -1;
        private @Nullable Font widthFont;
        private int cachedWidth;

        private int mainEpoch = -1;
        private @Nullable Font mainFont;
        private float mainX;
        private float mainY;
        private int mainColor;
        private byte mainFlags;
        private int mainBgColor;
        private Font.@Nullable PreparedText cachedMain;

        private int outlineEpoch = -1;
        private @Nullable Font outlineFont;
        private float outlineX;
        private float outlineY;
        private int outlineColor;
        private Font.@Nullable PreparedText cachedOutline;

        PreparedLine(FormattedCharSequence delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            return this.delegate.accept(sink);
        }

        private byte ensureState() {
            byte s = this.state;
            if (s != STATE_UNPROBED) {
                return s;
            }
            boolean[] flags = new boolean[2];
            this.delegate.accept((index, style, codePoint) -> {
                flags[0] = true;
                if (style != null && style.isObfuscated()) {
                    flags[1] = true;
                    return false;
                }
                return true;
            });
            byte resolved = flags[1] ? STATE_OBFUSCATED : (flags[0] ? STATE_STATIC : STATE_EMPTY);
            this.state = resolved;
            return resolved;
        }

        public boolean isKnownEmpty() {
            return ensureState() == STATE_EMPTY;
        }

        public int getOrComputeWidth(Font font, Operation<Integer> original) {
            byte st = ensureState();
            if (st == STATE_EMPTY) {
                return 0;
            }
            if (st == STATE_OBFUSCATED) {
                return original.call(font, this.delegate);
            }
            int epoch = PapiClientCaches.epoch();
            if (this.widthEpoch == epoch && this.widthFont == font) {
                return this.cachedWidth;
            }
            int computed = original.call(font, this.delegate);
            this.widthEpoch = epoch;
            this.widthFont = font;
            this.cachedWidth = computed;
            return computed;
        }

        public Font.PreparedText getOrPrepareMain(
                Font font,
                float x,
                float y,
                int color,
                boolean dropShadow,
                boolean includeEmpty,
                int backgroundColor,
                Operation<Font.PreparedText> original
        ) {
            byte st = ensureState();
            if (st == STATE_OBFUSCATED) {
                return original.call(this.delegate, x, y, color, dropShadow, includeEmpty, backgroundColor);
            }
            int epoch = PapiClientCaches.epoch();
            byte flags = (byte) ((dropShadow ? 1 : 0) | (includeEmpty ? 2 : 0));
            Font.PreparedText cached = this.cachedMain;
            if (cached != null
                    && this.mainEpoch == epoch
                    && this.mainFont == font
                    && this.mainX == x
                    && this.mainY == y
                    && this.mainColor == color
                    && this.mainFlags == flags
                    && this.mainBgColor == backgroundColor) {
                return cached;
            }
            Font.PreparedText built = original.call(this.delegate, x, y, color, dropShadow, includeEmpty, backgroundColor);
            if (built != null) {
                this.mainEpoch = epoch;
                this.mainFont = font;
                this.mainX = x;
                this.mainY = y;
                this.mainColor = color;
                this.mainFlags = flags;
                this.mainBgColor = backgroundColor;
                this.cachedMain = built;
            }
            return built;
        }

        public Font.PreparedText getOrPrepareOutline(
                Font font,
                float x,
                float y,
                int outlineColor,
                Operation<Font.PreparedText> original
        ) {
            byte st = ensureState();
            if (st == STATE_OBFUSCATED) {
                return original.call(this.delegate, x, y, outlineColor);
            }
            int epoch = PapiClientCaches.epoch();
            Font.PreparedText cached = this.cachedOutline;
            if (cached != null
                    && this.outlineEpoch == epoch
                    && this.outlineFont == font
                    && this.outlineX == x
                    && this.outlineY == y
                    && this.outlineColor == outlineColor) {
                return cached;
            }
            Font.PreparedText built = original.call(this.delegate, x, y, outlineColor);
            if (built != null) {
                this.outlineEpoch = epoch;
                this.outlineFont = font;
                this.outlineX = x;
                this.outlineY = y;
                this.outlineColor = outlineColor;
                this.cachedOutline = built;
            }
            return built;
        }
    }
}
