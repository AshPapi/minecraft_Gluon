package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.ashpapi.papioptimization.ai.PapiEquipmentTracker;
import net.ashpapi.papioptimization.core.PapiEnumCache;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityEquipmentEpochMixin {

    @Shadow @Final protected EntityEquipment equipment;

    @Shadow public abstract ItemStack getItemBySlot(EquipmentSlot slot);
    @Shadow protected abstract boolean equipmentHasChanged(ItemStack oldItem, ItemStack newItem);

    @WrapMethod(method = "collectEquipmentChanges")
    private @Nullable Map<EquipmentSlot, ItemStack> papi$fastCollectEquipmentChanges(
            Map<EquipmentSlot, ItemStack> lastEquipmentItems,
            Operation<@Nullable Map<EquipmentSlot, ItemStack>> original
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        PapiEquipmentTracker tracker = null;
        if (this.equipment instanceof PapiEquipmentTracker t && t.papi$isTrackable() && PapiEquipmentTracker.usesStandardEquipment(self)) {
            tracker = t;
            if (!t.papi$isDirtySync() && t.papi$getNonEmptyMask() == 0) {
                return null;
            }
        }

        EquipmentSlot[] slots = PapiEnumCache.EQUIPMENT_SLOTS;
        for (int i = 0, len = slots.length; i < len; i++) {
            EquipmentSlot slot = slots[i];
            ItemStack prev = lastEquipmentItems.get(slot);
            ItemStack cur = this.getItemBySlot(slot);
            if (prev != cur && this.equipmentHasChanged(prev, cur)) {
                Map<EquipmentSlot, ItemStack> changes = original.call(lastEquipmentItems);
                if (tracker != null) {
                    tracker.papi$refreshNonEmptyMask();
                    tracker.papi$clearDirtySync();
                }
                return changes;
            }
        }

        if (tracker != null) {
            tracker.papi$clearDirtySync();
        }
        return null;
    }
}
