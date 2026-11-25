package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = VarLong.class, priority = 1500)
public abstract class VarLongMixin {

    @WrapMethod(method = "getByteSize")
    private static int papi$fastGetByteSize(long value, Operation<Integer> original) {
        return (5120 - Long.numberOfLeadingZeros(value | 1L) * 73) >>> 9;
    }

    @WrapMethod(method = "write")
    private static ByteBuf papi$fastWrite(ByteBuf buf, long value, Operation<ByteBuf> original) {
        if ((value & ~0x7FL) == 0L) {
            buf.writeByte((int) value);
            return buf;
        }
        if ((value & ~0x3FFFL) == 0L) {
            int v = (int) value;
            buf.writeShortLE((v & 0x7F) | 0x80 | ((v & 0x3F80) << 1));
            return buf;
        }
        papi$writeLargeVarLong(buf, value);
        return buf;
    }

    @Unique
    private static void papi$writeLargeVarLong(ByteBuf buf, long value) {
        while ((value & ~0x3FFFL) != 0L) {
            int v = (int) value;
            buf.writeShortLE((v & 0x7F) | ((v & 0x3F80) << 1) | 0x8080);
            value >>>= 14;
        }
        if ((value & ~0x7FL) == 0L) {
            buf.writeByte((int) value);
        } else {
            int v = (int) value;
            buf.writeShortLE((v & 0x7F) | 0x80 | ((v & 0x3F80) << 1));
        }
    }
}
