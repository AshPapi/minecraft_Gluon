package net.ashpapi.papioptimization.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.ashpapi.papioptimization.sculk.IChunkListenerAccess;
import net.ashpapi.papioptimization.sculk.PapiServerLevelListeners;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LevelChunk.class, priority = 1500)
public abstract class LevelChunkListenerMixin implements IChunkListenerAccess {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    @Mutable
    private @Nullable Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;

    @WrapOperation(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
            at = @At(value = "NEW", target = "()Lit/unimi/dsi/fastutil/ints/Int2ObjectOpenHashMap;")
    )
    private Int2ObjectOpenHashMap<GameEventListenerRegistry> papi$deferListenerMap(
            Operation<Int2ObjectOpenHashMap<GameEventListenerRegistry>> original
    ) {
        return null;
    }

    @WrapMethod(method = "getListenerRegistry")
    private GameEventListenerRegistry papi$fastGetListenerRegistry(int sectionY, Operation<GameEventListenerRegistry> original) {
        if (this.level instanceof ServerLevel serverLevel) {
            Int2ObjectMap<GameEventListenerRegistry> map = this.gameEventListenerRegistrySections;
            if (map != null) {
                GameEventListenerRegistry existing = map.get(sectionY);
                if (existing != null) {
                    return existing;
                }
            } else {
                this.gameEventListenerRegistrySections = new Int2ObjectOpenHashMap<>(4);
            }
            GameEventListenerRegistry created = original.call(sectionY);
            if (serverLevel instanceof PapiServerLevelListeners tracker) {
                LevelChunk self = (LevelChunk) (Object) this;
                tracker.papi$registerListenerChunk(self.getPos().pack(), self);
            }
            return created;
        }
        return original.call(sectionY);
    }

    @WrapMethod(method = "removeGameEventListenerRegistry")
    private void papi$fastRemoveListenerRegistry(int sectionY, Operation<Void> original) {
        Int2ObjectMap<GameEventListenerRegistry> map = this.gameEventListenerRegistrySections;
        if (map == null) {
            return;
        }
        map.remove(sectionY);
        if (map.isEmpty()) {
            this.gameEventListenerRegistrySections = null;
            if (this.level instanceof ServerLevel serverLevel && serverLevel instanceof PapiServerLevelListeners tracker) {
                LevelChunk self = (LevelChunk) (Object) this;
                tracker.papi$unregisterListenerChunk(self.getPos().pack(), self);
            }
        }
    }

    @Override
    public @Nullable GameEventListenerRegistry papi$getListenerRegistryIfPresent(int sectionY) {
        Int2ObjectMap<GameEventListenerRegistry> map = this.gameEventListenerRegistrySections;
        return map != null ? map.get(sectionY) : null;
    }

    @Override
    public boolean papi$hasAnyListenerRegistries() {
        Int2ObjectMap<GameEventListenerRegistry> map = this.gameEventListenerRegistrySections;
        return map != null && !map.isEmpty();
    }
}
