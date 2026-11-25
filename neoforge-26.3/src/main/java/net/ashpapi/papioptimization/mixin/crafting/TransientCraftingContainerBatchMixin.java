package net.ashpapi.papioptimization.mixin.crafting;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.ashpapi.papioptimization.access.PapiBatchCraftingContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TransientCraftingContainer.class, priority = 1500)
public abstract class TransientCraftingContainerBatchMixin implements PapiBatchCraftingContainer {

    @Shadow
    @Final
    private AbstractContainerMenu menu;

    @Unique
    private int papi$batchDepth;

    @Unique
    private boolean papi$dirty;

    @Override
    public void papi$beginBatch() {
        this.papi$batchDepth++;
    }

    @Override
    public void papi$endBatchAndNotify() {
        if (this.papi$batchDepth > 0 && --this.papi$batchDepth == 0 && this.papi$dirty) {
            this.papi$dirty = false;
            this.menu.slotsChanged((TransientCraftingContainer) (Object) this);
        }
    }

    @WrapWithCondition(
            method = {"setItem", "removeItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;slotsChanged(Lnet/minecraft/world/Container;)V"
            )
    )
    private boolean papi$deferSlotsChangedInBatch(AbstractContainerMenu targetMenu, Container container) {
        if (this.papi$batchDepth > 0) {
            this.papi$dirty = true;
            return false;
        }
        return true;
    }
}
