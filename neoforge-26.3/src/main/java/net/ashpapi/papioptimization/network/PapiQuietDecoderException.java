package net.ashpapi.papioptimization.network;

import io.netty.handler.codec.DecoderException;

public final class PapiQuietDecoderException extends DecoderException {

    public PapiQuietDecoderException(String message) {
        super(message);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
