package net.ashpapi.papioptimization.mixin.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.lang.ref.WeakReference;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.ashpapi.papioptimization.access.PapiLevelBlockEpoch;
import net.ashpapi.papioptimization.core.PapiHopperClassCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ContainerOrHandler;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HopperBlockEntity.class, priority = 1500)
public abstract class FastHopperMixin extends RandomizableContainerBlockEntity implements Hopper {

    @Unique
    private static final byte PAPI$MODE_NONE = 0;

    @Unique
    private static final byte PAPI$MODE_CONTAINER = 1;

    @Unique
    private static final byte PAPI$MODE_NO_BLOCK_INV = 2;

    @Unique
    private static final byte PAPI$MODE_COMPOSTER = 3;

    @Shadow
    private Direction facing;

    @Shadow
    private int cooldownTime;

    @Shadow
    private long tickedGameTime;

    @Shadow
    private static @Nullable Container getEntityContainer(Level level, double x, double y, double z) {
        return null;
    }

    @Shadow
    public static List<ItemEntity> getItemsAtAndAbove(Level level, Hopper hopper) {
        throw new AssertionError();
    }

    @Shadow
    public static boolean addItem(Container container, ItemEntity itemEntity) {
        return false;
    }

    protected FastHopperMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private @Nullable AABB papi$cachedSuckAabb;

    @Unique
    private double papi$cachedX = Double.NaN;

    @Unique
    private double papi$cachedY = Double.NaN;

    @Unique
    private double papi$cachedZ = Double.NaN;

    @Unique
    private byte papi$pushMode;

    @Unique
    private @Nullable WeakReference<BaseContainerBlockEntity> papi$pushPrimary;

    @Unique
    private @Nullable WeakReference<BaseContainerBlockEntity> papi$pushSecondary;

    @Unique
    private @Nullable Direction papi$lastPushDir;

    @Unique
    private long papi$lastPushPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPushSelfEpoch;

    @Unique
    private long papi$lastPushOtherEpoch;

    @Unique
    private long papi$lastPushBlockEpoch;

    @Unique
    private long papi$lastPushOtherBlockPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPushOtherBlockEpoch;

    @Unique
    private byte papi$pullMode;

    @Unique
    private @Nullable WeakReference<BaseContainerBlockEntity> papi$pullPrimary;

    @Unique
    private @Nullable WeakReference<BaseContainerBlockEntity> papi$pullSecondary;

    @Unique
    private boolean papi$pullReplaySetChanged1;

    @Unique
    private boolean papi$pullReplaySetChanged2;

    @Unique
    private boolean papi$lastPullFullBlock;

    @Unique
    private long papi$lastPullPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPullSelfEpoch;

    @Unique
    private long papi$lastPullOtherEpoch;

    @Unique
    private long papi$lastPullBlockEpoch;

    @Unique
    private long papi$lastPullOtherBlockPosLong = Long.MIN_VALUE;

    @Unique
    private long papi$lastPullOtherBlockEpoch;

    @Unique
    private transient @Nullable ContainerOrHandler papi$observedPushResult;

    @Unique
    private transient @Nullable BlockPos papi$observedPushPos;

    @Unique
    private transient @Nullable ContainerOrHandler papi$observedPullResult;

    @Unique
    private transient @Nullable BlockState papi$observedPullState;

    @Unique
    private transient @Nullable BlockPos papi$observedPullPos;

    @WrapMethod(method = "pushItemsTick")
    private static void papi$fastPushItemsTick(
            Level level,
            BlockPos pos,
            BlockState state,
            HopperBlockEntity hopper,
            Operation<Void> original
    ) {
        FastHopperMixin self = (FastHopperMixin) (Object) hopper;
        if (!state.getValueOrElse(HopperBlock.ENABLED, Boolean.TRUE)) {
            if (self.cooldownTime > 0) {
                self.cooldownTime--;
            } else {
                self.cooldownTime = 0;
            }
            self.tickedGameTime = level.getGameTime();
            return;
        }
        if (self.cooldownTime > 1) {
            self.cooldownTime--;
            self.tickedGameTime = level.getGameTime();
            return;
        }
        original.call(level, pos, state, hopper);
    }

    @WrapMethod(method = "getItemsAtAndAbove")
    private static List<ItemEntity> papi$fastGetItemsAtAndAbove(
            Level level,
            Hopper hopper,
            Operation<List<ItemEntity>> original
    ) {
        if (hopper instanceof HopperBlockEntity hbe) {
            FastHopperMixin accessor = (FastHopperMixin) (Object) hbe;
            double hx = hopper.getLevelX();
            double hy = hopper.getLevelY();
            double hz = hopper.getLevelZ();
            AABB box = accessor.papi$cachedSuckAabb;
            if (box == null || hx != accessor.papi$cachedX || hy != accessor.papi$cachedY || hz != accessor.papi$cachedZ) {
                box = hopper.getSuckAabb().move(hx - 0.5, hy - 0.5, hz - 0.5);
                accessor.papi$cachedSuckAabb = box;
                accessor.papi$cachedX = hx;
                accessor.papi$cachedY = hy;
                accessor.papi$cachedZ = hz;
            }
            return level.getEntitiesOfClass(ItemEntity.class, box, EntitySelector.ENTITY_STILL_ALIVE);
        }
        return original.call(level, hopper);
    }

    @WrapMethod(method = "ejectItems")
    private static boolean papi$epochEjectItems(
            Level level,
            BlockPos pos,
            HopperBlockEntity hopper,
            Operation<Boolean> original
    ) {
        FastHopperMixin self = (FastHopperMixin) (Object) hopper;
        Direction dir = self.facing;
        int tx = pos.getX() + dir.getStepX();
        int ty = pos.getY() + dir.getStepY();
        int tz = pos.getZ() + dir.getStepZ();
        long targetLong = BlockPos.asLong(tx, ty, tz);
        PapiLevelBlockEpoch blockEpochBus = (PapiLevelBlockEpoch) level;
        long curBlockEpoch = blockEpochBus.papi$getBlockEpoch(targetLong);

        byte mode = self.papi$pushMode;
        if (mode != PAPI$MODE_NONE && self.papi$lastPushPosLong == targetLong && curBlockEpoch == self.papi$lastPushBlockEpoch) {
            if (mode == PAPI$MODE_CONTAINER) {
                BaseContainerBlockEntity be1 = self.papi$pushPrimary != null ? self.papi$pushPrimary.get() : null;
                BaseContainerBlockEntity be2 = self.papi$pushSecondary != null ? self.papi$pushSecondary.get() : null;
                boolean expectedDouble = self.papi$pushSecondary != null;
                if (be1 != null
                        && (!expectedDouble || be2 != null)
                        && (self.papi$lastPushOtherBlockPosLong == Long.MIN_VALUE
                            || blockEpochBus.papi$getBlockEpoch(self.papi$lastPushOtherBlockPosLong) == self.papi$lastPushOtherBlockEpoch)
                        && papi$areValidCachedHalves(be1, be2, level, targetLong)) {
                    long selfEpoch = ((PapiEpochContainer) hopper).papi$getChangeEpoch();
                    long otherEpoch = papi$computeTargetEpoch(be1, be2);
                    if (otherEpoch >= 0L && selfEpoch == self.papi$lastPushSelfEpoch && otherEpoch == self.papi$lastPushOtherEpoch) {
                        return false;
                    }
                }
            } else if (mode == PAPI$MODE_COMPOSTER) {
                long selfEpoch = ((PapiEpochContainer) hopper).papi$getChangeEpoch();
                if (selfEpoch == self.papi$lastPushSelfEpoch) {
                    return false;
                }
            } else if (mode == PAPI$MODE_NO_BLOCK_INV && dir == self.papi$lastPushDir) {
                if (getEntityContainer(level, tx + 0.5, ty + 0.5, tz + 0.5) == null) {
                    return false;
                }
            }
        }

        self.papi$observedPushResult = null;
        self.papi$observedPushPos = null;
        boolean moved = original.call(level, pos, hopper);
        ContainerOrHandler observedResult = self.papi$observedPushResult;
        BlockPos observedPos = self.papi$observedPushPos;
        self.papi$observedPushResult = null;
        self.papi$observedPushPos = null;

        if (!moved && observedResult != null) {
            Container observed = observedResult.container();
            if (observed != null && papi$recordContainerCache(self, true, hopper, level, targetLong, curBlockEpoch, blockEpochBus, observed)) {
                return false;
            }
            if (observedPos != null) {
                BlockState targetState = level.getBlockState(observedPos);
                if (targetState.getBlock() instanceof ComposterBlock) {
                    self.papi$clearPushRefs();
                    self.papi$pushMode = PAPI$MODE_COMPOSTER;
                    self.papi$lastPushPosLong = targetLong;
                    self.papi$lastPushBlockEpoch = curBlockEpoch;
                    self.papi$lastPushSelfEpoch = ((PapiEpochContainer) hopper).papi$getChangeEpoch();
                    return false;
                }
                if (observedResult.isEmpty()
                        && !targetState.hasBlockEntity()
                        && !(targetState.getBlock() instanceof WorldlyContainerHolder)) {
                    self.papi$clearPushRefs();
                    self.papi$pushMode = PAPI$MODE_NO_BLOCK_INV;
                    self.papi$lastPushDir = dir;
                    self.papi$lastPushPosLong = targetLong;
                    self.papi$lastPushBlockEpoch = curBlockEpoch;
                    return false;
                }
            }
        }

        self.papi$clearPushRefs();
        self.papi$pushMode = PAPI$MODE_NONE;
        return moved;
    }

    @WrapOperation(
            method = "ejectItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getContainerOrHandlerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lnet/neoforged/neoforge/transfer/item/ContainerOrHandler;"
            )
    )
    private static ContainerOrHandler papi$capturePushContainer(
            Level level,
            BlockPos pos,
            Direction side,
            Operation<ContainerOrHandler> original,
            @Local(argsOnly = true) HopperBlockEntity hopper
    ) {
        ContainerOrHandler result = original.call(level, pos, side);
        if (hopper != null) {
            FastHopperMixin self = (FastHopperMixin) (Object) hopper;
            self.papi$observedPushResult = result;
            self.papi$observedPushPos = pos;
        }
        return result;
    }

    @WrapMethod(method = "suckInItems")
    private static boolean papi$epochSuckInItems(
            Level level,
            Hopper hopper,
            Operation<Boolean> original
    ) {
        if (!(hopper instanceof HopperBlockEntity hbe)) {
            return original.call(level, hopper);
        }
        FastHopperMixin self = (FastHopperMixin) (Object) hbe;
        double hx = hopper.getLevelX();
        double hy = hopper.getLevelY() + 1.0;
        double hz = hopper.getLevelZ();
        long aboveLong = BlockPos.asLong(Mth.floor(hx), Mth.floor(hy), Mth.floor(hz));
        PapiLevelBlockEpoch blockEpochBus = (PapiLevelBlockEpoch) level;
        long curBlockEpoch = blockEpochBus.papi$getBlockEpoch(aboveLong);

        byte mode = self.papi$pullMode;
        if (mode != PAPI$MODE_NONE && self.papi$lastPullPosLong == aboveLong && curBlockEpoch == self.papi$lastPullBlockEpoch) {
            if (mode == PAPI$MODE_CONTAINER) {
                BaseContainerBlockEntity be1 = self.papi$pullPrimary != null ? self.papi$pullPrimary.get() : null;
                BaseContainerBlockEntity be2 = self.papi$pullSecondary != null ? self.papi$pullSecondary.get() : null;
                boolean expectedDouble = self.papi$pullSecondary != null;
                if (be1 != null
                        && (!expectedDouble || be2 != null)
                        && (self.papi$lastPullOtherBlockPosLong == Long.MIN_VALUE
                            || blockEpochBus.papi$getBlockEpoch(self.papi$lastPullOtherBlockPosLong) == self.papi$lastPullOtherBlockEpoch)
                        && papi$areValidCachedHalves(be1, be2, level, aboveLong)) {
                    long selfEpoch = ((PapiEpochContainer) hbe).papi$getChangeEpoch();
                    long otherEpoch = papi$computeTargetEpoch(be1, be2);
                    if (otherEpoch >= 0L && selfEpoch == self.papi$lastPullSelfEpoch && otherEpoch == self.papi$lastPullOtherEpoch) {
                        if (self.papi$pullReplaySetChanged1) {
                            be1.setChanged();
                        }
                        if (self.papi$pullReplaySetChanged2 && be2 != null) {
                            be2.setChanged();
                        }
                        if (self.papi$pullReplaySetChanged1 || self.papi$pullReplaySetChanged2) {
                            self.papi$lastPullOtherEpoch = papi$computeTargetEpoch(be1, be2);
                        }
                        return false;
                    }
                }
            } else if (mode == PAPI$MODE_COMPOSTER) {
                long selfEpoch = ((PapiEpochContainer) hbe).papi$getChangeEpoch();
                if (selfEpoch == self.papi$lastPullSelfEpoch) {
                    return false;
                }
            } else if (mode == PAPI$MODE_NO_BLOCK_INV) {
                if (getEntityContainer(level, hx, hy, hz) == null) {
                    if (self.papi$lastPullFullBlock) {
                        return false;
                    }
                    List<ItemEntity> items = getItemsAtAndAbove(level, hopper);
                    for (int i = 0, len = items.size(); i < len; i++) {
                        if (addItem(hopper, items.get(i))) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }

        self.papi$observedPullResult = null;
        self.papi$observedPullState = null;
        self.papi$observedPullPos = null;
        boolean pulled = original.call(level, hopper);
        ContainerOrHandler observedResult = self.papi$observedPullResult;
        BlockState observedState = self.papi$observedPullState;
        BlockPos observedPos = self.papi$observedPullPos;
        self.papi$observedPullResult = null;
        self.papi$observedPullState = null;
        self.papi$observedPullPos = null;

        if (!pulled && observedResult != null) {
            Container observed = observedResult.container();
            if (observed != null && papi$recordContainerCache(self, false, hbe, level, aboveLong, curBlockEpoch, blockEpochBus, observed)) {
                return false;
            }
            if (observedState != null && observedPos != null) {
                if (observedState.getBlock() instanceof ComposterBlock) {
                    self.papi$clearPullRefs();
                    self.papi$pullMode = PAPI$MODE_COMPOSTER;
                    self.papi$lastPullPosLong = aboveLong;
                    self.papi$lastPullBlockEpoch = curBlockEpoch;
                    self.papi$lastPullSelfEpoch = ((PapiEpochContainer) hbe).papi$getChangeEpoch();
                    return false;
                }
                if (observedResult.isEmpty()
                        && !observedState.hasBlockEntity()
                        && !(observedState.getBlock() instanceof WorldlyContainerHolder)) {
                    self.papi$clearPullRefs();
                    self.papi$pullMode = PAPI$MODE_NO_BLOCK_INV;
                    self.papi$lastPullFullBlock = observedState.isCollisionShapeFullBlock(level, observedPos);
                    self.papi$lastPullPosLong = aboveLong;
                    self.papi$lastPullBlockEpoch = curBlockEpoch;
                    return false;
                }
            }
        }

        self.papi$clearPullRefs();
        self.papi$pullMode = PAPI$MODE_NONE;
        return pulled;
    }

    @WrapOperation(
            method = "suckInItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getSourceContainerOrHandler(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/neoforged/neoforge/transfer/item/ContainerOrHandler;"
            )
    )
    private static ContainerOrHandler papi$capturePullContainer(
            Level level,
            Hopper hopper,
            BlockPos pos,
            BlockState state,
            Operation<ContainerOrHandler> original
    ) {
        ContainerOrHandler result = original.call(level, hopper, pos, state);
        if (hopper instanceof HopperBlockEntity hbe) {
            FastHopperMixin self = (FastHopperMixin) (Object) hbe;
            self.papi$observedPullResult = result;
            self.papi$observedPullState = state;
            self.papi$observedPullPos = pos;
        }
        return result;
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void papi$onHopperSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        ((PapiEpochContainer) this).papi$incrementEpoch();
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void papi$onHopperRemoveItem(int slot, int count, CallbackInfoReturnable<ItemStack> cir) {
        ((PapiEpochContainer) this).papi$incrementEpoch();
    }

    @Inject(method = "setItems", at = @At("RETURN"))
    private void papi$onHopperSetItems(NonNullList<ItemStack> items, CallbackInfo ci) {
        ((PapiEpochContainer) this).papi$incrementEpoch();
    }

    @Unique
    private void papi$clearPushRefs() {
        this.papi$pushPrimary = null;
        this.papi$pushSecondary = null;
        this.papi$lastPushDir = null;
        this.papi$lastPushOtherBlockPosLong = Long.MIN_VALUE;
    }

    @Unique
    private void papi$clearPullRefs() {
        this.papi$pullPrimary = null;
        this.papi$pullSecondary = null;
        this.papi$pullReplaySetChanged1 = false;
        this.papi$pullReplaySetChanged2 = false;
        this.papi$lastPullOtherBlockPosLong = Long.MIN_VALUE;
    }

    @Unique
    private static boolean papi$recordContainerCache(
            FastHopperMixin self,
            boolean isPush,
            HopperBlockEntity hopper,
            Level level,
            long targetLong,
            long curBlockEpoch,
            PapiLevelBlockEpoch blockEpochBus,
            Container observed
    ) {
        BaseContainerBlockEntity be1 = null;
        BaseContainerBlockEntity be2 = null;
        if (observed instanceof BaseContainerBlockEntity singleBe) {
            be1 = singleBe;
        } else if (observed instanceof CompoundContainer cc) {
            CompoundContainerAccessor acc = (CompoundContainerAccessor) cc;
            if (acc.papi$getContainer1() instanceof BaseContainerBlockEntity c1
                    && acc.papi$getContainer2() instanceof BaseContainerBlockEntity c2) {
                be1 = c1;
                be2 = c2;
            }
        }
        if (be1 == null || !papi$areValidCachedHalves(be1, be2, level, targetLong)) {
            return false;
        }
        long otherEpoch = papi$computeTargetEpoch(be1, be2);
        if (otherEpoch < 0L) {
            return false;
        }
        long secondaryPos = Long.MIN_VALUE;
        if (be2 != null) {
            long p1 = be1.getBlockPos().asLong();
            long p2 = be2.getBlockPos().asLong();
            secondaryPos = (p1 == targetLong) ? p2 : p1;
        }

        if (isPush) {
            WeakReference<BaseContainerBlockEntity> prev1 = self.papi$pushPrimary;
            WeakReference<BaseContainerBlockEntity> prev2 = self.papi$pushSecondary;
            self.papi$pushPrimary = (prev1 != null && prev1.get() == be1) ? prev1 : new WeakReference<>(be1);
            self.papi$pushSecondary = be2 == null ? null : ((prev2 != null && prev2.get() == be2) ? prev2 : new WeakReference<>(be2));
            self.papi$pushMode = PAPI$MODE_CONTAINER;
            self.papi$lastPushPosLong = targetLong;
            self.papi$lastPushSelfEpoch = ((PapiEpochContainer) hopper).papi$getChangeEpoch();
            self.papi$lastPushOtherEpoch = otherEpoch;
            self.papi$lastPushBlockEpoch = curBlockEpoch;
            self.papi$lastPushOtherBlockPosLong = secondaryPos;
            self.papi$lastPushOtherBlockEpoch = secondaryPos != Long.MIN_VALUE ? blockEpochBus.papi$getBlockEpoch(secondaryPos) : 0L;
        } else {
            WeakReference<BaseContainerBlockEntity> prev1 = self.papi$pullPrimary;
            WeakReference<BaseContainerBlockEntity> prev2 = self.papi$pullSecondary;
            self.papi$pullPrimary = (prev1 != null && prev1.get() == be1) ? prev1 : new WeakReference<>(be1);
            self.papi$pullSecondary = be2 == null ? null : ((prev2 != null && prev2.get() == be2) ? prev2 : new WeakReference<>(be2));
            self.papi$pullReplaySetChanged1 = papi$hasSingleCountItem(be1);
            self.papi$pullReplaySetChanged2 = be2 != null && papi$hasSingleCountItem(be2);
            self.papi$pullMode = PAPI$MODE_CONTAINER;
            self.papi$lastPullPosLong = aboveLongOrTarget(targetLong);
            self.papi$lastPullSelfEpoch = ((PapiEpochContainer) hopper).papi$getChangeEpoch();
            self.papi$lastPullOtherEpoch = otherEpoch;
            self.papi$lastPullBlockEpoch = curBlockEpoch;
            self.papi$lastPullOtherBlockPosLong = secondaryPos;
            self.papi$lastPullOtherBlockEpoch = secondaryPos != Long.MIN_VALUE ? blockEpochBus.papi$getBlockEpoch(secondaryPos) : 0L;
        }
        return true;
    }

    @Unique
    private static long aboveLongOrTarget(long posLong) {
        return posLong;
    }

    @Unique
    private static boolean papi$hasSingleCountItem(BaseContainerBlockEntity be) {
        for (int i = 0, n = be.getContainerSize(); i < n; i++) {
            ItemStack stack = be.getItem(i);
            if (!stack.isEmpty() && stack.getCount() == 1) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static long papi$computeTargetEpoch(BaseContainerBlockEntity be1, @Nullable BaseContainerBlockEntity be2) {
        long e1 = ((PapiEpochContainer) be1).papi$getChangeEpoch();
        if (be2 == null) {
            return e1;
        }
        long e2 = ((PapiEpochContainer) be2).papi$getChangeEpoch();
        return (e1 * 31L + e2 * 1315423911L) & Long.MAX_VALUE;
    }

    @Unique
    private static boolean papi$areValidCachedHalves(
            BaseContainerBlockEntity be1,
            @Nullable BaseContainerBlockEntity be2,
            Level level,
            long posLong
    ) {
        if (!(be1 instanceof PapiEpochContainer) || !PapiHopperClassCache.isVanillaEpochContainer(be1.getClass())) {
            return false;
        }
        if (be1.isRemoved() || be1.getLevel() != level) {
            return false;
        }
        if (be2 == null) {
            if (be1.getBlockPos().asLong() != posLong) {
                return false;
            }
            if (be1 instanceof ChestBlockEntity) {
                return be1.getBlockState().getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) == ChestType.SINGLE;
            }
            return true;
        }
        if (!(be2 instanceof PapiEpochContainer) || !PapiHopperClassCache.isVanillaEpochContainer(be2.getClass())) {
            return false;
        }
        if (be2.isRemoved() || be2.getLevel() != level) {
            return false;
        }
        if (be1.getBlockPos().asLong() != posLong && be2.getBlockPos().asLong() != posLong) {
            return false;
        }
        ChestType t1 = be1.getBlockState().getValueOrElse(ChestBlock.TYPE, ChestType.LEFT);
        ChestType t2 = be2.getBlockState().getValueOrElse(ChestBlock.TYPE, ChestType.LEFT);
        return t1 != ChestType.SINGLE && t2 != ChestType.SINGLE;
    }
}
