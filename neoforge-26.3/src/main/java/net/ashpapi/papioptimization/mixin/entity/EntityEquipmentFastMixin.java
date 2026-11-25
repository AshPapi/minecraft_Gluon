package net.ashpapi.papioptimization.mixin.entity;

import java.util.EnumMap;
import net.ashpapi.papioptimization.ai.PapiEquipmentTracker;
import net.ashpapi.papioptimization.core.PapiEnumCache;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityEquipment.class, priority = 1500)
public abstract class EntityEquipmentFastMixin implements PapiEquipmentTracker {

    @Shadow
    @Final
    private EnumMap<EquipmentSlot, ItemStack> items;

    @Unique
    private boolean papi$trackable;

    @Unique
    private boolean papi$dirtySync = true;

    @Unique
    private int papi$nonEmptyMask;

    @Unique
    private int papi$tickableEnchantMask;

    @Unique
    private ItemEnchantments @Nullable [] papi$seenEnchantments;

    @Inject(method = "<init>(Ljava/util/EnumMap;)V", at = @At("RETURN"))
    private void papi$initTracking(EnumMap<EquipmentSlot, ItemStack> map, CallbackInfo ci) {
        boolean exact = ((Object) this).getClass() == EntityEquipment.class;
        this.papi$trackable = exact;
        this.papi$dirtySync = true;
        if (exact) {
            this.papi$recomputeAllSlots();
        }
    }

    @Unique
    private void papi$recomputeAllSlots() {
        int nonEmpty = 0;
        int tickable = 0;
        ItemEnchantments[] seen = this.papi$seenEnchantments;
        for (int i = 0, len = PapiEnumCache.EQUIPMENT_SLOTS.length; i < len; i++) {
            EquipmentSlot slot = PapiEnumCache.EQUIPMENT_SLOTS[i];
            ItemStack stack = this.items.getOrDefault(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                int bit = 1 << i;
                nonEmpty |= bit;
                ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (!ench.isEmpty()) {
                    if (seen == null) {
                        seen = new ItemEnchantments[PapiEnumCache.EQUIPMENT_SLOTS.length];
                        this.papi$seenEnchantments = seen;
                    }
                    seen[i] = ench;
                    if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.TICK)) {
                        tickable |= bit;
                    }
                } else if (seen != null) {
                    seen[i] = ItemEnchantments.EMPTY;
                }
            } else if (seen != null) {
                seen[i] = null;
            }
        }
        this.papi$nonEmptyMask = nonEmpty;
        this.papi$tickableEnchantMask = tickable;
    }

    @Unique
    private void papi$updateSlot(EquipmentSlot slot, ItemStack stack) {
        int idx = slot.ordinal();
        int bit = 1 << idx;
        ItemEnchantments[] seen = this.papi$seenEnchantments;
        if (stack == null || stack.isEmpty()) {
            this.papi$nonEmptyMask &= ~bit;
            this.papi$tickableEnchantMask &= ~bit;
            if (seen != null) {
                seen[idx] = null;
            }
        } else {
            this.papi$nonEmptyMask |= bit;
            ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (!ench.isEmpty()) {
                if (seen == null) {
                    seen = new ItemEnchantments[PapiEnumCache.EQUIPMENT_SLOTS.length];
                    this.papi$seenEnchantments = seen;
                }
                seen[idx] = ench;
                if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.TICK)) {
                    this.papi$tickableEnchantMask |= bit;
                } else {
                    this.papi$tickableEnchantMask &= ~bit;
                }
            } else {
                this.papi$tickableEnchantMask &= ~bit;
                if (seen != null) {
                    seen[idx] = ItemEnchantments.EMPTY;
                }
            }
        }
        this.papi$dirtySync = true;
    }

    @Inject(method = "set", at = @At("RETURN"))
    private void papi$onSet(EquipmentSlot slot, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (this.papi$trackable) {
            this.papi$updateSlot(slot, stack);
        }
    }

    @Inject(method = "setAll", at = @At("RETURN"))
    private void papi$onSetAll(EntityEquipment other, CallbackInfo ci) {
        if (this.papi$trackable) {
            this.papi$dirtySync = true;
            this.papi$recomputeAllSlots();
        }
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void papi$onClear(CallbackInfo ci) {
        if (this.papi$trackable) {
            if (this.papi$nonEmptyMask != 0) {
                this.papi$dirtySync = true;
            }
            this.papi$nonEmptyMask = 0;
            this.papi$tickableEnchantMask = 0;
            ItemEnchantments[] seen = this.papi$seenEnchantments;
            if (seen != null) {
                for (int i = 0; i < seen.length; i++) {
                    seen[i] = null;
                }
            }
        }
    }

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void papi$fastIsEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (!this.papi$trackable) {
            return;
        }
        int mask = this.papi$nonEmptyMask;
        if (mask == 0) {
            cir.setReturnValue(true);
            return;
        }
        int m = mask;
        int liveMask = 0;
        while (m != 0) {
            int idx = Integer.numberOfTrailingZeros(m);
            int bit = 1 << idx;
            m &= m - 1;
            ItemStack stack = this.items.getOrDefault(PapiEnumCache.EQUIPMENT_SLOTS[idx], ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                liveMask |= bit;
            } else {
                this.papi$tickableEnchantMask &= ~bit;
                this.papi$dirtySync = true;
            }
        }
        this.papi$nonEmptyMask = liveMask;
        cir.setReturnValue(liveMask == 0);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$fastTick(Entity entity, CallbackInfo ci) {
        if (!this.papi$trackable) {
            return;
        }
        ci.cancel();
        int m = this.papi$nonEmptyMask;
        if (m == 0) {
            return;
        }
        var level = entity.level();
        while (m != 0) {
            int idx = Integer.numberOfTrailingZeros(m);
            int bit = 1 << idx;
            m &= m - 1;
            EquipmentSlot slot = PapiEnumCache.EQUIPMENT_SLOTS[idx];
            ItemStack stack = this.items.getOrDefault(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                stack.inventoryTick(level, entity, slot);
                if (stack.isEmpty()) {
                    this.papi$nonEmptyMask &= ~bit;
                    this.papi$tickableEnchantMask &= ~bit;
                    this.papi$dirtySync = true;
                }
            } else {
                this.papi$nonEmptyMask &= ~bit;
                this.papi$tickableEnchantMask &= ~bit;
                this.papi$dirtySync = true;
            }
        }
    }

    @Override
    public boolean papi$isTrackable() {
        return this.papi$trackable;
    }

    @Override
    public boolean papi$isDirtySync() {
        return this.papi$dirtySync;
    }

    @Override
    public void papi$clearDirtySync() {
        this.papi$dirtySync = false;
    }

    @Override
    public void papi$refreshNonEmptyMask() {
        if (this.papi$trackable) {
            this.papi$recomputeAllSlots();
        }
    }

    @Override
    public int papi$getNonEmptyMask() {
        return this.papi$nonEmptyMask;
    }

    @Override
    public boolean papi$hasTickableEnchantments() {
        int m = this.papi$nonEmptyMask;
        if (m == 0) {
            return false;
        }
        ItemEnchantments[] seen = this.papi$seenEnchantments;
        while (m != 0) {
            int idx = Integer.numberOfTrailingZeros(m);
            int bit = 1 << idx;
            m &= m - 1;
            ItemStack stack = this.items.getOrDefault(PapiEnumCache.EQUIPMENT_SLOTS[idx], ItemStack.EMPTY);
            if (stack.isEmpty()) {
                this.papi$nonEmptyMask &= ~bit;
                this.papi$tickableEnchantMask &= ~bit;
                this.papi$dirtySync = true;
                continue;
            }
            ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (ench.isEmpty()) {
                this.papi$tickableEnchantMask &= ~bit;
                if (seen != null) {
                    seen[idx] = ItemEnchantments.EMPTY;
                }
            } else {
                ItemEnchantments prev = seen != null ? seen[idx] : null;
                if (ench != prev) {
                    if (seen == null) {
                        seen = new ItemEnchantments[PapiEnumCache.EQUIPMENT_SLOTS.length];
                        this.papi$seenEnchantments = seen;
                    }
                    seen[idx] = ench;
                    if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.TICK)) {
                        this.papi$tickableEnchantMask |= bit;
                    } else {
                        this.papi$tickableEnchantMask &= ~bit;
                    }
                }
            }
        }
        return this.papi$tickableEnchantMask != 0;
    }
}
