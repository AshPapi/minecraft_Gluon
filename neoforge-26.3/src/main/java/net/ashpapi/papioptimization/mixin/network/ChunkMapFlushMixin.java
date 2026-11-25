package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.ashpapi.papioptimization.network.PapiAutoFlush;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ChunkMap.class, priority = 1500)
public abstract class ChunkMapFlushMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @WrapMethod(method = "tick()V")
    private void papi$consolidateEntityTrackerFlushes(Operation<Void> original) {
        List<ServerPlayer> players = this.level.players();
        int size = players.size();
        if (size == 0) {
            original.call();
            return;
        }
        PapiAutoFlush[] suspended = new PapiAutoFlush[size];
        int count = 0;
        for (int i = 0; i < size; i++) {
            ServerPlayer player = players.get(i);
            if (player.connection != null) {
                Connection conn = player.connection.getConnection();
                if (conn instanceof PapiAutoFlush autoFlush) {
                    autoFlush.papi$suspendAutoFlush();
                    suspended[count++] = autoFlush;
                }
            }
        }
        try {
            original.call();
        } finally {
            for (int i = 0; i < count; i++) {
                suspended[i].papi$resumeAutoFlush();
            }
        }
    }
}
