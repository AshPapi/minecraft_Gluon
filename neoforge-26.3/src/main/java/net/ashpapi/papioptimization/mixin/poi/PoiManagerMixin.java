package net.ashpapi.papioptimization.mixin.poi;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.ashpapi.papioptimization.access.PapiPoiSectionFast;
import net.ashpapi.papioptimization.ai.PapiFastPoiManager;
import net.ashpapi.papioptimization.ai.PapiPoiColumnIndex;
import net.ashpapi.papioptimization.ai.PapiSinglePoiTypeFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PoiManager.class, priority = 1500)
public abstract class PoiManagerMixin implements PapiFastPoiManager {

    @Shadow
    @Final
    private LongSet loadedChunks;

    @Inject(
        method = "findClosest(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastFindClosest(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        PoiRecord best = papi$searchClosestRecord(typePredicate, null, origin, radius, occupancy);
        cir.setReturnValue(best != null ? Optional.of(best.getPos()) : Optional.empty());
    }

    @Inject(
        method = "findClosest(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastFindClosestFiltered(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> posPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        PoiRecord best = papi$searchClosestRecord(typePredicate, posPredicate, origin, radius, occupancy);
        cir.setReturnValue(best != null ? Optional.of(best.getPos()) : Optional.empty());
    }

    @Inject(
        method = "findClosestWithType(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastFindClosestWithType(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Optional<Pair<Holder<PoiType>, BlockPos>>> cir
    ) {
        PoiRecord best = papi$searchClosestRecord(typePredicate, null, origin, radius, occupancy);
        cir.setReturnValue(best != null ? Optional.of(Pair.of(best.getPoiType(), best.getPos())) : Optional.empty());
    }

    @Inject(method = "find", at = @At("HEAD"), cancellable = true)
    private void papi$fastFind(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> posPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        if (radius < 0) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        PoiRecord found = papi$findFirstInRange(typePredicate, posPredicate, null, origin, radius, occupancy);
        cir.setReturnValue(found != null ? Optional.of(found.getPos()) : Optional.empty());
    }

    @Inject(method = "take", at = @At("HEAD"), cancellable = true)
    private void papi$fastTake(
            Predicate<Holder<PoiType>> typePredicate,
            BiPredicate<Holder<PoiType>, BlockPos> biPredicate,
            BlockPos origin,
            int radius,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        if (radius < 0) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        PoiRecord found = papi$findFirstInRange(typePredicate, null, biPredicate, origin, radius, PoiManager.Occupancy.HAS_SPACE);
        if (found != null) {
            ((PoiRecordAccessor) found).papi$acquireTicket();
            cir.setReturnValue(Optional.of(found.getPos()));
        } else {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getCountInRange", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetCountInRange(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Long> cir
    ) {
        if (radius < 0) {
            cir.setReturnValue(0L);
            return;
        }
        cir.setReturnValue(papi$countInRange(typePredicate, origin, radius, occupancy));
    }

    @Inject(method = "getRandom", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetRandom(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> posPredicate,
            PoiManager.Occupancy occupancy,
            BlockPos origin,
            int radius,
            RandomSource random,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        if (radius < 0) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        ObjectArrayList<PoiRecord> list = new ObjectArrayList<>();
        papi$collectInRange(typePredicate, origin, radius, occupancy, false, list);
        int size = list.size();
        if (size == 0) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        for (int i = size - 1; i >= 0; i--) {
            int j = random.nextInt(i + 1);
            PoiRecord picked = list.get(j);
            if (j != i) {
                list.set(j, list.get(i));
            }
            BlockPos pos = picked.getPos();
            if (posPredicate.test(pos)) {
                cir.setReturnValue(Optional.of(pos));
                return;
            }
        }
        cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getInSquare", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetInSquare(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Stream<PoiRecord>> cir
    ) {
        if (radius < 0) {
            cir.setReturnValue(Stream.empty());
            return;
        }
        ObjectArrayList<PoiRecord> list = new ObjectArrayList<>();
        papi$collectInRange(typePredicate, origin, radius, occupancy, true, list);
        cir.setReturnValue(list.isEmpty() ? Stream.empty() : list.stream());
    }

    @Inject(method = "getInRange", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetInRange(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Stream<PoiRecord>> cir
    ) {
        if (radius < 0) {
            cir.setReturnValue(Stream.empty());
            return;
        }
        ObjectArrayList<PoiRecord> list = new ObjectArrayList<>();
        papi$collectInRange(typePredicate, origin, radius, occupancy, false, list);
        cir.setReturnValue(list.isEmpty() ? Stream.empty() : list.stream());
    }

    @Inject(method = "findAllClosestFirstWithType", at = @At("HEAD"), cancellable = true)
    private void papi$fastFindAllClosestFirstWithType(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> posPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Stream<Pair<Holder<PoiType>, BlockPos>>> cir
    ) {
        if (radius < 0) {
            cir.setReturnValue(Stream.empty());
            return;
        }
        ObjectArrayList<PoiRecord> raw = new ObjectArrayList<>();
        papi$collectInRange(typePredicate, origin, radius, occupancy, false, raw);
        if (raw.isEmpty()) {
            cir.setReturnValue(Stream.empty());
            return;
        }
        ObjectArrayList<Pair<Holder<PoiType>, BlockPos>> pairs = new ObjectArrayList<>(raw.size());
        for (int i = 0, len = raw.size(); i < len; i++) {
            PoiRecord rec = raw.get(i);
            BlockPos pos = rec.getPos();
            if (posPredicate.test(pos)) {
                pairs.add(Pair.of(rec.getPoiType(), pos));
            }
        }
        if (pairs.isEmpty()) {
            cir.setReturnValue(Stream.empty());
            return;
        }
        if (pairs.size() > 1) {
            pairs.sort(Comparator.comparingDouble(p -> p.getSecond().distSqr(origin)));
        }
        cir.setReturnValue(pairs.stream());
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "ensureLoadedAndValid", at = @At("HEAD"), cancellable = true)
    private void papi$fastEnsureLoadedAndValid(LevelReader levelReader, BlockPos center, int radius, CallbackInfo ci) {
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        PapiPoiColumnIndex<PoiSection> colIdx = (PapiPoiColumnIndex<PoiSection>) (Object) this;
        LevelHeightAccessor heightAccessor = storage.papi$getLevelHeightAccessor();
        int minSecY = heightAccessor.getMinSectionY();
        int maxSecY = heightAccessor.getMaxSectionY();
        int chunkRadius = Math.floorDiv(radius, 16);
        int centerCx = SectionPos.blockToSectionCoord(center.getX());
        int centerCz = SectionPos.blockToSectionCoord(center.getZ());

        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            int cz = centerCz + dz;
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                int cx = centerCx + dx;
                long chunkKey = ChunkPos.pack(cx, cz);
                if (this.loadedChunks.contains(chunkKey) && colIdx.papi$getColumnNonEmptyMask(chunkKey) >= 0L) {
                    continue;
                }
                boolean anyInvalid = false;
                for (int secY = minSecY; secY <= maxSecY; secY++) {
                    Optional<PoiSection> opt = storage.papi$getOrLoad(SectionPos.asLong(cx, secY, cz));
                    if (opt == null || opt.isEmpty() || !opt.get().isValid()) {
                        anyInvalid = true;
                    }
                }
                if (anyInvalid && this.loadedChunks.add(chunkKey)) {
                    levelReader.getChunk(cx, cz, ChunkStatus.EMPTY);
                }
            }
        }
        ci.cancel();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<BlockPos> papi$takeExact(Predicate<Holder<PoiType>> typePredicate, BlockPos targetPos) {
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        Optional<PoiSection> opt = storage.papi$getOrLoad(SectionPos.asLong(targetPos));
        if (opt == null || opt.isEmpty()) {
            return Optional.empty();
        }
        PoiSection section = opt.get();
        PoiRecord rec = ((PapiPoiSectionFast) section).papi$getRecordAt(targetPos);
        if (rec != null && rec.hasSpace() && typePredicate.test(rec.getPoiType())) {
            ((PoiRecordAccessor) rec).papi$acquireTicket();
            return Optional.of(rec.getPos());
        }
        return Optional.empty();
    }

    @Override
    public Set<Pair<Holder<PoiType>, BlockPos>> papi$collectTop5AcquirePoi(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> batchPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            ServerLevel level,
            BiPredicate<ServerLevel, BlockPos> validatePoi
    ) {
        if (radius < 0) {
            return Set.of();
        }
        ObjectArrayList<PoiRecord> raw = new ObjectArrayList<>();
        papi$collectInRange(typePredicate, origin, radius, occupancy, false, raw);
        int rawSize = raw.size();
        if (rawSize == 0) {
            return Set.of();
        }
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        PoiRecord[] topRecords = new PoiRecord[5];
        long[] topDists = new long[5];
        int topCount = 0;

        for (int i = 0; i < rawSize; i++) {
            PoiRecord rec = raw.get(i);
            BlockPos pos = rec.getPos();
            if (!batchPredicate.test(pos)) {
                continue;
            }
            long dx = (long) pos.getX() - ox;
            long dy = (long) pos.getY() - oy;
            long dz = (long) pos.getZ() - oz;
            long distSq = dx * dx + dy * dy + dz * dz;

            if (topCount < 5) {
                int ins = topCount;
                while (ins > 0 && distSq < topDists[ins - 1]) {
                    topDists[ins] = topDists[ins - 1];
                    topRecords[ins] = topRecords[ins - 1];
                    ins--;
                }
                topDists[ins] = distSq;
                topRecords[ins] = rec;
                topCount++;
            } else if (distSq < topDists[4]) {
                int ins = 4;
                while (ins > 0 && distSq < topDists[ins - 1]) {
                    topDists[ins] = topDists[ins - 1];
                    topRecords[ins] = topRecords[ins - 1];
                    ins--;
                }
                topDists[ins] = distSq;
                topRecords[ins] = rec;
            }
        }

        if (topCount == 0) {
            return Set.of();
        }
        ObjectArraySet<Pair<Holder<PoiType>, BlockPos>> result = new ObjectArraySet<>(topCount);
        for (int i = 0; i < topCount; i++) {
            PoiRecord rec = topRecords[i];
            BlockPos pos = rec.getPos();
            if (validatePoi.test(level, pos)) {
                result.add(Pair.of(rec.getPoiType(), pos));
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<BlockPos> papi$findClosestPortal(
            ServerLevel level,
            Predicate<Holder<PoiType>> portalTypePredicate,
            BlockPos exitPos,
            int radius,
            WorldBorder worldBorder
    ) {
        if (radius < 0) {
            return Optional.empty();
        }
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        PapiPoiColumnIndex<PoiSection> colIdx = (PapiPoiColumnIndex<PoiSection>) (Object) this;
        int ox = exitPos.getX();
        int oy = exitPos.getY();
        int oz = exitPos.getZ();
        int originCx = SectionPos.blockToSectionCoord(ox);
        int originCz = SectionPos.blockToSectionCoord(oz);
        int chunkRadius = Math.floorDiv(radius, 16) + 1;

        int borderMinCx = SectionPos.posToSectionCoord(worldBorder.getMinX());
        int borderMaxCx = SectionPos.posToSectionCoord(worldBorder.getMaxX());
        int borderMinCz = SectionPos.posToSectionCoord(worldBorder.getMinZ());
        int borderMaxCz = SectionPos.posToSectionCoord(worldBorder.getMaxZ());

        LevelHeightAccessor heightAccessor = storage.papi$getLevelHeightAccessor();
        int minSecY = heightAccessor.getMinSectionY();
        int maxSecY = heightAccessor.getMaxSectionY();
        int centerSecY = Mth.clamp(SectionPos.blockToSectionCoord(oy), minSecY, maxSecY);
        int maxSecOffset = Math.max(centerSecY - minSecY, maxSecY - centerSecY);

        long bestDistSq = Long.MAX_VALUE;
        int bestY = Integer.MAX_VALUE;
        int bestCz = Integer.MAX_VALUE;
        int bestCx = Integer.MAX_VALUE;
        int bestSecY = Integer.MAX_VALUE;
        int bestOrd = Integer.MAX_VALUE;
        BlockPos bestPos = null;

        for (int r = 0; r <= chunkRadius; r++) {
            if (r >= 1 && bestPos != null) {
                int minHoriz = papi$minRingHorizDist(ox, oz, r);
                long minRingSq = (long) minHoriz * (long) minHoriz;
                if (minRingSq > bestDistSq) {
                    break;
                }
            }
            if (r == 0) {
                if (originCx >= borderMinCx && originCx <= borderMaxCx && originCz >= borderMinCz && originCz <= borderMaxCz) {
                    bestPos = papi$scanPortalChunk(
                            level, storage, colIdx, portalTypePredicate, worldBorder,
                            ox, oy, oz, radius, originCx, originCz,
                            minSecY, maxSecY, centerSecY, maxSecOffset,
                            bestPos, bestDistSq, bestY, bestCz, bestCx, bestSecY, bestOrd
                    );
                    if (bestPos != null) {
                        long dx = (long) bestPos.getX() - ox;
                        long dy = (long) bestPos.getY() - oy;
                        long dz = (long) bestPos.getZ() - oz;
                        bestDistSq = dx * dx + dy * dy + dz * dz;
                        bestY = bestPos.getY();
                        bestCz = SectionPos.blockToSectionCoord(bestPos.getZ());
                        bestCx = SectionPos.blockToSectionCoord(bestPos.getX());
                        bestSecY = SectionPos.blockToSectionCoord(bestPos.getY());
                        bestOrd = this.papi$portalBestOrd;
                    }
                }
                continue;
            }

            for (int step = 0; step < 4; step++) {
                int dzStart;
                int dzEnd;
                int dxFixed = 0;
                boolean rowScan;
                if (step == 0) {
                    dzStart = -r;
                    dzEnd = -r;
                    rowScan = true;
                } else if (step == 1) {
                    dzStart = -r + 1;
                    dzEnd = r - 1;
                    dxFixed = -r;
                    rowScan = false;
                } else if (step == 2) {
                    dzStart = -r + 1;
                    dzEnd = r - 1;
                    dxFixed = r;
                    rowScan = false;
                } else {
                    dzStart = r;
                    dzEnd = r;
                    rowScan = true;
                }
                for (int dz = dzStart; dz <= dzEnd; dz++) {
                    int cz = originCz + dz;
                    if (cz < borderMinCz || cz > borderMaxCz) {
                        continue;
                    }
                    int dxMin = rowScan ? -r : dxFixed;
                    int dxMax = rowScan ? r : dxFixed;
                    for (int dx = dxMin; dx <= dxMax; dx++) {
                        int cx = originCx + dx;
                        if (cx < borderMinCx || cx > borderMaxCx) {
                            continue;
                        }
                        BlockPos prevBest = bestPos;
                        bestPos = papi$scanPortalChunk(
                                level, storage, colIdx, portalTypePredicate, worldBorder,
                                ox, oy, oz, radius, cx, cz,
                                minSecY, maxSecY, centerSecY, maxSecOffset,
                                bestPos, bestDistSq, bestY, bestCz, bestCx, bestSecY, bestOrd
                        );
                        if (bestPos != null && bestPos != prevBest) {
                            long pdx = (long) bestPos.getX() - ox;
                            long pdy = (long) bestPos.getY() - oy;
                            long pdz = (long) bestPos.getZ() - oz;
                            bestDistSq = pdx * pdx + pdy * pdy + pdz * pdz;
                            bestY = bestPos.getY();
                            bestCz = SectionPos.blockToSectionCoord(bestPos.getZ());
                            bestCx = SectionPos.blockToSectionCoord(bestPos.getX());
                            bestSecY = SectionPos.blockToSectionCoord(bestPos.getY());
                            bestOrd = this.papi$portalBestOrd;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(bestPos);
    }

    @Unique
    private int papi$portalBestOrd;

    @Unique
    private @Nullable BlockPos papi$scanPortalChunk(
            ServerLevel level,
            SectionStorageAccessor<PoiSection> storage,
            PapiPoiColumnIndex<PoiSection> colIdx,
            Predicate<Holder<PoiType>> portalTypePredicate,
            WorldBorder worldBorder,
            int ox, int oy, int oz, int radius,
            int cx, int cz,
            int minSecY, int maxSecY, int centerSecY, int maxSecOffset,
            @Nullable BlockPos bestPos,
            long bestDistSq, int bestY, int bestCz, int bestCx, int bestSecY, int bestOrd
    ) {
        int chunkMinX = cx << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = cz << 4;
        int chunkMaxZ = chunkMinZ + 15;
        if (chunkMaxX < ox - radius || chunkMinX > ox + radius || chunkMaxZ < oz - radius || chunkMinZ > oz + radius) {
            return bestPos;
        }
        long cdx = ox < chunkMinX ? (chunkMinX - ox) : (ox > chunkMaxX ? (ox - chunkMaxX) : 0);
        long cdz = oz < chunkMinZ ? (chunkMinZ - oz) : (oz > chunkMaxZ ? (oz - chunkMaxZ) : 0);
        long minChunkDistSq = cdx * cdx + cdz * cdz;
        if (minChunkDistSq > bestDistSq) {
            return bestPos;
        }
        long chunkKey = ChunkPos.pack(cx, cz);
        long colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
        if (colMask == 0L) {
            return bestPos;
        }
        ChunkAccess cachedChunk = null;

        for (int dyOff = 0; dyOff <= maxSecOffset; dyOff++) {
            int passes = dyOff == 0 ? 1 : 2;
            for (int side = 0; side < passes; side++) {
                int secY = side == 0 ? (centerSecY + dyOff) : (centerSecY - dyOff);
                if (secY < minSecY || secY > maxSecY) {
                    continue;
                }
                if (colMask >= 0L && (colMask & (1L << (secY - minSecY))) == 0L) {
                    continue;
                }
                int secMinY = secY << 4;
                int secMaxY = secMinY + 15;
                long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                if (minChunkDistSq + sdy * sdy > bestDistSq) {
                    continue;
                }
                long sectionKey = SectionPos.asLong(cx, secY, cz);
                Optional<PoiSection> opt = colMask >= 0L
                        ? colIdx.papi$getLoadedSectionDirect(sectionKey)
                        : storage.papi$getOrLoad(sectionKey);
                if (colMask < 0L) {
                    colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                    if (colMask == 0L) {
                        return bestPos;
                    }
                }
                if (opt == null || opt.isEmpty()) {
                    continue;
                }
                PoiSection section = opt.get();
                Map<Holder<PoiType>, Set<PoiRecord>> byType = ((PoiSectionAccessor) section).papi$getByType();
                if (byType == null || byType.isEmpty()) {
                    continue;
                }
                int ord = 0;
                if (portalTypePredicate instanceof PapiSinglePoiTypeFilter single) {
                    Set<PoiRecord> set = byType.get(single.targetType());
                    if (set != null && !set.isEmpty()) {
                        for (PoiRecord rec : set) {
                            BlockPos p = rec.getPos();
                            int px = p.getX();
                            int pz = p.getZ();
                            if (Math.abs(px - ox) <= radius && Math.abs(pz - oz) <= radius) {
                                int py = p.getY();
                                long rdx = (long) px - ox;
                                long rdy = (long) py - oy;
                                long rdz = (long) pz - oz;
                                long distSq = rdx * rdx + rdy * rdy + rdz * rdz;
                                if (papi$beatsPortal(distSq, py, cz, cx, secY, ord, bestDistSq, bestY, bestCz, bestCx, bestSecY, bestOrd)) {
                                    if (worldBorder.isWithinBounds(p)) {
                                        if (cachedChunk == null) {
                                            cachedChunk = level.getChunk(cx, cz, ChunkStatus.EMPTY);
                                        }
                                        if (cachedChunk.getBlockState(p).hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                                            bestDistSq = distSq;
                                            bestY = py;
                                            bestCz = cz;
                                            bestCx = cx;
                                            bestSecY = secY;
                                            bestOrd = ord;
                                            bestPos = p;
                                            this.papi$portalBestOrd = ord;
                                        }
                                    }
                                }
                            }
                            ord++;
                        }
                    }
                } else {
                    for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : byType.entrySet()) {
                        if (portalTypePredicate.test(entry.getKey())) {
                            for (PoiRecord rec : entry.getValue()) {
                                BlockPos p = rec.getPos();
                                int px = p.getX();
                                int pz = p.getZ();
                                if (Math.abs(px - ox) <= radius && Math.abs(pz - oz) <= radius) {
                                    int py = p.getY();
                                    long rdx = (long) px - ox;
                                    long rdy = (long) py - oy;
                                    long rdz = (long) pz - oz;
                                    long distSq = rdx * rdx + rdy * rdy + rdz * rdz;
                                    if (papi$beatsPortal(distSq, py, cz, cx, secY, ord, bestDistSq, bestY, bestCz, bestCx, bestSecY, bestOrd)) {
                                        if (worldBorder.isWithinBounds(p)) {
                                            if (cachedChunk == null) {
                                                cachedChunk = level.getChunk(cx, cz, ChunkStatus.EMPTY);
                                            }
                                            if (cachedChunk.getBlockState(p).hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                                                bestDistSq = distSq;
                                                bestY = py;
                                                bestCz = cz;
                                                bestCx = cx;
                                                bestSecY = secY;
                                                bestOrd = ord;
                                                bestPos = p;
                                                this.papi$portalBestOrd = ord;
                                            }
                                        }
                                    }
                                }
                                ord++;
                            }
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private @Nullable PoiRecord papi$searchClosestRecord(
            Predicate<Holder<PoiType>> typePredicate,
            @Nullable Predicate<BlockPos> posPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy
    ) {
        if (radius < 0) {
            return null;
        }
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        PapiPoiColumnIndex<PoiSection> colIdx = (PapiPoiColumnIndex<PoiSection>) (Object) this;
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        int originChunkX = SectionPos.blockToSectionCoord(ox);
        int originChunkZ = SectionPos.blockToSectionCoord(oz);
        int chunkRadius = Math.floorDiv(radius, 16) + 1;
        long maxRadiusSq = (long) radius * (long) radius;

        LevelHeightAccessor heightAccessor = storage.papi$getLevelHeightAccessor();
        int minSecY = heightAccessor.getMinSectionY();
        int maxSecY = heightAccessor.getMaxSectionY();
        int centerSecY = Mth.clamp(SectionPos.blockToSectionCoord(oy), minSecY, maxSecY);
        int maxSecOffset = Math.max(centerSecY - minSecY, maxSecY - centerSecY);

        Predicate<? super PoiRecord> occupancyTest = occupancy.getTest();

        long bestDistSq = Long.MAX_VALUE;
        int bestCz = Integer.MAX_VALUE;
        int bestCx = Integer.MAX_VALUE;
        int bestSecY = Integer.MAX_VALUE;
        int bestOrd = Integer.MAX_VALUE;
        PoiRecord bestRecord = null;

        for (int r = 0; r <= chunkRadius; r++) {
            if (r >= 1) {
                int minHoriz = papi$minRingHorizDist(ox, oz, r);
                long minRingSq = (long) minHoriz * (long) minHoriz;
                if (minRingSq > maxRadiusSq || minRingSq > bestDistSq) {
                    break;
                }
            }
            if (r == 0) {
                bestRecord = papi$scanClosestChunk(
                        storage, colIdx, typePredicate, posPredicate, occupancy, occupancyTest,
                        ox, oy, oz, maxRadiusSq, originChunkX, originChunkZ,
                        minSecY, maxSecY, centerSecY, maxSecOffset,
                        bestRecord, bestDistSq, bestCz, bestCx, bestSecY, bestOrd
                );
                if (bestRecord != null) {
                    BlockPos p = bestRecord.getPos();
                    long dx = (long) p.getX() - ox;
                    long dy = (long) p.getY() - oy;
                    long dz = (long) p.getZ() - oz;
                    bestDistSq = dx * dx + dy * dy + dz * dz;
                    bestCz = SectionPos.blockToSectionCoord(p.getZ());
                    bestCx = SectionPos.blockToSectionCoord(p.getX());
                    bestSecY = SectionPos.blockToSectionCoord(p.getY());
                    bestOrd = this.papi$lastBestOrd;
                }
                continue;
            }

            for (int dz = -r; dz <= r; dz++) {
                int cz = originChunkZ + dz;
                if (dz == -r || dz == r) {
                    for (int dx = -r; dx <= r; dx++) {
                        int cx = originChunkX + dx;
                        PoiRecord prev = bestRecord;
                        bestRecord = papi$scanClosestChunk(
                                storage, colIdx, typePredicate, posPredicate, occupancy, occupancyTest,
                                ox, oy, oz, maxRadiusSq, cx, cz,
                                minSecY, maxSecY, centerSecY, maxSecOffset,
                                bestRecord, bestDistSq, bestCz, bestCx, bestSecY, bestOrd
                        );
                        if (bestRecord != null && bestRecord != prev) {
                            BlockPos p = bestRecord.getPos();
                            long pdx = (long) p.getX() - ox;
                            long pdy = (long) p.getY() - oy;
                            long pdz = (long) p.getZ() - oz;
                            bestDistSq = pdx * pdx + pdy * pdy + pdz * pdz;
                            bestCz = SectionPos.blockToSectionCoord(p.getZ());
                            bestCx = SectionPos.blockToSectionCoord(p.getX());
                            bestSecY = SectionPos.blockToSectionCoord(p.getY());
                            bestOrd = this.papi$lastBestOrd;
                        }
                    }
                } else {
                    for (int side = 0; side < 2; side++) {
                        int cx = originChunkX + (side == 0 ? -r : r);
                        PoiRecord prev = bestRecord;
                        bestRecord = papi$scanClosestChunk(
                                storage, colIdx, typePredicate, posPredicate, occupancy, occupancyTest,
                                ox, oy, oz, maxRadiusSq, cx, cz,
                                minSecY, maxSecY, centerSecY, maxSecOffset,
                                bestRecord, bestDistSq, bestCz, bestCx, bestSecY, bestOrd
                        );
                        if (bestRecord != null && bestRecord != prev) {
                            BlockPos p = bestRecord.getPos();
                            long pdx = (long) p.getX() - ox;
                            long pdy = (long) p.getY() - oy;
                            long pdz = (long) p.getZ() - oz;
                            bestDistSq = pdx * pdx + pdy * pdy + pdz * pdz;
                            bestCz = SectionPos.blockToSectionCoord(p.getZ());
                            bestCx = SectionPos.blockToSectionCoord(p.getX());
                            bestSecY = SectionPos.blockToSectionCoord(p.getY());
                            bestOrd = this.papi$lastBestOrd;
                        }
                    }
                }
            }
        }
        return bestRecord;
    }

    @Unique
    private int papi$lastBestOrd;

    @Unique
    private @Nullable PoiRecord papi$scanClosestChunk(
            SectionStorageAccessor<PoiSection> storage,
            PapiPoiColumnIndex<PoiSection> colIdx,
            Predicate<Holder<PoiType>> typePredicate,
            @Nullable Predicate<BlockPos> posPredicate,
            PoiManager.Occupancy occupancy,
            Predicate<? super PoiRecord> occupancyTest,
            int ox, int oy, int oz, long maxRadiusSq,
            int cx, int cz,
            int minSecY, int maxSecY, int centerSecY, int maxSecOffset,
            @Nullable PoiRecord bestRecord,
            long bestDistSq, int bestCz, int bestCx, int bestSecY, int bestOrd
    ) {
        int chunkMinX = cx << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = cz << 4;
        int chunkMaxZ = chunkMinZ + 15;
        long cdx = ox < chunkMinX ? (chunkMinX - ox) : (ox > chunkMaxX ? (ox - chunkMaxX) : 0);
        long cdz = oz < chunkMinZ ? (chunkMinZ - oz) : (oz > chunkMaxZ ? (oz - chunkMaxZ) : 0);
        long minChunkDistSq = cdx * cdx + cdz * cdz;
        if (minChunkDistSq > maxRadiusSq || minChunkDistSq > bestDistSq) {
            return bestRecord;
        }
        long chunkKey = ChunkPos.pack(cx, cz);
        long colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
        if (colMask == 0L) {
            return bestRecord;
        }

        for (int dyOff = 0; dyOff <= maxSecOffset; dyOff++) {
            int passes = dyOff == 0 ? 1 : 2;
            for (int side = 0; side < passes; side++) {
                int secY = side == 0 ? (centerSecY + dyOff) : (centerSecY - dyOff);
                if (secY < minSecY || secY > maxSecY) {
                    continue;
                }
                if (colMask >= 0L && (colMask & (1L << (secY - minSecY))) == 0L) {
                    continue;
                }
                int secMinY = secY << 4;
                int secMaxY = secMinY + 15;
                long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                long min3DSq = minChunkDistSq + sdy * sdy;
                if (min3DSq > maxRadiusSq || min3DSq > bestDistSq) {
                    continue;
                }
                long sectionKey = SectionPos.asLong(cx, secY, cz);
                Optional<PoiSection> opt = colMask >= 0L
                        ? colIdx.papi$getLoadedSectionDirect(sectionKey)
                        : storage.papi$getOrLoad(sectionKey);
                if (colMask < 0L) {
                    colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                    if (colMask == 0L) {
                        return bestRecord;
                    }
                }
                if (opt == null || opt.isEmpty()) {
                    continue;
                }
                PoiSection section = opt.get();
                if (section instanceof PapiPoiSectionFast fast && !fast.papi$mayMatchOccupancy(occupancy)) {
                    continue;
                }
                Map<Holder<PoiType>, Set<PoiRecord>> byType = ((PoiSectionAccessor) section).papi$getByType();
                if (byType == null || byType.isEmpty()) {
                    continue;
                }
                int ord = 0;
                if (typePredicate instanceof PapiSinglePoiTypeFilter single) {
                    Set<PoiRecord> set = byType.get(single.targetType());
                    if (set != null && !set.isEmpty()) {
                        for (PoiRecord rec : set) {
                            BlockPos p = rec.getPos();
                            long rdx = (long) p.getX() - ox;
                            long rdy = (long) p.getY() - oy;
                            long rdz = (long) p.getZ() - oz;
                            long distSq = rdx * rdx + rdy * rdy + rdz * rdz;
                            if (distSq <= maxRadiusSq
                                    && papi$beatsClosest(distSq, cz, cx, secY, ord, bestDistSq, bestCz, bestCx, bestSecY, bestOrd)
                                    && occupancyTest.test(rec)
                                    && (posPredicate == null || posPredicate.test(p))) {
                                bestDistSq = distSq;
                                bestCz = cz;
                                bestCx = cx;
                                bestSecY = secY;
                                bestOrd = ord;
                                bestRecord = rec;
                                this.papi$lastBestOrd = ord;
                            }
                            ord++;
                        }
                    }
                } else {
                    for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : byType.entrySet()) {
                        Set<PoiRecord> set = entry.getValue();
                        if (!set.isEmpty() && typePredicate.test(entry.getKey())) {
                            for (PoiRecord rec : set) {
                                BlockPos p = rec.getPos();
                                long rdx = (long) p.getX() - ox;
                                long rdy = (long) p.getY() - oy;
                                long rdz = (long) p.getZ() - oz;
                                long distSq = rdx * rdx + rdy * rdy + rdz * rdz;
                                if (distSq <= maxRadiusSq
                                        && papi$beatsClosest(distSq, cz, cx, secY, ord, bestDistSq, bestCz, bestCx, bestSecY, bestOrd)
                                        && occupancyTest.test(rec)
                                        && (posPredicate == null || posPredicate.test(p))) {
                                    bestDistSq = distSq;
                                    bestCz = cz;
                                    bestCx = cx;
                                    bestSecY = secY;
                                    bestOrd = ord;
                                    bestRecord = rec;
                                    this.papi$lastBestOrd = ord;
                                }
                                ord++;
                            }
                        }
                    }
                }
            }
        }
        return bestRecord;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private @Nullable PoiRecord papi$findFirstInRange(
            Predicate<Holder<PoiType>> typePredicate,
            @Nullable Predicate<BlockPos> posPredicate,
            @Nullable BiPredicate<Holder<PoiType>, BlockPos> biPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy
    ) {
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        PapiPoiColumnIndex<PoiSection> colIdx = (PapiPoiColumnIndex<PoiSection>) (Object) this;
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        int originCx = SectionPos.blockToSectionCoord(ox);
        int originCz = SectionPos.blockToSectionCoord(oz);
        int chunkRadius = Math.floorDiv(radius, 16) + 1;
        long maxRadiusSq = (long) radius * (long) radius;

        LevelHeightAccessor heightAccessor = storage.papi$getLevelHeightAccessor();
        int minSecY = heightAccessor.getMinSectionY();
        int maxSecY = heightAccessor.getMaxSectionY();
        Predicate<? super PoiRecord> occTest = occupancy.getTest();

        for (int cz = originCz - chunkRadius; cz <= originCz + chunkRadius; cz++) {
            int chunkMinZ = cz << 4;
            int chunkMaxZ = chunkMinZ + 15;
            long cdz = oz < chunkMinZ ? (chunkMinZ - oz) : (oz > chunkMaxZ ? (oz - chunkMaxZ) : 0);
            long cdzSq = cdz * cdz;
            if (cdzSq > maxRadiusSq) {
                continue;
            }
            for (int cx = originCx - chunkRadius; cx <= originCx + chunkRadius; cx++) {
                int chunkMinX = cx << 4;
                int chunkMaxX = chunkMinX + 15;
                long cdx = ox < chunkMinX ? (chunkMinX - ox) : (ox > chunkMaxX ? (ox - chunkMaxX) : 0);
                long minChunkDistSq = cdx * cdx + cdzSq;
                if (minChunkDistSq > maxRadiusSq) {
                    continue;
                }
                long chunkKey = ChunkPos.pack(cx, cz);
                long colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                if (colMask == 0L) {
                    continue;
                }
                if (colMask > 0L) {
                    long bits = colMask;
                    while (bits != 0L) {
                        int bit = Long.numberOfTrailingZeros(bits);
                        bits &= (bits - 1L);
                        int secY = minSecY + bit;
                        int secMinY = secY << 4;
                        int secMaxY = secMinY + 15;
                        long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                        if (minChunkDistSq + sdy * sdy > maxRadiusSq) {
                            continue;
                        }
                        Optional<PoiSection> opt = colIdx.papi$getLoadedSectionDirect(SectionPos.asLong(cx, secY, cz));
                        if (opt != null && opt.isPresent()) {
                            PoiRecord match = papi$firstMatchInSection(
                                    opt.get(), typePredicate, posPredicate, biPredicate,
                                    occupancy, occTest, ox, oy, oz, maxRadiusSq
                            );
                            if (match != null) {
                                return match;
                            }
                        }
                    }
                } else {
                    for (int secY = minSecY; secY <= maxSecY; secY++) {
                        int secMinY = secY << 4;
                        int secMaxY = secMinY + 15;
                        long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                        if (minChunkDistSq + sdy * sdy > maxRadiusSq) {
                            continue;
                        }
                        Optional<PoiSection> opt = storage.papi$getOrLoad(SectionPos.asLong(cx, secY, cz));
                        if (colMask < 0L) {
                            colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                            if (colMask == 0L) {
                                break;
                            }
                        }
                        if (opt != null && opt.isPresent()) {
                            PoiRecord match = papi$firstMatchInSection(
                                    opt.get(), typePredicate, posPredicate, biPredicate,
                                    occupancy, occTest, ox, oy, oz, maxRadiusSq
                            );
                            if (match != null) {
                                return match;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Unique
    private static @Nullable PoiRecord papi$firstMatchInSection(
            PoiSection section,
            Predicate<Holder<PoiType>> typePredicate,
            @Nullable Predicate<BlockPos> posPredicate,
            @Nullable BiPredicate<Holder<PoiType>, BlockPos> biPredicate,
            PoiManager.Occupancy occupancy,
            Predicate<? super PoiRecord> occTest,
            int ox, int oy, int oz,
            long maxRadiusSq
    ) {
        if (section instanceof PapiPoiSectionFast fast && !fast.papi$mayMatchOccupancy(occupancy)) {
            return null;
        }
        Map<Holder<PoiType>, Set<PoiRecord>> byType = ((PoiSectionAccessor) section).papi$getByType();
        if (byType == null || byType.isEmpty()) {
            return null;
        }
        if (typePredicate instanceof PapiSinglePoiTypeFilter single) {
            Set<PoiRecord> set = byType.get(single.targetType());
            if (set != null && !set.isEmpty()) {
                for (PoiRecord rec : set) {
                    BlockPos p = rec.getPos();
                    long dx = (long) p.getX() - ox;
                    long dy = (long) p.getY() - oy;
                    long dz = (long) p.getZ() - oz;
                    if (dx * dx + dy * dy + dz * dz <= maxRadiusSq && occTest.test(rec)) {
                        if ((posPredicate == null || posPredicate.test(p))
                                && (biPredicate == null || biPredicate.test(rec.getPoiType(), p))) {
                            return rec;
                        }
                    }
                }
            }
            return null;
        }
        for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : byType.entrySet()) {
            Set<PoiRecord> set = entry.getValue();
            if (!set.isEmpty() && typePredicate.test(entry.getKey())) {
                for (PoiRecord rec : set) {
                    BlockPos p = rec.getPos();
                    long dx = (long) p.getX() - ox;
                    long dy = (long) p.getY() - oy;
                    long dz = (long) p.getZ() - oz;
                    if (dx * dx + dy * dy + dz * dz <= maxRadiusSq && occTest.test(rec)) {
                        if ((posPredicate == null || posPredicate.test(p))
                                && (biPredicate == null || biPredicate.test(rec.getPoiType(), p))) {
                            return rec;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private long papi$countInRange(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy
    ) {
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        PapiPoiColumnIndex<PoiSection> colIdx = (PapiPoiColumnIndex<PoiSection>) (Object) this;
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        int originCx = SectionPos.blockToSectionCoord(ox);
        int originCz = SectionPos.blockToSectionCoord(oz);
        int chunkRadius = Math.floorDiv(radius, 16) + 1;
        long maxRadiusSq = (long) radius * (long) radius;

        LevelHeightAccessor heightAccessor = storage.papi$getLevelHeightAccessor();
        int minSecY = heightAccessor.getMinSectionY();
        int maxSecY = heightAccessor.getMaxSectionY();
        Predicate<? super PoiRecord> occTest = occupancy.getTest();
        long count = 0L;

        for (int cz = originCz - chunkRadius; cz <= originCz + chunkRadius; cz++) {
            int chunkMinZ = cz << 4;
            int chunkMaxZ = chunkMinZ + 15;
            long cdz = oz < chunkMinZ ? (chunkMinZ - oz) : (oz > chunkMaxZ ? (oz - chunkMaxZ) : 0);
            long cdzSq = cdz * cdz;
            if (cdzSq > maxRadiusSq) {
                continue;
            }
            for (int cx = originCx - chunkRadius; cx <= originCx + chunkRadius; cx++) {
                int chunkMinX = cx << 4;
                int chunkMaxX = chunkMinX + 15;
                long cdx = ox < chunkMinX ? (chunkMinX - ox) : (ox > chunkMaxX ? (ox - chunkMaxX) : 0);
                long minChunkDistSq = cdx * cdx + cdzSq;
                if (minChunkDistSq > maxRadiusSq) {
                    continue;
                }
                long chunkKey = ChunkPos.pack(cx, cz);
                long colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                if (colMask == 0L) {
                    continue;
                }
                if (colMask > 0L) {
                    long bits = colMask;
                    while (bits != 0L) {
                        int bit = Long.numberOfTrailingZeros(bits);
                        bits &= (bits - 1L);
                        int secY = minSecY + bit;
                        int secMinY = secY << 4;
                        int secMaxY = secMinY + 15;
                        long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                        if (minChunkDistSq + sdy * sdy > maxRadiusSq) {
                            continue;
                        }
                        Optional<PoiSection> opt = colIdx.papi$getLoadedSectionDirect(SectionPos.asLong(cx, secY, cz));
                        if (opt != null && opt.isPresent()) {
                            count += papi$countSectionRecords(opt.get(), typePredicate, occupancy, occTest, ox, oy, oz, maxRadiusSq);
                        }
                    }
                } else {
                    for (int secY = minSecY; secY <= maxSecY; secY++) {
                        int secMinY = secY << 4;
                        int secMaxY = secMinY + 15;
                        long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                        if (minChunkDistSq + sdy * sdy > maxRadiusSq) {
                            continue;
                        }
                        Optional<PoiSection> opt = storage.papi$getOrLoad(SectionPos.asLong(cx, secY, cz));
                        if (colMask < 0L) {
                            colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                            if (colMask == 0L) {
                                break;
                            }
                        }
                        if (opt != null && opt.isPresent()) {
                            count += papi$countSectionRecords(opt.get(), typePredicate, occupancy, occTest, ox, oy, oz, maxRadiusSq);
                        }
                    }
                }
            }
        }
        return count;
    }

    @Unique
    private static long papi$countSectionRecords(
            PoiSection section,
            Predicate<Holder<PoiType>> typePredicate,
            PoiManager.Occupancy occupancy,
            Predicate<? super PoiRecord> occTest,
            int ox, int oy, int oz,
            long maxRadiusSq
    ) {
        if (section instanceof PapiPoiSectionFast fast && !fast.papi$mayMatchOccupancy(occupancy)) {
            return 0L;
        }
        Map<Holder<PoiType>, Set<PoiRecord>> byType = ((PoiSectionAccessor) section).papi$getByType();
        if (byType == null || byType.isEmpty()) {
            return 0L;
        }
        long c = 0L;
        if (typePredicate instanceof PapiSinglePoiTypeFilter single) {
            Set<PoiRecord> set = byType.get(single.targetType());
            if (set != null && !set.isEmpty()) {
                for (PoiRecord rec : set) {
                    BlockPos p = rec.getPos();
                    long dx = (long) p.getX() - ox;
                    long dy = (long) p.getY() - oy;
                    long dz = (long) p.getZ() - oz;
                    if (dx * dx + dy * dy + dz * dz <= maxRadiusSq && occTest.test(rec)) {
                        c++;
                    }
                }
            }
            return c;
        }
        for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : byType.entrySet()) {
            Set<PoiRecord> set = entry.getValue();
            if (!set.isEmpty() && typePredicate.test(entry.getKey())) {
                for (PoiRecord rec : set) {
                    BlockPos p = rec.getPos();
                    long dx = (long) p.getX() - ox;
                    long dy = (long) p.getY() - oy;
                    long dz = (long) p.getZ() - oz;
                    if (dx * dx + dy * dy + dz * dz <= maxRadiusSq && occTest.test(rec)) {
                        c++;
                    }
                }
            }
        }
        return c;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void papi$collectInRange(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            boolean squareOnly,
            List<PoiRecord> out
    ) {
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        PapiPoiColumnIndex<PoiSection> colIdx = (PapiPoiColumnIndex<PoiSection>) (Object) this;
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        int originCx = SectionPos.blockToSectionCoord(ox);
        int originCz = SectionPos.blockToSectionCoord(oz);
        int chunkRadius = Math.floorDiv(radius, 16) + 1;
        long maxRadiusSq = (long) radius * (long) radius;

        LevelHeightAccessor heightAccessor = storage.papi$getLevelHeightAccessor();
        int minSecY = heightAccessor.getMinSectionY();
        int maxSecY = heightAccessor.getMaxSectionY();
        Predicate<? super PoiRecord> occTest = occupancy.getTest();

        for (int cz = originCz - chunkRadius; cz <= originCz + chunkRadius; cz++) {
            int chunkMinZ = cz << 4;
            int chunkMaxZ = chunkMinZ + 15;
            if (chunkMaxZ < oz - radius || chunkMinZ > oz + radius) {
                continue;
            }
            long cdz = oz < chunkMinZ ? (chunkMinZ - oz) : (oz > chunkMaxZ ? (oz - chunkMaxZ) : 0);
            long cdzSq = cdz * cdz;
            if (!squareOnly && cdzSq > maxRadiusSq) {
                continue;
            }
            for (int cx = originCx - chunkRadius; cx <= originCx + chunkRadius; cx++) {
                int chunkMinX = cx << 4;
                int chunkMaxX = chunkMinX + 15;
                if (chunkMaxX < ox - radius || chunkMinX > ox + radius) {
                    continue;
                }
                long cdx = ox < chunkMinX ? (chunkMinX - ox) : (ox > chunkMaxX ? (ox - chunkMaxX) : 0);
                long minChunkDistSq = cdx * cdx + cdzSq;
                if (!squareOnly && minChunkDistSq > maxRadiusSq) {
                    continue;
                }
                long chunkKey = ChunkPos.pack(cx, cz);
                long colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                if (colMask == 0L) {
                    continue;
                }
                if (colMask > 0L) {
                    long bits = colMask;
                    while (bits != 0L) {
                        int bit = Long.numberOfTrailingZeros(bits);
                        bits &= (bits - 1L);
                        int secY = minSecY + bit;
                        if (!squareOnly) {
                            int secMinY = secY << 4;
                            int secMaxY = secMinY + 15;
                            long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                            if (minChunkDistSq + sdy * sdy > maxRadiusSq) {
                                continue;
                            }
                        }
                        Optional<PoiSection> opt = colIdx.papi$getLoadedSectionDirect(SectionPos.asLong(cx, secY, cz));
                        if (opt != null && opt.isPresent()) {
                            papi$appendMatchingRecords(
                                    opt.get(), typePredicate, occupancy, occTest,
                                    ox, oy, oz, radius, maxRadiusSq, squareOnly, out
                            );
                        }
                    }
                } else {
                    for (int secY = minSecY; secY <= maxSecY; secY++) {
                        if (!squareOnly) {
                            int secMinY = secY << 4;
                            int secMaxY = secMinY + 15;
                            long sdy = oy < secMinY ? (secMinY - oy) : (oy > secMaxY ? (oy - secMaxY) : 0);
                            if (minChunkDistSq + sdy * sdy > maxRadiusSq) {
                                continue;
                            }
                        }
                        Optional<PoiSection> opt = storage.papi$getOrLoad(SectionPos.asLong(cx, secY, cz));
                        if (colMask < 0L) {
                            colMask = colIdx.papi$getColumnNonEmptyMask(chunkKey);
                            if (colMask == 0L) {
                                break;
                            }
                        }
                        if (opt != null && opt.isPresent()) {
                            papi$appendMatchingRecords(
                                    opt.get(), typePredicate, occupancy, occTest,
                                    ox, oy, oz, radius, maxRadiusSq, squareOnly, out
                            );
                        }
                    }
                }
            }
        }
    }

    @Unique
    private static void papi$appendMatchingRecords(
            PoiSection section,
            Predicate<Holder<PoiType>> typePredicate,
            PoiManager.Occupancy occupancy,
            Predicate<? super PoiRecord> occTest,
            int ox, int oy, int oz,
            int radius, long maxRadiusSq,
            boolean squareOnly,
            List<PoiRecord> out
    ) {
        if (section instanceof PapiPoiSectionFast fast && !fast.papi$mayMatchOccupancy(occupancy)) {
            return;
        }
        Map<Holder<PoiType>, Set<PoiRecord>> byType = ((PoiSectionAccessor) section).papi$getByType();
        if (byType == null || byType.isEmpty()) {
            return;
        }
        if (typePredicate instanceof PapiSinglePoiTypeFilter single) {
            Set<PoiRecord> set = byType.get(single.targetType());
            if (set != null && !set.isEmpty()) {
                for (PoiRecord rec : set) {
                    if (occTest.test(rec) && papi$matchesBounds(rec.getPos(), ox, oy, oz, radius, maxRadiusSq, squareOnly)) {
                        out.add(rec);
                    }
                }
            }
            return;
        }
        for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : byType.entrySet()) {
            Set<PoiRecord> set = entry.getValue();
            if (!set.isEmpty() && typePredicate.test(entry.getKey())) {
                for (PoiRecord rec : set) {
                    if (occTest.test(rec) && papi$matchesBounds(rec.getPos(), ox, oy, oz, radius, maxRadiusSq, squareOnly)) {
                        out.add(rec);
                    }
                }
            }
        }
    }

    @Unique
    private static boolean papi$matchesBounds(
            BlockPos p, int ox, int oy, int oz, int radius, long maxRadiusSq, boolean squareOnly
    ) {
        int dx = p.getX() - ox;
        int dz = p.getZ() - oz;
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
            return false;
        }
        if (squareOnly) {
            return true;
        }
        long ldx = dx;
        long ldy = (long) p.getY() - oy;
        long ldz = dz;
        return ldx * ldx + ldy * ldy + ldz * ldz <= maxRadiusSq;
    }

    @Unique
    private static int papi$minRingHorizDist(int ox, int oz, int r) {
        int base = (r - 1) << 4;
        int east = base + (16 - (ox & 15));
        int west = base + ((ox & 15) + 1);
        int south = base + (16 - (oz & 15));
        int north = base + ((oz & 15) + 1);
        return Math.min(Math.min(east, west), Math.min(south, north));
    }

    @Unique
    private static boolean papi$beatsClosest(
            long distSq, int cz, int cx, int secY, int ord,
            long bestDistSq, int bestCz, int bestCx, int bestSecY, int bestOrd
    ) {
        if (distSq != bestDistSq) {
            return distSq < bestDistSq;
        }
        if (cz != bestCz) {
            return cz < bestCz;
        }
        if (cx != bestCx) {
            return cx < bestCx;
        }
        if (secY != bestSecY) {
            return secY < bestSecY;
        }
        return ord < bestOrd;
    }

    @Unique
    private static boolean papi$beatsPortal(
            long distSq, int py, int cz, int cx, int secY, int ord,
            long bestDistSq, int bestY, int bestCz, int bestCx, int bestSecY, int bestOrd
    ) {
        if (distSq != bestDistSq) {
            return distSq < bestDistSq;
        }
        if (py != bestY) {
            return py < bestY;
        }
        if (cz != bestCz) {
            return cz < bestCz;
        }
        if (cx != bestCx) {
            return cx < bestCx;
        }
        if (secY != bestSecY) {
            return secY < bestSecY;
        }
        return ord < bestOrd;
    }
}
