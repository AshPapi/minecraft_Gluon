package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.bytes.ByteList;
import java.nio.IntBuffer;
import net.ashpapi.papioptimization.client.CompactByteLineData;
import net.ashpapi.papioptimization.client.CompactShortLineData;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = UnihexProvider.class, priority = 1500)
public abstract class UnihexProviderFastMixin {

    @Shadow
    private static int decodeHex(int lineNum, ByteList list, int index) {
        return 0;
    }

    @WrapOperation(
        method = "readFromStream",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/providers/UnihexProvider$ByteContents;read(ILit/unimi/dsi/fastutil/bytes/ByteList;)Lnet/minecraft/client/gui/font/providers/UnihexProvider$LineData;"
        )
    )
    private static UnihexProvider.LineData papi$readCompactBytes(int lineNum, ByteList list, Operation<UnihexProvider.LineData> original) {
        long low = 0L;
        long high = 0L;
        int pos = 0;
        for (int i = 0; i < 8; i++) {
            int d0 = decodeHex(lineNum, list, pos++);
            int d1 = decodeHex(lineNum, list, pos++);
            long b = (long) ((d0 << 4) | d1) & 0xFFL;
            low |= (b << (i << 3));
        }
        for (int i = 0; i < 8; i++) {
            int d0 = decodeHex(lineNum, list, pos++);
            int d1 = decodeHex(lineNum, list, pos++);
            long b = (long) ((d0 << 4) | d1) & 0xFFL;
            high |= (b << (i << 3));
        }
        return new CompactByteLineData(low, high);
    }

    @WrapOperation(
        method = "readFromStream",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/providers/UnihexProvider$ShortContents;read(ILit/unimi/dsi/fastutil/bytes/ByteList;)Lnet/minecraft/client/gui/font/providers/UnihexProvider$LineData;"
        )
    )
    private static UnihexProvider.LineData papi$readCompactShorts(int lineNum, ByteList list, Operation<UnihexProvider.LineData> original) {
        long w0 = 0L;
        long w1 = 0L;
        long w2 = 0L;
        long w3 = 0L;
        int pos = 0;
        for (int i = 0; i < 16; i++) {
            int d0 = decodeHex(lineNum, list, pos++);
            int d1 = decodeHex(lineNum, list, pos++);
            int d2 = decodeHex(lineNum, list, pos++);
            int d3 = decodeHex(lineNum, list, pos++);
            long s = (long) ((d0 << 12) | (d1 << 8) | (d2 << 4) | d3) & 0xFFFFL;
            int shift = (i & 3) << 4;
            switch (i >>> 2) {
                case 0 -> w0 |= (s << shift);
                case 1 -> w1 |= (s << shift);
                case 2 -> w2 |= (s << shift);
                default -> w3 |= (s << shift);
            }
        }
        return new CompactShortLineData(w0, w1, w2, w3);
    }

    @Inject(method = "unpackBitsToBytes(Ljava/nio/IntBuffer;III)V", at = @At("HEAD"), cancellable = true)
    private static void papi$fastUnpackBitsToBytes(IntBuffer out, int value, int left, int right, CallbackInfo ci) {
        int startBit = 31 - left;
        int endBit = 31 - right;
        for (int bit = startBit; bit >= endBit; bit--) {
            if ((bit & ~31) == 0) {
                out.put(-((value >>> bit) & 1));
            } else {
                out.put(0);
            }
        }
        ci.cancel();
    }
}
