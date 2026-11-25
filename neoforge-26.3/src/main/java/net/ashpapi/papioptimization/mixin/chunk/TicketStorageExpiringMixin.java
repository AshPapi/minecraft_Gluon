package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TicketStorage.class, priority = 1500)
public abstract class TicketStorageExpiringMixin extends SavedData {

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<List<Ticket>> tickets;

    @Shadow
    private TicketStorage.@Nullable ChunkUpdated loadingChunkUpdatedListener;

    @Shadow
    private TicketStorage.@Nullable ChunkUpdated simulationChunkUpdatedListener;

    @Shadow
    private boolean canTicketExpire(ChunkMap chunkMap, Ticket ticket, long chunkPos) {
        throw new AssertionError();
    }

    @Shadow
    private static int getTicketLevelAt(List<Ticket> tickets, boolean simulate) {
        throw new AssertionError();
    }

    @Shadow
    private void updateForcedChunks() {
        throw new AssertionError();
    }

    @Shadow
    private void updateForcedNaturalSpawning() {
        throw new AssertionError();
    }

    @Unique
    private final LongOpenHashSet papi$expiringChunks = new LongOpenHashSet();

    @Inject(
        method = "<init>(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
        at = @At("RETURN")
    )
    private void papi$indexInitialExpiringTickets(
            Long2ObjectOpenHashMap<List<Ticket>> active,
            Long2ObjectOpenHashMap<List<Ticket>> deactivated,
            CallbackInfo ci
    ) {
        for (Long2ObjectMap.Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(this.tickets)) {
            List<Ticket> list = entry.getValue();
            for (int i = 0, n = list.size(); i < n; i++) {
                if (list.get(i).getType().hasTimeout()) {
                    this.papi$expiringChunks.add(entry.getLongKey());
                    break;
                }
            }
        }
    }

    @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z", at = @At("HEAD"))
    private void papi$trackExpiringTicket(long chunkPos, Ticket ticket, CallbackInfoReturnable<Boolean> cir) {
        if (ticket.getType().hasTimeout()) {
            this.papi$expiringChunks.add(chunkPos);
        }
    }

    @Inject(method = "replaceTicketLevelOfType", at = @At("HEAD"))
    private void papi$trackReplacedTicketType(int level, TicketType type, CallbackInfo ci) {
        if (type.hasTimeout()) {
            for (Long2ObjectMap.Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(this.tickets)) {
                List<Ticket> list = entry.getValue();
                for (int i = 0, n = list.size(); i < n; i++) {
                    if (list.get(i).getType().equals(type)) {
                        this.papi$expiringChunks.add(entry.getLongKey());
                        break;
                    }
                }
            }
        }
    }

    @WrapMethod(method = "purgeStaleTickets")
    private void papi$purgeOnlyExpiringTickets(ChunkMap chunkMap, Operation<Void> original) {
        if (this.papi$expiringChunks.isEmpty()) {
            return;
        }
        boolean forcedChanged = false;
        boolean forceSpawnChanged = false;
        boolean anyTouched = false;
        LongIterator chunkIter = this.papi$expiringChunks.iterator();
        while (chunkIter.hasNext()) {
            long chunkPos = chunkIter.nextLong();
            List<Ticket> list = this.tickets.get(chunkPos);
            if (list == null || list.isEmpty()) {
                chunkIter.remove();
                continue;
            }
            boolean loadRemoved = false;
            boolean simRemoved = false;
            boolean stillHasExpiring = false;
            Iterator<Ticket> ticketIter = list.iterator();
            while (ticketIter.hasNext()) {
                Ticket ticket = ticketIter.next();
                TicketType type = ticket.getType();
                if (!type.hasTimeout()) {
                    continue;
                }
                if (this.canTicketExpire(chunkMap, ticket, chunkPos)) {
                    ticket.decreaseTicksLeft();
                    anyTouched = true;
                    if (ticket.isTimedOut()) {
                        ticketIter.remove();
                        if (type.doesLoad()) {
                            loadRemoved = true;
                        }
                        if (type.doesSimulate()) {
                            simRemoved = true;
                        }
                        if (TicketType.FORCED.equals(type)) {
                            forcedChanged = true;
                        }
                        if (type.forceNaturalSpawning()) {
                            forceSpawnChanged = true;
                        }
                        continue;
                    }
                }
                stillHasExpiring = true;
            }
            if (!stillHasExpiring) {
                chunkIter.remove();
            }
            if (loadRemoved || simRemoved) {
                if (loadRemoved && this.loadingChunkUpdatedListener != null) {
                    this.loadingChunkUpdatedListener.update(chunkPos, getTicketLevelAt(list, false), false);
                }
                if (simRemoved && this.simulationChunkUpdatedListener != null) {
                    this.simulationChunkUpdatedListener.update(chunkPos, getTicketLevelAt(list, true), false);
                }
                anyTouched = true;
                if (list.isEmpty()) {
                    this.tickets.remove(chunkPos);
                }
            }
        }
        if (forcedChanged) {
            this.updateForcedChunks();
        }
        if (forceSpawnChanged) {
            this.updateForcedNaturalSpawning();
        }
        if (anyTouched) {
            this.setDirty();
        }
    }
}
