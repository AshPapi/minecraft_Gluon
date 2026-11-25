package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import net.ashpapi.papioptimization.network.PapiCompressionPool;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompressionEncoder.class, priority = 1500)
public abstract class CompressionEncoderMixin extends MessageToByteEncoder<ByteBuf> {

    @Unique
    private static final int PAPI$MAX_CHUNK = 65536;

    @Shadow
    @Final
    private byte[] encodeBuf;

    @Shadow
    @Final
    private Deflater deflater;

    @Shadow
    private int threshold;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void papi$releaseUnusedVanillaDeflater(int threshold, CallbackInfo ci) {
        if (this.deflater != null) {
            this.deflater.end();
        }
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        int readable = msg.readableBytes();
        if (readable < this.threshold) {
            int exact = 1 + readable;
            return preferDirect ? ctx.alloc().directBuffer(exact) : ctx.alloc().heapBuffer(exact);
        }
        int estimated = readable <= PAPI$MAX_CHUNK ? (readable + 16) : Math.max(readable >>> 2, 32768);
        return preferDirect ? ctx.alloc().directBuffer(estimated) : ctx.alloc().heapBuffer(estimated);
    }

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V")
    private void papi$zeroAllocEncode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            ByteBuf out,
            Operation<Void> original
    ) {
        int readable = in.readableBytes();
        if (readable > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + readable + ", should be less than 8388608)");
        }
        if (readable < this.threshold) {
            out.ensureWritable(1 + readable);
            out.writeByte(0);
            out.writeBytes(in, readable);
            return;
        }
        VarInt.write(out, readable);
        Deflater pooledDeflater = PapiCompressionPool.acquireDeflater();
        ByteBuf tempIn = null;
        try {
            if (in.isDirect() && in.nioBufferCount() == 1) {
                ByteBuffer srcBuf = in.internalNioBuffer(in.readerIndex(), readable);
                in.skipBytes(readable);
                pooledDeflater.setInput(srcBuf);
            } else {
                tempIn = ctx.alloc().directBuffer(readable);
                in.readBytes(tempIn, readable);
                pooledDeflater.setInput(tempIn.internalNioBuffer(0, readable));
            }
            pooledDeflater.finish();
            boolean directOut = out.isDirect() && !(out instanceof CompositeByteBuf) && out.nioBufferCount() == 1;
            int chunk = Math.min(Math.max(readable + 16, 256), PAPI$MAX_CHUNK);
            while (!pooledDeflater.finished()) {
                if (directOut) {
                    if (out.writableBytes() < 64) {
                        out.ensureWritable(chunk);
                    }
                    if (out.nioBufferCount() == 1) {
                        int wi = out.writerIndex();
                        int avail = out.writableBytes();
                        ByteBuffer dstBuf = out.internalNioBuffer(wi, avail);
                        int startPos = dstBuf.position();
                        int written = pooledDeflater.deflate(dstBuf);
                        int delta = dstBuf.position() - startPos;
                        if (delta > 0) {
                            out.writerIndex(wi + delta);
                        } else if (written == 0 && !pooledDeflater.finished()) {
                            out.ensureWritable(chunk);
                            int fallback = pooledDeflater.deflate(this.encodeBuf);
                            if (fallback > 0) {
                                out.writeBytes(this.encodeBuf, 0, fallback);
                            }
                        }
                        chunk = Math.min(chunk << 1, PAPI$MAX_CHUNK);
                        continue;
                    }
                    directOut = false;
                }
                int written = pooledDeflater.deflate(this.encodeBuf);
                if (written > 0) {
                    out.writeBytes(this.encodeBuf, 0, written);
                }
            }
        } finally {
            if (tempIn != null) {
                tempIn.release();
            }
            PapiCompressionPool.releaseDeflater(pooledDeflater);
        }
    }
}
