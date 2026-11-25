package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExperienceOrb.class, priority = 1500)
public abstract class ExperienceOrbFastMixin extends Entity {

    @Shadow
    private int count;

    @Shadow
    private int age;

    @Shadow
    public abstract int getValue();

    @Shadow
    public abstract void setValue(int value);

    @Shadow
    private int repairPlayerItems(ServerPlayer player, int amount) {
        throw new AssertionError();
    }

    @Unique
    private static final long PAPI$MAX_TOTAL_XP = 1_000_000_000L;

    @Unique
    private static final EntityTypeTest<Entity, ExperienceOrb> PAPI$ORB_TEST =
            EntityTypeTest.forClass(ExperienceOrb.class);

    @Unique
    private static final Predicate<ExperienceOrb> PAPI$ALIVE_ORB = orb -> !orb.isRemoved();

    @Unique
    private @Nullable Int2IntOpenHashMap papi$clumpedValues;

    @Unique
    private long papi$totalXp;

    public ExperienceOrbFastMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private long papi$getTotalXp() {
        Int2IntOpenHashMap map = this.papi$clumpedValues;
        if (map == null || map.isEmpty()) {
            return (long) Math.max(0, this.getValue()) * (long) Math.max(0, this.count);
        }
        return this.papi$totalXp;
    }

    @Unique
    private void papi$ensureClumpMap() {
        if (this.papi$clumpedValues == null) {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap(4);
            int curVal = this.getValue();
            long sum = 0L;
            if (curVal > 0 && this.count > 0) {
                map.put(curVal, this.count);
                sum = (long) curVal * (long) this.count;
            }
            this.papi$clumpedValues = map;
            this.papi$totalXp = sum;
        }
    }

    @Unique
    private void papi$addClumpedEntry(int val, int addCount) {
        if (val <= 0 || addCount <= 0) {
            return;
        }
        Int2IntOpenHashMap map = this.papi$clumpedValues;
        int prev = map.get(val);
        long next = Math.min(Integer.MAX_VALUE, (long) prev + (long) addCount);
        int deltaCount = (int) next - prev;
        if (deltaCount > 0) {
            map.put(val, (int) next);
            long addXp = (long) val * (long) deltaCount;
            long maxSafe = Long.MAX_VALUE - addXp;
            this.papi$totalXp = this.papi$totalXp > maxSafe ? Long.MAX_VALUE : (this.papi$totalXp + addXp);
        }
    }

    @Unique
    private void papi$syncClumpedSyncedState() {
        if (this.papi$totalXp <= 0L) {
            this.count = 0;
            this.papi$clumpedValues = null;
            this.discard();
            return;
        }
        this.count = 1;
        int synced = (int) Math.min(Integer.MAX_VALUE, this.papi$totalXp);
        if (this.getValue() != synced) {
            this.setValue(synced);
        }
    }

    @Unique
    private static void papi$awardTotalXp(Player player, long totalXp) {
        long remaining = totalXp;
        while (remaining > 0L) {
            int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
            player.giveExperiencePoints(chunk);
            remaining -= chunk;
        }
    }

    @Unique
    private static boolean papi$hasDamagedMendingItem(ServerPlayer player) {
        return EnchantmentHelper.getRandomItemWith(
                EnchantmentEffectComponents.REPAIR_WITH_XP,
                player,
                ItemStack::isDamaged
        ).isPresent();
    }

    @Unique
    private static boolean papi$canAnyDamagedItemRepairWithVal(ServerPlayer player, int val) {
        ServerLevel level = player.level();
        List<EquipmentSlot> slots = EquipmentSlot.VALUES;
        for (int i = 0, len = slots.size(); i < len; i++) {
            ItemStack stack = player.getItemBySlot(slots.get(i));
            if (stack.isEmpty() || !stack.isDamaged()) {
                continue;
            }
            if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP)) {
                continue;
            }
            int baseRepair = (int) ((float) val * stack.getXpRepairRatio());
            if (baseRepair > 0 && EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, baseRepair) > 0) {
                return true;
            }
        }
        return false;
    }

    @WrapMethod(method = "tryMergeToExisting")
    private static boolean papi$tryMergeDifferentValues(
            ServerLevel level,
            Vec3 pos,
            int value,
            Operation<Boolean> original
    ) {
        if (value <= 0) {
            return original.call(level, pos, value);
        }
        AABB box = AABB.ofSize(pos, 1.0, 1.0, 1.0);
        List<ExperienceOrb> list = level.getEntities(PAPI$ORB_TEST, box, PAPI$ALIVE_ORB);
        for (int i = 0, len = list.size(); i < len; i++) {
            ExperienceOrb orb = list.get(i);
            ExperienceOrbFastMixin m = (ExperienceOrbFastMixin) (Object) orb;
            if (m.count <= 0) {
                continue;
            }
            if (m.papi$getTotalXp() + (long) value > PAPI$MAX_TOTAL_XP) {
                continue;
            }
            m.papi$ensureClumpMap();
            m.papi$addClumpedEntry(value, 1);
            m.papi$syncClumpedSyncedState();
            m.age = 0;
            return true;
        }
        return false;
    }

    @Inject(method = "scanForMerges", at = @At("HEAD"), cancellable = true)
    private void papi$scanForMergesFast(CallbackInfo ci) {
        if (this.isRemoved() || this.count <= 0) {
            ci.cancel();
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        AABB searchBox = this.getBoundingBox().inflate(1.25D);
        List<ExperienceOrb> nearbyOrbs = serverLevel.getEntities(PAPI$ORB_TEST, searchBox, PAPI$ALIVE_ORB);
        int orbCount = nearbyOrbs.size();
        if (orbCount <= 1) {
            ci.cancel();
            return;
        }

        long selfTotal = this.papi$getTotalXp();
        boolean mergedAny = false;

        for (int i = 0; i < orbCount; i++) {
            ExperienceOrb other = nearbyOrbs.get(i);
            if (other == (Object) this) {
                continue;
            }
            ExperienceOrbFastMixin otherMixin = (ExperienceOrbFastMixin) (Object) other;
            if (otherMixin.isRemoved() || otherMixin.count <= 0) {
                continue;
            }

            long otherTotal = otherMixin.papi$getTotalXp();
            if (otherTotal <= 0L || selfTotal + otherTotal > PAPI$MAX_TOTAL_XP) {
                continue;
            }

            this.papi$ensureClumpMap();
            Int2IntOpenHashMap otherMap = otherMixin.papi$clumpedValues;
            if (otherMap != null && !otherMap.isEmpty()) {
                ObjectIterator<Int2IntMap.Entry> it = Int2IntMaps.fastIterator(otherMap);
                while (it.hasNext()) {
                    Int2IntMap.Entry e = it.next();
                    this.papi$addClumpedEntry(e.getIntKey(), e.getIntValue());
                }
                otherMap.clear();
                otherMixin.papi$totalXp = 0L;
            } else {
                this.papi$addClumpedEntry(other.getValue(), otherMixin.count);
            }

            selfTotal = this.papi$totalXp;
            this.age = Math.min(this.age, otherMixin.age);
            otherMixin.count = 0;
            other.discard();
            mergedAny = true;
        }

        if (mergedAny) {
            this.papi$syncClumpedSyncedState();
        }

        ci.cancel();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void papi$saveClumpedValues(ValueOutput output, CallbackInfo ci) {
        Int2IntOpenHashMap map = this.papi$clumpedValues;
        if (map != null && !map.isEmpty()) {
            int[] packed = new int[map.size() * 2];
            int idx = 0;
            ObjectIterator<Int2IntMap.Entry> it = Int2IntMaps.fastIterator(map);
            while (it.hasNext()) {
                Int2IntMap.Entry e = it.next();
                packed[idx++] = e.getIntKey();
                packed[idx++] = e.getIntValue();
            }
            output.putIntArray("GluonOrbValues", packed);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void papi$loadClumpedValues(ValueInput input, CallbackInfo ci) {
        this.papi$clumpedValues = null;
        this.papi$totalXp = 0L;
        int[] packed = input.getIntArray("GluonOrbValues")
                .orElseGet(() -> input.getIntArray("PapiClumps").orElse(null));
        if (packed != null && packed.length >= 2 && (packed.length & 1) == 0) {
            Int2IntOpenHashMap map = new Int2IntOpenHashMap(packed.length >> 1);
            long total = 0L;
            for (int i = 0; i < packed.length; i += 2) {
                int val = packed[i];
                int cnt = packed[i + 1];
                if (val > 0 && cnt > 0) {
                    int prev = map.get(val);
                    long next = Math.min(Integer.MAX_VALUE, (long) prev + (long) cnt);
                    int added = (int) next - prev;
                    if (added > 0) {
                        map.put(val, (int) next);
                        long addXp = (long) val * (long) added;
                        total = total > Long.MAX_VALUE - addXp ? Long.MAX_VALUE : (total + addXp);
                    }
                }
            }
            if (!map.isEmpty() && total > 0L) {
                this.papi$clumpedValues = map;
                this.papi$totalXp = total;
                this.papi$syncClumpedSyncedState();
                return;
            }
        }
        if (this.count > 1) {
            int curVal = this.getValue();
            if (curVal > 0) {
                this.papi$ensureClumpMap();
                this.papi$syncClumpedSyncedState();
            } else {
                this.count = 0;
                this.discard();
            }
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void papi$fastPlayerTouch(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (this.isRemoved() || this.count <= 0) {
            if (!this.isRemoved()) {
                this.discard();
            }
            ci.cancel();
            return;
        }

        int preEventVal = this.getValue();
        IEventBus eventBus = NeoForge.EVENT_BUS;
        PlayerXpEvent.PickupXp event = new PlayerXpEvent.PickupXp(player, (ExperienceOrb) (Object) this);
        if (eventBus.post(event).isCanceled()) {
            ci.cancel();
            return;
        }
        if (this.isRemoved() || this.count <= 0) {
            if (!this.isRemoved()) {
                this.discard();
            }
            ci.cancel();
            return;
        }

        int orbValue = this.getValue();
        int totalCount = this.count;
        Int2IntOpenHashMap clumped = this.papi$clumpedValues;
        long clumpedTotal = this.papi$totalXp;

        this.count = 0;
        this.papi$clumpedValues = null;
        this.papi$totalXp = 0L;
        this.discard();

        player.take((ExperienceOrb) (Object) this, 1);
        player.takeXpDelay = 0;

        long pendingPlayerXp = 0L;

        if (clumped == null || clumped.isEmpty()) {
            boolean allRepaired = !papi$hasDamagedMendingItem(serverPlayer);
            int maxNoRepairVal = 0;
            if (orbValue > 0) {
                pendingPlayerXp += this.papi$applyOrbBatch(serverPlayer, orbValue, 1, allRepaired, maxNoRepairVal);
                allRepaired = allRepaired || !papi$hasDamagedMendingItem(serverPlayer);
            }
            if (totalCount > 1 && preEventVal > 0) {
                pendingPlayerXp += this.papi$applyOrbBatch(serverPlayer, preEventVal, totalCount - 1, allRepaired, maxNoRepairVal);
            }
        } else {
            long baseExpected = clumpedTotal > 0L ? clumpedTotal : preEventVal;
            int syncedExpected = (int) Math.min(Integer.MAX_VALUE, baseExpected);
            long remainingBudget;
            long bonusXp = 0L;

            if (orbValue <= 0) {
                remainingBudget = baseExpected > syncedExpected ? (baseExpected - syncedExpected) : 0L;
            } else if (orbValue < syncedExpected) {
                remainingBudget = (baseExpected - syncedExpected) + orbValue;
            } else {
                remainingBudget = baseExpected;
                if (orbValue > syncedExpected) {
                    bonusXp = (long) orbValue - (long) syncedExpected;
                }
            }

            boolean allRepaired = (remainingBudget <= 0L && bonusXp <= 0L) || !papi$hasDamagedMendingItem(serverPlayer);
            int maxNoRepairVal = 0;
            int consecutiveNoRepair = 0;

            if (remainingBudget > 0L) {
                ObjectIterator<Int2IntMap.Entry> it = Int2IntMaps.fastIterator(clumped);
                while (it.hasNext() && remainingBudget > 0L) {
                    Int2IntMap.Entry entry = it.next();
                    int val = entry.getIntKey();
                    int cnt = entry.getIntValue();
                    if (val <= 0 || cnt <= 0) {
                        continue;
                    }
                    long bucketTotal = (long) val * (long) cnt;
                    if (allRepaired || val <= maxNoRepairVal) {
                        long take = Math.min(bucketTotal, remainingBudget);
                        pendingPlayerXp += take;
                        remainingBudget -= take;
                        continue;
                    }
                    for (int i = 0; i < cnt && remainingBudget > 0L; i++) {
                        int feed = (int) Math.min((long) val, remainingBudget);
                        remainingBudget -= feed;
                        int remainingXp = this.repairPlayerItems(serverPlayer, feed);
                        if (remainingXp == feed) {
                            if (!papi$hasDamagedMendingItem(serverPlayer)) {
                                allRepaired = true;
                                pendingPlayerXp += remainingXp;
                                long rest = Math.min((long) (cnt - i - 1) * (long) val, remainingBudget);
                                pendingPlayerXp += rest;
                                remainingBudget -= rest;
                                break;
                            }
                            pendingPlayerXp += remainingXp;
                            if (++consecutiveNoRepair >= 8) {
                                consecutiveNoRepair = 0;
                                if (!papi$canAnyDamagedItemRepairWithVal(serverPlayer, feed)) {
                                    maxNoRepairVal = Math.max(maxNoRepairVal, feed);
                                    long rest = Math.min((long) (cnt - i - 1) * (long) val, remainingBudget);
                                    pendingPlayerXp += rest;
                                    remainingBudget -= rest;
                                    break;
                                }
                            }
                        } else {
                            consecutiveNoRepair = 0;
                            if (remainingXp > 0) {
                                pendingPlayerXp += remainingXp;
                            }
                        }
                    }
                }
            }

            if (bonusXp > 0L) {
                if (allRepaired) {
                    pendingPlayerXp += bonusXp;
                } else {
                    long remBonus = bonusXp;
                    while (remBonus > 0L) {
                        int chunk = (int) Math.min(Integer.MAX_VALUE, remBonus);
                        remBonus -= chunk;
                        int remAfterRepair = this.repairPlayerItems(serverPlayer, chunk);
                        if (remAfterRepair > 0) {
                            pendingPlayerXp += remAfterRepair;
                        }
                        if (remBonus > 0L && !papi$hasDamagedMendingItem(serverPlayer)) {
                            pendingPlayerXp += remBonus;
                            break;
                        }
                    }
                }
            }
        }

        if (pendingPlayerXp > 0L) {
            papi$awardTotalXp(serverPlayer, pendingPlayerXp);
        }

        ci.cancel();
    }

    @Unique
    private long papi$applyOrbBatch(
            ServerPlayer serverPlayer,
            int val,
            int cnt,
            boolean alreadyRepaired,
            int maxNoRepairVal
    ) {
        if (val <= 0 || cnt <= 0) {
            return 0L;
        }
        if (alreadyRepaired || val <= maxNoRepairVal) {
            return (long) val * (long) cnt;
        }
        long pending = 0L;
        int consecutiveNoRepair = 0;
        for (int i = 0; i < cnt; i++) {
            int remainingXp = this.repairPlayerItems(serverPlayer, val);
            if (remainingXp == val) {
                if (!papi$hasDamagedMendingItem(serverPlayer)) {
                    pending += (long) (cnt - i) * (long) val;
                    break;
                }
                pending += remainingXp;
                if (++consecutiveNoRepair >= 8) {
                    consecutiveNoRepair = 0;
                    if (!papi$canAnyDamagedItemRepairWithVal(serverPlayer, val)) {
                        pending += (long) (cnt - i - 1) * (long) val;
                        break;
                    }
                }
            } else {
                consecutiveNoRepair = 0;
                if (remainingXp > 0) {
                    pending += remainingXp;
                }
            }
        }
        return pending;
    }
}
