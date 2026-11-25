package net.ashpapi.papioptimization.pathfinding;

import java.util.function.Predicate;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jspecify.annotations.Nullable;

public final class PapiPathTypeHelper {

    public static final int NEIGHBOR_UNSET = 0;
    public static final int NEIGHBOR_NONE = 0xFFFF;

    private static final PathType[] TYPES = PathType.values();

    private static final ClassValue<Boolean> DEFAULT_ADJACENT_PATH_TYPE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!type.getName().startsWith("net.minecraft.")) {
                return Boolean.FALSE;
            }
            try {
                Class<?> declaring = type.getMethod(
                        "getAdjacentBlockPathType",
                        BlockState.class,
                        BlockGetter.class,
                        BlockPos.class,
                        Mob.class,
                        PathType.class
                ).getDeclaringClass();
                return declaring == IBlockExtension.class;
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    };

    private static final ClassValue<Boolean> CUSTOM_RECOMPUTE_NAV = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Class<?> declaring = type.getMethod("shouldRecomputePath", BlockPos.class).getDeclaringClass();
                return declaring != PathNavigation.class;
            } catch (Throwable ignored) {
                return Boolean.TRUE;
            }
        }
    };

    public static final Predicate<BlockState> MAY_HAVE_NEIGHBOR_HAZARD = state -> {
        if (state.isAir()) {
            return false;
        }
        PapiBlockStatePathType access = (PapiBlockStatePathType) state;
        if (!access.papi$isCacheable()) {
            return true;
        }
        return getOrComputeNeighborHazard(state, access, PapiTagEpoch.current()) != null;
    };

    private PapiPathTypeHelper() {}

    public static boolean isCacheableBlock(Block block) {
        return block != null
                && !block.hasDynamicShape()
                && PapiTagEpoch.isCacheableBlock(block)
                && DEFAULT_ADJACENT_PATH_TYPE.get(block.getClass());
    }

    public static boolean hasCustomShouldRecomputePath(Class<?> navClass) {
        return CUSTOM_RECOMPUTE_NAV.get(navClass);
    }

    public static @Nullable PathType bySlot(int slot) {
        if (slot <= 0 || slot > TYPES.length) {
            return null;
        }
        return TYPES[slot - 1];
    }

    public static int toSlot(@Nullable PathType type) {
        return type == null ? 0 : (type.ordinal() + 1);
    }

    public static PathType getOrComputeSelfPathType(BlockState state, PapiBlockStatePathType access, int epoch) {
        if (state.isAir()) {
            return PathType.OPEN;
        }
        PathType cached = access.papi$getCachedPathType(epoch);
        if (cached != null) {
            return cached;
        }
        PathType computed = computeCacheableSelfPathType(state);
        if (PapiTagEpoch.current() == epoch) {
            access.papi$setCachedPathType(computed, epoch);
        }
        return computed;
    }

    public static @Nullable PathType getOrComputeNeighborHazard(BlockState state, PapiBlockStatePathType access, int epoch) {
        if (state.isAir()) {
            return null;
        }
        int slot = access.papi$getCachedNeighborSlot(epoch);
        if (slot == NEIGHBOR_NONE) {
            return null;
        }
        if (slot != NEIGHBOR_UNSET) {
            return bySlot(slot);
        }
        PathType selfType = getOrComputeSelfPathType(state, access, epoch);
        PathType hazard = computeCacheableNeighborHazard(state, selfType);
        if (PapiTagEpoch.current() == epoch) {
            access.papi$setCachedNeighborSlot(hazard == null ? NEIGHBOR_NONE : toSlot(hazard), epoch);
        }
        return hazard;
    }

    public static @Nullable PathType mapSelfToNeighborHazard(PathType pathType) {
        if (pathType == PathType.DAMAGING) {
            return PathType.DAMAGING_IN_NEIGHBOR;
        }
        if (pathType == PathType.FIRE || pathType == PathType.LAVA) {
            return PathType.FIRE_IN_NEIGHBOR;
        }
        if (pathType == PathType.WATER) {
            return PathType.WATER_BORDER;
        }
        if (pathType == PathType.DAMAGE_CAUTIOUS) {
            return PathType.DAMAGE_CAUTIOUS;
        }
        return null;
    }

    private static @Nullable PathType computeCacheableNeighborHazard(BlockState state, PathType selfType) {
        PathType adj = state.getAdjacentBlockPathType(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, null, selfType);
        if (adj != null) {
            return adj;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            PathType fluidAdj = fluid.getAdjacentBlockPathType(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, null, selfType);
            if (fluidAdj != null) {
                return fluidAdj;
            }
        }
        return mapSelfToNeighborHazard(selfType);
    }

    public static PathType computeCacheableSelfPathType(BlockState state) {
        PathType ext = state.getBlockPathType(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, null);
        if (ext != null) {
            return ext;
        }
        Block block = state.getBlock();
        if (state.isAir()) {
            return PathType.OPEN;
        }
        if (state.is(BlockTags.TRAPDOORS) || state.is(Blocks.LILY_PAD) || state.is(Blocks.BIG_DRIPLEAF)) {
            return PathType.TRAPDOOR;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return PathType.POWDER_SNOW;
        }
        if (state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)) {
            return PathType.DAMAGING;
        }
        if (state.is(Blocks.HONEY_BLOCK)) {
            return PathType.STICKY_HONEY;
        }
        if (state.is(Blocks.COCOA)) {
            return PathType.COCOA;
        }
        if (state.is(Blocks.WITHER_ROSE) || state.is(BlockTags.SPELEOTHEMS)) {
            return PathType.DAMAGE_CAUTIOUS;
        }
        FluidState fluid = state.getFluidState();
        PathType fluidType = fluid.getBlockPathType(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, null, false);
        if (fluidType != null) {
            return fluidType;
        }
        if (fluid.is(FluidTags.LAVA)) {
            return PathType.LAVA;
        }
        if (WalkNodeEvaluator.isBurningBlock(state)) {
            return PathType.FIRE;
        }
        if (block instanceof DoorBlock door) {
            if (state.getValue(DoorBlock.OPEN)) {
                return PathType.DOOR_OPEN;
            }
            return door.type().canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
        }
        if (block instanceof BaseRailBlock) {
            return PathType.RAIL;
        }
        if (block instanceof LeavesBlock) {
            return PathType.LEAVES;
        }
        if (state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS)
                || (block instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN))) {
            return PathType.FENCE;
        }
        if (!state.isPathfindable(PathComputationType.LAND)) {
            return PathType.BLOCKED;
        }
        PathType walkableFluid = fluid.getBlockPathType(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, null, true);
        if (walkableFluid != null) {
            return walkableFluid;
        }
        return fluid.is(FluidTags.WATER) ? PathType.WATER : PathType.OPEN;
    }
}
