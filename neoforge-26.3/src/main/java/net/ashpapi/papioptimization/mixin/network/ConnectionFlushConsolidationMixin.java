package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOutboundHandler;
import java.util.concurrent.atomic.AtomicInteger;
import net.ashpapi.papioptimization.network.PapiAutoFlush;
import net.minecraft.network.Connection;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Connection.class, priority = 1500)
public abstract class ConnectionFlushConsolidationMixin implements PapiAutoFlush {

    @Unique
    private static final Varint21LengthFieldPrepender PAPI$SHARED_PREPENDER = new Varint21LengthFieldPrepender();

    @Shadow
    public abstract boolean isMemoryConnection();

    @Shadow
    public abstract boolean isConnected();

    @Shadow
    public abstract void flushChannel();

    @Unique
    private final AtomicInteger papi$suspendDepth = new AtomicInteger(0);

    @Unique
    private volatile boolean papi$hasDeferredFlush;

    @Override
    public void papi$suspendAutoFlush() {
        this.papi$suspendDepth.getAndIncrement();
    }

    @Override
    public void papi$resumeAutoFlush() {
        int rem = this.papi$suspendDepth.decrementAndGet();
        if (rem <= 0) {
            if (rem < 0) {
                this.papi$suspendDepth.set(0);
            }
            if (this.papi$hasDeferredFlush) {
                this.papi$hasDeferredFlush = false;
                if (this.isConnected()) {
                    this.flushChannel();
                }
            }
        }
    }

    @WrapMethod(method = "createFrameEncoder")
    private static ChannelOutboundHandler papi$sharePrepender(
            boolean local,
            Operation<ChannelOutboundHandler> original
    ) {
        if (!local) {
            return PAPI$SHARED_PREPENDER;
        }
        return original.call(true);
    }

    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    private void papi$consolidatePhaseFlush(
            Packet<?> packet,
            @Nullable ChannelFutureListener listener,
            boolean flush,
            Operation<Void> original
    ) {
        if (this.papi$suspendDepth.getAcquire() == 0) {
            original.call(packet, listener, flush);
            return;
        }
        if (flush && listener == null && !this.isMemoryConnection() && this.isConnected()) {
            this.papi$hasDeferredFlush = true;
            original.call(packet, null, false);
            return;
        }
        if (flush) {
            this.papi$hasDeferredFlush = false;
        }
        original.call(packet, listener, flush);
    }

    @Inject(method = "flushChannel", at = @At("HEAD"))
    private void papi$clearDeferredOnExplicitFlush(CallbackInfo ci) {
        this.papi$hasDeferredFlush = false;
    }
}
