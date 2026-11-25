package net.ashpapi.papioptimization.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.server.network.LegacyQueryHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LegacyQueryHandler.class, priority = 1500)
public abstract class LegacyQueryHandlerMixin {

    @Unique
    private boolean papi$handled;

    @Inject(method = "channelRead", at = @At("HEAD"), cancellable = true)
    private void papi$dropWhenClosedOrHandled(ChannelHandlerContext ctx, Object msg, CallbackInfo ci) {
        if (this.papi$handled || !ctx.channel().isActive()) {
            ReferenceCountUtil.release(msg);
            ci.cancel();
        }
    }

    @Inject(
            method = "channelRead",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/LegacyQueryHandler;sendFlushAndClose(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;)V"
            )
    )
    private void papi$markHandledBeforeReply(ChannelHandlerContext ctx, Object msg, CallbackInfo ci) {
        this.papi$handled = true;
    }

    @Inject(method = "sendFlushAndClose", at = @At("HEAD"))
    private static void papi$disableAutoReadOnLegacyReply(ChannelHandlerContext ctx, ByteBuf data, CallbackInfo ci) {
        ctx.channel().config().setAutoRead(false);
    }
}
