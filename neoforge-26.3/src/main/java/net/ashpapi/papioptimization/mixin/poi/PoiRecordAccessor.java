package net.ashpapi.papioptimization.mixin.poi;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = PoiRecord.class, priority = 1500)
public interface PoiRecordAccessor {
    @Invoker("acquireTicket")
    boolean papi$acquireTicket();
}
