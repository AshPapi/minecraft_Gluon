package net.ashpapi.papioptimization.ai;

import it.unimi.dsi.fastutil.HashCommon;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.ashpapi.papioptimization.access.PapiLevelBlockEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.common.config.NeoForgeSyncedConfig;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jspecify.annotations.Nullable;

public final class SpatialEntityQueryHelper {

    public static final EntityTypeTest<Entity, Villager> EXACT_VILLAGER_TEST = new EntityTypeTest<>() {
        @Override
        public @Nullable Villager tryCast(Entity entity) {
            return entity.getType() == EntityTypes.VILLAGER ? (Villager) entity : null;
        }

        @Override
        public Class<? extends Entity> getBaseClass() {
            return Villager.class;
        }
    };

    public static final EntityTypeTest<Entity, Llama> LLAMA_TEST = EntityTypeTest.forClass(Llama.class);

    public static final EntityTypeTest<Entity, AbstractMinecart> MINECART_TEST = EntityTypeTest.forClass(AbstractMinecart.class);

    public static final ClassValue<Boolean> DEFAULT_IS_LADDER_BLOCK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("isLadder", BlockState.class, LevelReader.class, BlockPos.class, LivingEntity.class);
                return m.getDeclaringClass() == IBlockExtension.class;
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    };

    public static final ClassValue<Boolean> VANILLA_AMBIENT_SOUND_MOB = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!Mob.class.isAssignableFrom(type)) {
                return Boolean.FALSE;
            }
            for (Class<?> cur = type; cur != null && cur != Mob.class; cur = cur.getSuperclass()) {
                try {
                    cur.getDeclaredMethod("playAmbientSound");
                    return Boolean.FALSE;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
    };

    private static final ThreadLocal<CrowdedClimbableState> CROWDED_STATE =
            ThreadLocal.withInitial(CrowdedClimbableState::new);

    private SpatialEntityQueryHelper() {}

    public static void enterPushableQuery() {
        CROWDED_STATE.get().pushQueryDepth++;
    }

    public static void exitPushableQuery() {
        CrowdedClimbableState st = CROWDED_STATE.get();
        if (st.pushQueryDepth > 0) {
            st.pushQueryDepth--;
        }
        if (st.pushQueryDepth == 0) {
            st.inCrowdedSection = false;
        }
    }

    public static boolean enterCrowdedSectionIfPushQuery() {
        CrowdedClimbableState st = CROWDED_STATE.get();
        if (st.pushQueryDepth > 0 && !st.inCrowdedSection) {
            st.inCrowdedSection = true;
            return true;
        }
        return false;
    }

    public static void exitCrowdedSection() {
        CROWDED_STATE.get().inCrowdedSection = false;
    }

    public static int checkCrowdedClimbable(LivingEntity entity) {
        CrowdedClimbableState st = CROWDED_STATE.get();
        if (!st.inCrowdedSection) {
            return -1;
        }
        if (entity.isSpectator()) {
            return 0;
        }
        if (entity.isFallFlying() || NeoForgeSyncedConfig.INSTANCE.fullBoundingBoxLadders.get()) {
            return -1;
        }
        Level level = entity.level();
        if (!(level instanceof PapiLevelBlockEpoch epochHost)) {
            return -1;
        }
        st.ensureLevel(level);

        BlockPos pos = entity.blockPosition();
        long key = pos.asLong();
        long curEpoch = epochHost.papi$getBlockEpoch(key);
        int slot = (int) HashCommon.mix(key) & 15;
        if (st.occupied[slot] && st.keys[slot] == key && st.epochs[slot] == curEpoch) {
            return st.values[slot];
        }

        BlockState blockState = entity.getInBlockState();
        byte val;
        if (blockState.isAir()) {
            val = 0;
        } else if (DEFAULT_IS_LADDER_BLOCK.get(blockState.getBlock().getClass())) {
            val = blockState.is(BlockTags.CLIMBABLE) ? (byte) 1 : (byte) 0;
        } else {
            val = -1;
        }
        st.occupied[slot] = true;
        st.keys[slot] = key;
        st.epochs[slot] = curEpoch;
        st.values[slot] = val;
        return val;
    }

    private static final class CrowdedClimbableState {
        int pushQueryDepth;
        boolean inCrowdedSection;
        @Nullable WeakReference<Level> levelRef;
        final boolean[] occupied = new boolean[16];
        final long[] keys = new long[16];
        final long[] epochs = new long[16];
        final byte[] values = new byte[16];

        void ensureLevel(Level level) {
            Level prev = this.levelRef != null ? this.levelRef.get() : null;
            if (prev != level) {
                this.levelRef = new WeakReference<>(level);
                Arrays.fill(this.occupied, false);
            }
        }
    }
}
