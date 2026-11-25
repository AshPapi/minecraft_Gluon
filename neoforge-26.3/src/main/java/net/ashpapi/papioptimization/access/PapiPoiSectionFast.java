package net.ashpapi.papioptimization.access;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.PapiPoiColumnIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.jspecify.annotations.Nullable;

public interface PapiPoiSectionFast {

    boolean papi$isEmpty();

    boolean papi$mayMatchOccupancy(PoiManager.Occupancy occupancy);

    void papi$markOccupancyDirty();

    void papi$bindColumnIndex(@Nullable PapiPoiColumnIndex<?> columnIndex, long chunkKey, int sectionBit);

    void papi$forEachMatchingRecord(
            Predicate<Holder<PoiType>> typePredicate,
            PoiManager.Occupancy occupancy,
            Consumer<PoiRecord> consumer
    );

    @Nullable PoiRecord papi$getRecordAt(BlockPos pos);
}
