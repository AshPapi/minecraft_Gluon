package net.ashpapi.papioptimization.ai;

import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public interface PapiEquipmentTracker {

    ClassValue<Boolean> STANDARD_GET_ITEM_BY_SLOT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!LivingEntity.class.isAssignableFrom(type) || Player.class.isAssignableFrom(type)) {
                return Boolean.FALSE;
            }
            try {
                Method m = type.getMethod("getItemBySlot", EquipmentSlot.class);
                return m.getDeclaringClass() == LivingEntity.class;
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    };

    ClassValue<Boolean> VANILLA_SPRINT_PARTICLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> cur = type; cur != null && cur != Entity.class; cur = cur.getSuperclass()) {
                try {
                    cur.getDeclaredMethod("spawnSprintParticle");
                    return Boolean.FALSE;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) {
                    return Boolean.FALSE;
                }
            }
            return Entity.class.isAssignableFrom(type);
        }
    };

    boolean papi$isTrackable();

    boolean papi$isDirtySync();

    void papi$clearDirtySync();

    void papi$refreshNonEmptyMask();

    int papi$getNonEmptyMask();

    boolean papi$hasTickableEnchantments();

    static boolean usesStandardEquipment(LivingEntity entity) {
        return STANDARD_GET_ITEM_BY_SLOT.get(entity.getClass());
    }

    static boolean hasVanillaSprintParticle(Entity entity) {
        return entity.getClass() == LivingEntity.class || VANILLA_SPRINT_PARTICLE.get(entity.getClass());
    }
}
