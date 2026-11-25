package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.ashpapi.papioptimization.network.PapiNettyExceptions;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Varint21FrameDecoder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Varint21FrameDecoder.class, priority = 1500)
public abstract class Varint21FrameDecoderMixin {

    @Shadow
    @Final
    private BandwidthDebugMonitor monitor;

    @WrapMethod(method = "decode")
    private void papi$fastSwarDecode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out,
            Operation<Void> original
    ) {
        if (!ctx.channel().isActive()) {
            in.clear();
            return;
        }
        int readable = in.readableBytes();
        if (readable == 0) {
            return;
        }
        int ri = in.readerIndex();
        int headerLen;
        int packetLen;
        if (readable >= 4) {
            int word = in.getIntLE(ri);
            int stopMask = ~word & 0x808080;
            if (stopMask == 0) {
                throw PapiNettyExceptions.VARINT_TOO_BIG;
            }
            int tz = Integer.numberOfTrailingZeros(stopMask);
            headerLen = (tz + 1) >>> 3;
            int masked = word & ((1 << tz) - 1);
            packetLen = (masked & 0x7F) | ((masked & 0x7F00) >>> 1) | ((masked & 0x7F0000) >>> 2);
        } else {
            byte b0 = in.getByte(ri);
            if (b0 >= 0) {
                headerLen = 1;
                packetLen = b0;
            } else {
                if (readable < 2) {
                    return;
                }
                byte b1 = in.getByte(ri + 1);
                if (b1 >= 0) {
                    headerLen = 2;
                    packetLen = (b0 & 0x7F) | (b1 << 7);
                } else {
                    if (readable < 3) {
                        return;
                    }
                    byte b2 = in.getByte(ri + 2);
                    if (b2 >= 0) {
                        headerLen = 3;
                        packetLen = (b0 & 0x7F) | ((b1 & 0x7F) << 7) | (b2 << 14);
                    } else {
                        throw PapiNettyExceptions.VARINT_TOO_BIG;
                    }
                }
            }
        }
        if (packetLen <= 0) {
            throw PapiNettyExceptions.EMPTY_FRAME;
        }
        int totalFrameBytes = headerLen + packetLen;
        if (readable < totalFrameBytes) {
            return;
        }
        in.readerIndex(ri + headerLen);
        if (this.monitor != null) {
            this.monitor.onReceive(totalFrameBytes);
        }
        out.add(in.readRetainedSlice(packetLen));
    }
}
