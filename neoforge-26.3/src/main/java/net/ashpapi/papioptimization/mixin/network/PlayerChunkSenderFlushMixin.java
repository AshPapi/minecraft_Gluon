package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.network.PapiAutoFlush;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PlayerChunkSender.class, priority = 1500)
public abstract class PlayerChunkSenderFlushMixin {

    @WrapMethod(method = "sendNextChunks")
    private void papi$consolidateChunkBatchFlushes(ServerPlayer player, Operation<Void> original) {
        if (player.connection == null || !(player.connection.getConnection() instanceof PapiAutoFlush autoFlush)) {
            original.call(player);
            return;
        }
        autoFlush.papi$suspendAutoFlush();
        try {
            original.call(player);
        } finally {
            autoFlush.papi$resumeAutoFlush();
        }
    }
}
