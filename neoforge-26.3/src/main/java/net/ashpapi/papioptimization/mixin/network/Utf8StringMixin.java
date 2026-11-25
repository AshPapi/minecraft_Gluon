package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Utf8String.class, priority = 1500)
public abstract class Utf8StringMixin {

    @WrapMethod(method = "write")
    private static void papi$zeroAllocWrite(
            ByteBuf buf,
            CharSequence input,
            int maxLength,
            Operation<Void> original
    ) {
        int charLen = input.length();
        if (charLen > maxLength) {
            throw new EncoderException("String too big (was " + charLen + " characters, max " + maxLength + ")");
        }
        long maxAllowedBytes = (long) maxLength * 3L;
        if (charLen <= 42) {
            int maxPossibleBytes = ByteBufUtil.utf8MaxBytes(charLen);
            buf.ensureWritable(1 + maxPossibleBytes);
            int lengthIdx = buf.writerIndex();
            buf.writeByte(0);
            int written;
            try {
                written = ByteBufUtil.reserveAndWriteUtf8(buf, input, 0, charLen, maxPossibleBytes);
            } catch (RuntimeException e) {
                buf.writerIndex(lengthIdx);
                throw e;
            }
            if (written > maxAllowedBytes) {
                buf.writerIndex(lengthIdx);
                throw new EncoderException("String too big (was " + written + " bytes encoded, max " + maxAllowedBytes + ")");
            }
            buf.setByte(lengthIdx, written);
            return;
        }
        if (charLen >= 128 && charLen <= 512) {
            int maxPossibleBytes = ByteBufUtil.utf8MaxBytes(charLen);
            buf.ensureWritable(2 + maxPossibleBytes);
            int lengthIdx = buf.writerIndex();
            buf.writeShortLE(0);
            int written;
            try {
                written = ByteBufUtil.reserveAndWriteUtf8(buf, input, 0, charLen, maxPossibleBytes);
            } catch (RuntimeException e) {
                buf.writerIndex(lengthIdx);
                throw e;
            }
            if (written > maxAllowedBytes) {
                buf.writerIndex(lengthIdx);
                throw new EncoderException("String too big (was " + written + " bytes encoded, max " + maxAllowedBytes + ")");
            }
            buf.setShortLE(lengthIdx, (written & 0x7F) | 0x80 | ((written & 0x3F80) << 1));
            return;
        }
        int encodedBytes = ByteBufUtil.utf8Bytes(input, 0, charLen);
        if (encodedBytes > maxAllowedBytes) {
            throw new EncoderException("String too big (was " + encodedBytes + " bytes encoded, max " + maxAllowedBytes + ")");
        }
        int headerBytes = VarInt.getByteSize(encodedBytes);
        buf.ensureWritable(headerBytes + encodedBytes);
        VarInt.write(buf, encodedBytes);
        ByteBufUtil.reserveAndWriteUtf8(buf, input, 0, charLen, encodedBytes);
    }
}
