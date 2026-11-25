package net.ashpapi.papioptimization.core;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SupportType;

public final class PapiEnumCache {

    public static final MobCategory[] MOB_CATEGORIES = MobCategory.values();
    public static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();
    public static final SupportType[] SUPPORT_TYPES = SupportType.values();
    public static final Direction.Axis[] AXES = Direction.Axis.values();

    private PapiEnumCache() {
    }
}
