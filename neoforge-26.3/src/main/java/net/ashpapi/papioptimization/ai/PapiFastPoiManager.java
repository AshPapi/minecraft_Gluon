package net.ashpapi.papioptimization.ai;

import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.border.WorldBorder;

public interface PapiFastPoiManager {

    Set<Pair<Holder<PoiType>, BlockPos>> papi$collectTop5AcquirePoi(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> batchPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            ServerLevel level,
            BiPredicate<ServerLevel, BlockPos> validatePoi
    );

    Optional<BlockPos> papi$takeExact(Predicate<Holder<PoiType>> typePredicate, BlockPos targetPos);

    Optional<BlockPos> papi$findClosestPortal(
            ServerLevel level,
            Predicate<Holder<PoiType>> portalTypePredicate,
            BlockPos exitPos,
            int radius,
            WorldBorder worldBorder
    );
}
