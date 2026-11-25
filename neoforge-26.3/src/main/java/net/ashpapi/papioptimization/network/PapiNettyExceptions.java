package net.ashpapi.papioptimization.network;

import io.netty.handler.codec.CorruptedFrameException;

public final class PapiNettyExceptions {

    public static final CorruptedFrameException VARINT_TOO_BIG =
            new QuietCorruptedFrameException("length wider than 21-bit");

    public static final CorruptedFrameException EMPTY_FRAME =
            new QuietCorruptedFrameException("Frame length cannot be zero");

    private PapiNettyExceptions() {
    }

    private static final class QuietCorruptedFrameException extends CorruptedFrameException {

        QuietCorruptedFrameException(String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
