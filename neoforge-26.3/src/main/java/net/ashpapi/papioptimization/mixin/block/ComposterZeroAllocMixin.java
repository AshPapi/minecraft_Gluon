package net.ashpapi.papioptimization.mixin.block;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$InputContainer", priority = 1500)
public abstract class ComposterZeroAllocMixin {

    @Unique
    private static final int[] PAPI$ZERO_SLOTS = new int[0];

    @Unique
    private static final int[] PAPI$SINGLE_SLOT = new int[]{0};

    @Shadow
    private boolean changed;

    @WrapMethod(method = "getSlotsForFace")
    private int[] papi$inputSlotsForFace(Direction side, Operation<int[]> original) {
        return side == Direction.UP ? PAPI$SINGLE_SLOT : PAPI$ZERO_SLOTS;
    }

    @Inject(
            method = "setChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/ComposterBlock$InputContainer;removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private void papi$resetChangedBeforeClear(CallbackInfo ci) {
        this.changed = false;
    }
}

@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$OutputContainer", priority = 1500)
abstract class ComposterOutputZeroAllocMixin {

    @Unique
    private static final int[] PAPI$ZERO_SLOTS = new int[0];

    @Unique
    private static final int[] PAPI$SINGLE_SLOT = new int[]{0};

    @WrapMethod(method = "getSlotsForFace")
    private int[] papi$outputSlotsForFace(Direction side, Operation<int[]> original) {
        return side == Direction.DOWN ? PAPI$SINGLE_SLOT : PAPI$ZERO_SLOTS;
    }
}

@Mixin(targets = "net.minecraft.world.level.block.ComposterBlock$EmptyContainer", priority = 1500)
abstract class ComposterEmptyZeroAllocMixin {

    @Unique
    private static final int[] PAPI$ZERO_SLOTS = new int[0];

    @WrapMethod(method = "getSlotsForFace")
    private int[] papi$emptySlotsForFace(Direction side, Operation<int[]> original) {
        return PAPI$ZERO_SLOTS;
    }
}
