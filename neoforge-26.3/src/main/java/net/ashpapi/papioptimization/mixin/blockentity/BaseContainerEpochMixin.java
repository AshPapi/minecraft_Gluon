package net.ashpapi.papioptimization.mixin.blockentity;

import java.lang.ref.WeakReference;
import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaseContainerBlockEntity.class, priority = 1500)
public abstract class BaseContainerEpochMixin implements PapiEpochContainer {

    @Shadow
    protected abstract NonNullList<ItemStack> getItems();

    @Unique
    private long papi$changeEpoch = 1L;

    @Unique
    private @Nullable NonNullList<ItemStack> papi$lastItemsRef;

    @Unique
    private long papi$compEpoch = -1L;

    @Unique
    private int papi$compMaxStack = -1;

    @Unique
    private int papi$compSignal = -1;

    @Unique
    private @Nullable WeakReference<Object> papi$pairPartner;

    @Unique
    private long papi$pairEpoch = -1L;

    @Unique
    private int papi$pairMaxStack = -1;

    @Unique
    private int papi$pairSignal = -1;

    @Override
    public long papi$getChangeEpoch() {
        NonNullList<ItemStack> cur = this.getItems();
        if (cur != this.papi$lastItemsRef) {
            this.papi$lastItemsRef = cur;
            this.papi$changeEpoch++;
        }
        return this.papi$changeEpoch;
    }

    @Override
    public void papi$incrementEpoch() {
        this.papi$changeEpoch++;
    }

    @Override
    public int papi$getCachedRedstoneSignal(long epoch, int maxStackSize) {
        if (this.papi$compEpoch == epoch && this.papi$compMaxStack == maxStackSize) {
            return this.papi$compSignal;
        }
        return -1;
    }

    @Override
    public void papi$setCachedRedstoneSignal(long epoch, int maxStackSize, int signal) {
        this.papi$compEpoch = epoch;
        this.papi$compMaxStack = maxStackSize;
        this.papi$compSignal = signal;
    }

    @Override
    public int papi$getPairedRedstoneSignal(Object partner, long epoch, int maxStackSize) {
        WeakReference<Object> ref = this.papi$pairPartner;
        if (ref != null
                && ref.get() == partner
                && this.papi$pairEpoch == epoch
                && this.papi$pairMaxStack == maxStackSize) {
            return this.papi$pairSignal;
        }
        return -1;
    }

    @Override
    public void papi$setPairedRedstoneSignal(Object partner, long epoch, int maxStackSize, int signal) {
        WeakReference<Object> ref = this.papi$pairPartner;
        if (ref == null || ref.get() != partner) {
            this.papi$pairPartner = new WeakReference<>(partner);
        }
        this.papi$pairEpoch = epoch;
        this.papi$pairMaxStack = maxStackSize;
        this.papi$pairSignal = signal;
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("RETURN"))
    private void papi$onSetItem(int slot, ItemStack stack, boolean isSetChanged, CallbackInfo ci) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "removeItem", at = @At("RETURN"))
    private void papi$onRemoveItem(int slot, int count, CallbackInfoReturnable<ItemStack> cir) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "removeItemNoUpdate", at = @At("RETURN"))
    private void papi$onRemoveItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "clearContent", at = @At("RETURN"))
    private void papi$onClearContent(CallbackInfo ci) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void papi$onLoadAdditional(ValueInput input, CallbackInfo ci) {
        this.papi$changeEpoch++;
    }

    @Inject(method = "applyImplicitComponents", at = @At("RETURN"))
    private void papi$onApplyImplicitComponents(DataComponentGetter componentGetter, CallbackInfo ci) {
        this.papi$changeEpoch++;
    }
}
