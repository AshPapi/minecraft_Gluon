package net.ashpapi.papioptimization.network;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.FastThreadLocal;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public final class PapiCipherScratch {

    public static final int CHUNK_BYTES = 16384;

    private static final FastThreadLocal<ScratchBuffers> SCRATCH = new FastThreadLocal<>() {
        @Override
        protected ScratchBuffers initialValue() {
            return new ScratchBuffers();
        }
    };

    private PapiCipherScratch() {
    }

    public static void cryptInPlace(Cipher cipher, ByteBuf buf) throws ShortBufferException {
        int remaining = buf.readableBytes();
        if (remaining <= 0) {
            return;
        }
        ScratchBuffers s = SCRATCH.get();
        byte[] inArr = s.inBuf;
        byte[] outArr = s.outBuf;
        int readPos = buf.readerIndex();
        int writePos = readPos;
        while (remaining > 0) {
            int step = Math.min(remaining, CHUNK_BYTES);
            buf.getBytes(readPos, inArr, 0, step);
            int produced = cipher.update(inArr, 0, step, outArr, 0);
            if (produced > 0) {
                buf.setBytes(writePos, outArr, 0, produced);
                writePos += produced;
            }
            readPos += step;
            remaining -= step;
        }
    }

    public static void cryptToOut(Cipher cipher, ByteBuf in, ByteBuf out) throws ShortBufferException {
        int remaining = in.readableBytes();
        if (remaining <= 0) {
            return;
        }
        ScratchBuffers s = SCRATCH.get();
        byte[] inArr = s.inBuf;
        byte[] outArr = s.outBuf;
        out.ensureWritable(cipher.getOutputSize(remaining));
        while (remaining > 0) {
            int step = Math.min(remaining, CHUNK_BYTES);
            in.readBytes(inArr, 0, step);
            int produced = cipher.update(inArr, 0, step, outArr, 0);
            if (produced > 0) {
                out.writeBytes(outArr, 0, produced);
            }
            remaining -= step;
        }
    }

    private static final class ScratchBuffers {
        final byte[] inBuf = new byte[CHUNK_BYTES];
        final byte[] outBuf = new byte[CHUNK_BYTES];
    }
}
