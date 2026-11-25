package net.ashpapi.papioptimization.ai;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;

public record PapiSinglePoiTypeFilter(Holder<PoiType> targetType) implements Predicate<Holder<PoiType>> {

    @Override
    public boolean test(Holder<PoiType> holder) {
        return this.targetType.equals(holder);
    }
}
