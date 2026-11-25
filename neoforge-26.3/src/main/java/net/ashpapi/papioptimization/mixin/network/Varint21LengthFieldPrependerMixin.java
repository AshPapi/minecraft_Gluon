package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.VarInt;
import net.minecraft.network.Varint21LengthFieldPrepender;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Varint21LengthFieldPrepender.class, priority = 1500)
public abstract class Varint21LengthFieldPrependerMixin extends MessageToByteEncoder<ByteBuf> {

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        int bodyLen = msg.readableBytes();
        int totalLen = VarInt.getByteSize(bodyLen) + bodyLen;
        return preferDirect ? ctx.alloc().directBuffer(totalLen) : ctx.alloc().heapBuffer(totalLen);
    }

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V")
    private void papi$fastEncodeLength(
            ChannelHandlerContext ctx,
            ByteBuf msg,
            ByteBuf out,
            Operation<Void> original
    ) {
        int bodyLen = msg.readableBytes();
        if ((bodyLen & ~0x1FFFFF) != 0) {
            original.call(ctx, msg, out);
            return;
        }
        if (bodyLen < 0x80) {
            out.ensureWritable(1 + bodyLen);
            out.writeByte(bodyLen);
        } else if (bodyLen < 0x4000) {
            out.ensureWritable(2 + bodyLen);
            out.writeShortLE((bodyLen & 0x7F) | 0x80 | ((bodyLen & 0x3F80) << 1));
        } else {
            out.ensureWritable(3 + bodyLen);
            out.writeMediumLE((bodyLen & 0x7F) | ((bodyLen & 0x3F80) << 1) | ((bodyLen & 0x1FC000) << 2) | 0x008080);
        }
        out.writeBytes(msg, bodyLen);
    }
}
