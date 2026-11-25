package net.ashpapi.papioptimization.mixin.poi;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.ashpapi.papioptimization.access.PapiPoiRecordOwner;
import net.ashpapi.papioptimization.access.PapiPoiSectionFast;
import net.ashpapi.papioptimization.ai.PapiPoiColumnIndex;
import net.ashpapi.papioptimization.ai.PapiSinglePoiTypeFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PoiSection.class, priority = 1500)
public abstract class PoiSectionFastMixin implements PapiPoiSectionFast {

    @Shadow
    @Final
    private Short2ObjectMap<PoiRecord> records;

    @Shadow
    @Final
    private Map<Holder<PoiType>, Set<PoiRecord>> byType;

    @Unique
    private boolean papi$occupancyDirty = true;

    @Unique
    private boolean papi$hasAnySpace;

    @Unique
    private boolean papi$hasAnyOccupied;

    @Unique
    private @Nullable PapiPoiColumnIndex<?> papi$columnIndex;

    @Unique
    private long papi$chunkKey;

    @Unique
    private int papi$sectionBit = -1;

    @Unique
    private void papi$recomputeOccupancy() {
        boolean anySpace = false;
        boolean anyOccupied = false;
        if (!this.records.isEmpty()) {
            for (PoiRecord rec : this.records.values()) {
                if (rec.hasSpace()) {
                    anySpace = true;
                }
                if (rec.isOccupied()) {
                    anyOccupied = true;
                }
                if (anySpace && anyOccupied) {
                    break;
                }
            }
        }
        this.papi$hasAnySpace = anySpace;
        this.papi$hasAnyOccupied = anyOccupied;
        this.papi$occupancyDirty = false;
    }

    @Override
    public boolean papi$isEmpty() {
        return this.records.isEmpty();
    }

    @Override
    public boolean papi$mayMatchOccupancy(PoiManager.Occupancy occupancy) {
        if (this.records.isEmpty()) {
            return false;
        }
        if (occupancy == PoiManager.Occupancy.ANY) {
            return true;
        }
        if (this.papi$occupancyDirty) {
            papi$recomputeOccupancy();
        }
        if (occupancy == PoiManager.Occupancy.HAS_SPACE) {
            return this.papi$hasAnySpace;
        }
        if (occupancy == PoiManager.Occupancy.IS_OCCUPIED) {
            return this.papi$hasAnyOccupied;
        }
        return true;
    }

    @Override
    public void papi$markOccupancyDirty() {
        this.papi$occupancyDirty = true;
    }

    @Override
    public void papi$bindColumnIndex(@Nullable PapiPoiColumnIndex<?> columnIndex, long chunkKey, int sectionBit) {
        this.papi$columnIndex = columnIndex;
        this.papi$chunkKey = chunkKey;
        this.papi$sectionBit = sectionBit;
    }

    @Override
    public void papi$forEachMatchingRecord(
            Predicate<Holder<PoiType>> typePredicate,
            PoiManager.Occupancy occupancy,
            Consumer<PoiRecord> consumer
    ) {
        if (!papi$mayMatchOccupancy(occupancy) || this.byType.isEmpty()) {
            return;
        }
        Predicate<? super PoiRecord> occTest = occupancy.getTest();
        if (typePredicate instanceof PapiSinglePoiTypeFilter single) {
            Set<PoiRecord> matchingSet = this.byType.get(single.targetType());
            if (matchingSet != null && !matchingSet.isEmpty()) {
                for (PoiRecord record : matchingSet) {
                    if (occTest.test(record)) {
                        consumer.accept(record);
                    }
                }
            }
            return;
        }
        for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : this.byType.entrySet()) {
            Set<PoiRecord> set = entry.getValue();
            if (!set.isEmpty() && typePredicate.test(entry.getKey())) {
                for (PoiRecord record : set) {
                    if (occTest.test(record)) {
                        consumer.accept(record);
                    }
                }
            }
        }
    }

    @Override
    public @Nullable PoiRecord papi$getRecordAt(BlockPos pos) {
        if (this.records.isEmpty()) {
            return null;
        }
        return this.records.get(SectionPos.sectionRelativePos(pos));
    }

    @Inject(method = "add(Lnet/minecraft/world/entity/ai/village/poi/PoiRecord;)Z", at = @At("RETURN"))
    private void papi$onAddRecord(PoiRecord record, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && record != null) {
            ((PapiPoiRecordOwner) record).papi$setOwnerSection(this);
            this.papi$occupancyDirty = true;
            if (this.records.size() == 1 && this.papi$columnIndex != null) {
                this.papi$columnIndex.papi$setSectionNonEmpty(this.papi$chunkKey, this.papi$sectionBit, true);
            }
        }
    }

    @WrapOperation(
        method = "remove",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/shorts/Short2ObjectMap;remove(S)Ljava/lang/Object;"
        )
    )
    private Object papi$clearOwnerOnRemove(Short2ObjectMap<PoiRecord> map, short key, Operation<Object> original) {
        Object removedObj = original.call(map, key);
        if (removedObj instanceof PoiRecord removed) {
            ((PapiPoiRecordOwner) removed).papi$setOwnerSection(null);
            this.papi$occupancyDirty = true;
            if (map.isEmpty() && this.papi$columnIndex != null) {
                this.papi$columnIndex.papi$setSectionNonEmpty(this.papi$chunkKey, this.papi$sectionBit, false);
            }
        }
        return removedObj;
    }

    @Inject(method = "release", at = @At("RETURN"))
    private void papi$onRelease(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            this.papi$occupancyDirty = true;
        }
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void papi$onClear(CallbackInfo ci) {
        if (!this.records.isEmpty()) {
            for (PoiRecord rec : this.records.values()) {
                ((PapiPoiRecordOwner) rec).papi$setOwnerSection(null);
            }
        }
        this.papi$hasAnySpace = false;
        this.papi$hasAnyOccupied = false;
        this.papi$occupancyDirty = false;
        if (this.papi$columnIndex != null) {
            this.papi$columnIndex.papi$setSectionNonEmpty(this.papi$chunkKey, this.papi$sectionBit, false);
        }
    }

    @Inject(method = "refresh", at = @At("RETURN"))
    private void papi$onRefresh(Consumer<BiConsumer<BlockPos, Holder<PoiType>>> consumer, CallbackInfo ci) {
        boolean nonEmpty = !this.records.isEmpty();
        if (nonEmpty) {
            for (PoiRecord rec : this.records.values()) {
                ((PapiPoiRecordOwner) rec).papi$setOwnerSection(this);
            }
        }
        this.papi$occupancyDirty = true;
        if (this.papi$columnIndex != null) {
            this.papi$columnIndex.papi$setSectionNonEmpty(this.papi$chunkKey, this.papi$sectionBit, nonEmpty);
        }
    }

    @Inject(method = "getRecords", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyGetRecords(
            Predicate<Holder<PoiType>> typePredicate,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Stream<PoiRecord>> cir
    ) {
        if (!papi$mayMatchOccupancy(occupancy)) {
            cir.setReturnValue(Stream.empty());
        }
    }

    @Inject(method = "exists", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyExists(BlockPos pos, Predicate<Holder<PoiType>> predicate, CallbackInfoReturnable<Boolean> cir) {
        PoiRecord rec = papi$getRecordAt(pos);
        cir.setReturnValue(rec != null && predicate.test(rec.getPoiType()));
    }

    @Inject(method = "getFreeTickets", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyFreeTickets(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        PoiRecord rec = papi$getRecordAt(pos);
        cir.setReturnValue(rec != null ? rec.getFreeTickets() : 0);
    }

    @Inject(method = "getType", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyGetType(BlockPos pos, CallbackInfoReturnable<Optional<Holder<PoiType>>> cir) {
        PoiRecord rec = papi$getRecordAt(pos);
        cir.setReturnValue(rec != null ? Optional.of(rec.getPoiType()) : Optional.empty());
    }
}
