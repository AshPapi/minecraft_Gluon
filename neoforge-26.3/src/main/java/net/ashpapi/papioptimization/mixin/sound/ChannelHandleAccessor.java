package net.ashpapi.papioptimization.mixin.sound;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChannelAccess.ChannelHandle.class)
public interface ChannelHandleAccessor {

    @Accessor("channel")
    void papi$setChannel(Channel channel);

    @Accessor("stopped")
    void papi$setStopped(boolean stopped);
}
