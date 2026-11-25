package net.ashpapi.papioptimization.mixin.sound;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChannelAccess.class)
public abstract class ChannelAccessAsyncMixin {

    @Shadow
    @Final
    private Set<ChannelAccess.ChannelHandle> channels;

    @Shadow
    @Final
    private Library library;

    @Shadow
    @Final
    private Executor executor;

    @Inject(method = "createHandle", at = @At("HEAD"), cancellable = true)
    private void papi$createHandleNonBlocking(
        Library.Pool pool,
        CallbackInfoReturnable<CompletableFuture<ChannelAccess.ChannelHandle>> cir
    ) {
        ChannelAccess self = (ChannelAccess) (Object) this;
        ChannelAccess.ChannelHandle handle = self.new ChannelHandle(null);
        this.executor.execute(() -> {
            Channel channel = this.library.acquireChannel(pool);
            if (channel != null) {
                ((ChannelHandleAccessor) handle).papi$setChannel(channel);
                this.channels.add(handle);
            } else {
                ((ChannelHandleAccessor) handle).papi$setStopped(true);
            }
        });
        cir.setReturnValue(CompletableFuture.completedFuture(handle));
    }
}
