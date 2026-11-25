package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import javax.crypto.Cipher;
import net.ashpapi.papioptimization.network.PapiCipherScratch;
import net.minecraft.network.CipherBase;
import net.minecraft.network.CipherDecoder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = CipherDecoder.class, priority = 1500)
public abstract class CipherDecoderMixin {

    @Shadow
    @Final
    private CipherBase cipher;

    @WrapMethod(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V")
    private void papi$inPlaceDecode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out,
            Operation<Void> original
    ) throws Exception {
        int readable = in.readableBytes();
        Cipher jce = ((CipherBaseAccessor) this.cipher).papi$getCipher();
        int outSize = jce.getOutputSize(readable);
        if (readable == 0 && outSize == 0) {
            out.add(in.readRetainedSlice(0));
            return;
        }
        if (in.isDirect() && !in.isReadOnly() && in.refCnt() == 1 && outSize == readable) {
            PapiCipherScratch.cryptInPlace(jce, in);
            out.add(in.readRetainedSlice(readable));
            return;
        }
        ByteBuf dst = ctx.alloc().directBuffer(outSize);
        try {
            PapiCipherScratch.cryptToOut(jce, in, dst);
            out.add(dst);
            dst = null;
        } finally {
            if (dst != null) {
                dst.release();
            }
        }
    }
}
