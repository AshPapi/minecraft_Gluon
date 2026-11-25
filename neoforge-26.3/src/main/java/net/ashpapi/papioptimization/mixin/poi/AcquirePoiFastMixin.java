package net.ashpapi.papioptimization.mixin.poi;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.ashpapi.papioptimization.ai.PapiFastPoiManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableLong;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AcquirePoi.class, priority = 1500)
public abstract class AcquirePoiFastMixin {

    @WrapMethod(method = "findPathToPois")
    private static @Nullable Path papi$fastFindPathToPois(
            Mob mob,
            Set<Pair<Holder<PoiType>, BlockPos>> pois,
            Operation<@Nullable Path> original
    ) {
        if (pois.isEmpty()) {
            return null;
        }
        int size = pois.size();
        Set<BlockPos> targets = size <= 8 ? new ObjectArraySet<>(size) : new ObjectOpenHashSet<>(size);
        int maxValidRange = 1;
        for (Pair<Holder<PoiType>, BlockPos> pair : pois) {
            maxValidRange = Math.max(maxValidRange, pair.getFirst().value().validRange());
            targets.add(pair.getSecond());
        }
        return mob.getNavigation().createPath(targets, maxValidRange);
    }

    @WrapOperation(
        method = "lambda$create$3",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findAllClosestFirstWithType(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"
        )
    )
    private static Stream<Pair<Holder<PoiType>, BlockPos>> papi$computeTop5Directly(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> batchPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original,
            boolean onlyIfAdult,
            MutableLong nextScheduledStart,
            Long2ObjectMap<?> batchCache,
            Predicate<Holder<PoiType>> acquirablePois,
            BiPredicate<ServerLevel, BlockPos> validatePoi,
            MemoryAccessor<?, GlobalPos> memoryAccessor,
            Optional<Byte> eventId,
            ServerLevel level,
            PathfinderMob mob,
            long timestamp,
            @Share("top5Set") LocalRef<Set<Pair<Holder<PoiType>, BlockPos>>> topSetRef
    ) {
        if (poiManager instanceof PapiFastPoiManager fast) {
            topSetRef.set(fast.papi$collectTop5AcquirePoi(
                    typePredicate, batchPredicate, origin, radius, occupancy, level, validatePoi
            ));
            return Stream.empty();
        }
        return original.call(poiManager, typePredicate, batchPredicate, origin, radius, occupancy);
    }

    @WrapOperation(
        method = "lambda$create$3",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"
        )
    )
    private static Object papi$returnComputedTop5Set(
            Stream<?> stream,
            Collector<?, ?, ?> collector,
            Operation<Object> original,
            @Share("top5Set") LocalRef<Set<Pair<Holder<PoiType>, BlockPos>>> topSetRef
    ) {
        Set<Pair<Holder<PoiType>, BlockPos>> computed = topSetRef.get();
        if (computed != null) {
            return computed;
        }
        return original.call(stream, collector);
    }

    @WrapOperation(
        method = "lambda$create$7",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;take(Ljava/util/function/Predicate;Ljava/util/function/BiPredicate;Lnet/minecraft/core/BlockPos;I)Ljava/util/Optional;"
        )
    )
    private static Optional<BlockPos> papi$fastTakeAtExactPos(
            PoiManager poiManager,
            Predicate<Holder<PoiType>> typePredicate,
            BiPredicate<Holder<PoiType>, BlockPos> biPredicate,
            BlockPos targetPos,
            int radius,
            Operation<Optional<BlockPos>> original
    ) {
        if (poiManager instanceof PapiFastPoiManager fast) {
            return fast.papi$takeExact(typePredicate, targetPos);
        }
        return original.call(poiManager, typePredicate, biPredicate, targetPos, radius);
    }
}
