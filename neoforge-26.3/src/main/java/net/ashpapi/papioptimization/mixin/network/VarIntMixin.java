package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = VarInt.class, priority = 1500)
public abstract class VarIntMixin {

    @WrapMethod(method = "getByteSize")
    private static int papi$fastGetByteSize(int value, Operation<Integer> original) {
        return (1406 - Integer.numberOfLeadingZeros(value | 1) * 37) >>> 8;
    }

    @WrapMethod(method = "read")
    private static int papi$fastRead(ByteBuf buf, Operation<Integer> original) {
        byte b0 = buf.readByte();
        if (b0 >= 0) {
            return b0;
        }
        int result = b0 & 0x7F;
        byte b1 = buf.readByte();
        if (b1 >= 0) {
            return result | (b1 << 7);
        }
        result |= (b1 & 0x7F) << 7;
        byte b2 = buf.readByte();
        if (b2 >= 0) {
            return result | (b2 << 14);
        }
        result |= (b2 & 0x7F) << 14;
        byte b3 = buf.readByte();
        if (b3 >= 0) {
            return result | (b3 << 21);
        }
        result |= (b3 & 0x7F) << 21;
        byte b4 = buf.readByte();
        if (b4 >= 0) {
            return result | (b4 << 28);
        }
        throw new RuntimeException("VarInt too big");
    }

    @WrapMethod(method = "write")
    private static ByteBuf papi$fastWrite(ByteBuf buf, int value, Operation<ByteBuf> original) {
        if ((value & ~0x7F) == 0) {
            buf.writeByte(value);
            return buf;
        }
        if ((value & ~0x3FFF) == 0) {
            buf.writeShortLE((value & 0x7F) | 0x80 | ((value & 0x3F80) << 1));
            return buf;
        }
        papi$writeLargeVarInt(buf, value);
        return buf;
    }

    @Unique
    private static void papi$writeLargeVarInt(ByteBuf buf, int value) {
        if ((value & ~0x1FFFFF) == 0) {
            int encoded = (value & 0x7F)
                    | ((value & 0x3F80) << 1)
                    | ((value & 0x1FC000) << 2)
                    | 0x008080;
            buf.writeMediumLE(encoded);
        } else if ((value & ~0x0FFFFFFF) == 0) {
            int encoded = (value & 0x7F)
                    | ((value & 0x3F80) << 1)
                    | ((value & 0x1FC000) << 2)
                    | ((value & 0x0FE00000) << 3)
                    | 0x00808080;
            buf.writeIntLE(encoded);
        } else {
            int low = (value & 0x7F)
                    | ((value & 0x3F80) << 1)
                    | ((value & 0x1FC000) << 2)
                    | ((value & 0x0FE00000) << 3)
                    | 0x80808080;
            buf.writeIntLE(low);
            buf.writeByte(value >>> 28);
        }
    }
}
