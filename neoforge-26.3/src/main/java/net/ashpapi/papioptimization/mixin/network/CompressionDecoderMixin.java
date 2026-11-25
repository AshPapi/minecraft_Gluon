package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.Inflater;
import net.ashpapi.papioptimization.network.PapiCompressionPool;
import net.ashpapi.papioptimization.network.PapiQuietDecoderException;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompressionDecoder.class, priority = 1500)
public abstract class CompressionDecoderMixin {

    @Unique
    private static final int PAPI$HARD_MAX_UNCOMPRESSED = 134_217_728;

    @Unique
    private static final int PAPI$PROTOCOL_MAX_UNCOMPRESSED = 8_388_608;

    @Shadow
    @Final
    private Inflater inflater;

    @Shadow
    private int threshold;

    @Shadow
    private boolean validateDecompressed;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void papi$releaseUnusedVanillaInflater(int threshold, boolean validateDecompressed, CallbackInfo ci) {
        if (this.inflater != null) {
            this.inflater.end();
        }
    }

    @WrapMethod(method = "decode")
    private void papi$pooledZeroCopyDecode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out,
            Operation<Void> original
    ) throws Exception {
        if (in.readableBytes() == 0) {
            return;
        }
        int uncompressedSize = VarInt.read(in);
        if (uncompressedSize == 0) {
            out.add(in.readRetainedSlice(in.readableBytes()));
            return;
        }
        if (uncompressedSize < 0 || uncompressedSize > PAPI$HARD_MAX_UNCOMPRESSED) {
            throw new PapiQuietDecoderException(
                    "Badly compressed packet - size of " + uncompressedSize + " is out of bounds"
            );
        }
        if (this.validateDecompressed) {
            if (uncompressedSize < this.threshold) {
                throw new PapiQuietDecoderException(
                        "Badly compressed packet - size of " + uncompressedSize + " is below server threshold of " + this.threshold
                );
            }
            if (uncompressedSize > PAPI$PROTOCOL_MAX_UNCOMPRESSED) {
                throw new PapiQuietDecoderException(
                        "Badly compressed packet - size of " + uncompressedSize + " is larger than protocol maximum of " + PAPI$PROTOCOL_MAX_UNCOMPRESSED
                );
            }
        }

        Inflater pooledInflater = PapiCompressionPool.acquireInflater();
        ByteBuf tempIn = null;
        ByteBuf target = null;
        try {
            int readable = in.readableBytes();
            if (in.isDirect() && in.nioBufferCount() == 1) {
                ByteBuffer src = in.internalNioBuffer(in.readerIndex(), readable);
                in.skipBytes(readable);
                pooledInflater.setInput(src);
            } else {
                tempIn = ctx.alloc().directBuffer(readable);
                in.readBytes(tempIn, readable);
                pooledInflater.setInput(tempIn.internalNioBuffer(0, readable));
            }

            if (uncompressedSize <= PAPI$PROTOCOL_MAX_UNCOMPRESSED) {
                target = ctx.alloc().directBuffer(uncompressedSize);
            } else {
                int initialCap = Math.min(uncompressedSize, Math.max(readable << 2, 65536));
                target = ctx.alloc().directBuffer(initialCap, uncompressedSize);
            }

            int totalWritten = 0;
            while (totalWritten < uncompressedSize && !pooledInflater.finished()) {
                int remaining = uncompressedSize - totalWritten;
                int ensure = Math.min(remaining, Math.max(target.writableBytes(), 65536));
                target.ensureWritable(ensure);
                int wi = target.writerIndex();
                int writable = Math.min(remaining, target.writableBytes());
                ByteBuffer dst = target.internalNioBuffer(wi, writable);
                int startPos = dst.position();
                int produced = pooledInflater.inflate(dst);
                int delta = dst.position() - startPos;
                if (delta > 0) {
                    target.writerIndex(wi + delta);
                    totalWritten += delta;
                } else if (produced == 0 && (pooledInflater.needsInput() || pooledInflater.needsDictionary())) {
                    break;
                }
            }

            if (totalWritten != uncompressedSize) {
                throw new PapiQuietDecoderException(
                        "Badly compressed packet - actual length of uncompressed payload " + totalWritten + " does not match declared size " + uncompressedSize
                );
            }

            if (!pooledInflater.finished() && !pooledInflater.needsInput()) {
                byte[] probe = new byte[1];
                int extra = pooledInflater.inflate(probe, 0, 1);
                if (extra > 0) {
                    throw new PapiQuietDecoderException(
                            "Badly compressed packet - stream contains trailing uncompressed data beyond " + uncompressedSize
                    );
                }
            }

            out.add(target);
            target = null;
        } finally {
            if (tempIn != null) {
                tempIn.release();
            }
            if (target != null) {
                target.release();
            }
            PapiCompressionPool.releaseInflater(pooledInflater);
        }
    }
}
