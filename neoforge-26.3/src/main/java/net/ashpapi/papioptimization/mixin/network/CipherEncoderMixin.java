package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import javax.crypto.Cipher;
import net.ashpapi.papioptimization.network.PapiCipherScratch;
import net.minecraft.network.CipherBase;
import net.minecraft.network.CipherEncoder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = CipherEncoder.class, priority = 1500)
public abstract class CipherEncoderMixin extends MessageToByteEncoder<ByteBuf> {

    @Shadow
    @Final
    private CipherBase cipher;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buf) {
            int readable = buf.readableBytes();
            Cipher jce = ((CipherBaseAccessor) this.cipher).papi$getCipher();
            if (readable > 0 && !buf.isReadOnly() && buf.refCnt() == 1 && jce.getOutputSize(readable) == readable) {
                try {
                    PapiCipherScratch.cryptInPlace(jce, buf);
                    ctx.write(buf, promise);
                    buf = null;
                    return;
                } catch (EncoderException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new EncoderException(t);
                } finally {
                    if (buf != null) {
                        buf.release();
                    }
                }
            }
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        Cipher jce = ((CipherBaseAccessor) this.cipher).papi$getCipher();
        return ctx.alloc().directBuffer(jce.getOutputSize(msg.readableBytes()));
    }

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V")
    private void papi$fastEncode(
            ChannelHandlerContext ctx,
            ByteBuf msg,
            ByteBuf out,
            Operation<Void> original
    ) throws Exception {
        Cipher jce = ((CipherBaseAccessor) this.cipher).papi$getCipher();
        PapiCipherScratch.cryptToOut(jce, msg, out);
    }
}
