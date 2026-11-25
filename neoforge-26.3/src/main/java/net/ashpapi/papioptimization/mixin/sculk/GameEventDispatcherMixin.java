package net.ashpapi.papioptimization.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.ashpapi.papioptimization.sculk.IChunkListenerAccess;
import net.ashpapi.papioptimization.sculk.PapiServerLevelListeners;
import net.ashpapi.papioptimization.sculk.SculkDispatchContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugGameEventInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = GameEventDispatcher.class, priority = 1500)
public abstract class GameEventDispatcherMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract void handleGameEventMessagesInQueue(List<GameEvent.ListenerInfo> listenerInfos);

    @WrapMethod(method = "post")
    private void papi$fastPost(
            Holder<GameEvent> gameEvent,
            Vec3 position,
            GameEvent.Context context,
            Operation<Void> original
    ) {
        PapiServerLevelListeners levelListeners = this.level instanceof PapiServerLevelListeners l ? l : null;
        if (levelListeners != null && levelListeners.papi$hasNoListenerChunks()) {
            return;
        }

        int radius = gameEvent.value().notificationRadius();
        int centerX = Mth.floor(position.x);
        int centerY = Mth.floor(position.y);
        int centerZ = Mth.floor(position.z);

        int sectionMinX = SectionPos.blockToSectionCoord(centerX - radius);
        int sectionMinY = SectionPos.blockToSectionCoord(centerY - radius);
        int sectionMinZ = SectionPos.blockToSectionCoord(centerZ - radius);
        int sectionMaxX = SectionPos.blockToSectionCoord(centerX + radius);
        int sectionMaxY = SectionPos.blockToSectionCoord(centerY + radius);
        int sectionMaxZ = SectionPos.blockToSectionCoord(centerZ + radius);

        ServerChunkCache chunkSource = this.level.getChunkSource();
        SculkDispatchContext dispatchCtx = SculkDispatchContext.acquire(this.level, gameEvent, position, context);
        boolean applicable = false;

        try {
            for (int chunkX = sectionMinX; chunkX <= sectionMaxX; ++chunkX) {
                for (int chunkZ = sectionMinZ; chunkZ <= sectionMaxZ; ++chunkZ) {
                    LevelChunk chunk;
                    if (levelListeners != null) {
                        LevelChunk candidate = levelListeners.papi$getListenerChunk(ChunkPos.pack(chunkX, chunkZ));
                        if (candidate == null) {
                            continue;
                        }
                        chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                        if (chunk != candidate) {
                            continue;
                        }
                    } else {
                        chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                        if (chunk == null) {
                            continue;
                        }
                    }

                    IChunkListenerAccess tracker = (IChunkListenerAccess) chunk;
                    if (!tracker.papi$hasAnyListenerRegistries()) {
                        continue;
                    }

                    for (int section = sectionMinY; section <= sectionMaxY; ++section) {
                        GameEventListenerRegistry registry = tracker.papi$getListenerRegistryIfPresent(section);
                        if (registry == null || registry.isEmpty()) {
                            continue;
                        }
                        applicable |= registry.visitInRangeListeners(gameEvent, position, context, dispatchCtx);
                    }
                }
            }

            List<GameEvent.ListenerInfo> queue = dispatchCtx.getDistanceQueue();
            if (queue != null) {
                this.handleGameEventMessagesInQueue(queue);
            }
        } finally {
            dispatchCtx.release();
        }

        if (applicable) {
            this.level.debugSynchronizers().broadcastEventToTracking(
                    BlockPos.containing(position.x, position.y, position.z),
                    DebugSubscriptions.GAME_EVENTS,
                    new DebugGameEventInfo(gameEvent, position)
            );
        }
    }
}
