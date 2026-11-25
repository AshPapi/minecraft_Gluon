package net.ashpapi.papioptimization.mixin.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.ashpapi.papioptimization.core.PapiRecipeEpoch;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractFurnaceBlockEntity.class, priority = 1500)
public abstract class FastFurnaceSleepMixin {

    @Shadow
    protected NonNullList<ItemStack> items;

    @Shadow
    private int litTimeRemaining;

    @Shadow
    private int cookingTimer;

    @Unique
    private @Nullable RecipeHolder<? extends AbstractCookingRecipe> papi$curRecipe;

    @Unique
    private Optional<RecipeHolder<? extends AbstractCookingRecipe>> papi$curRecipeOptional = Optional.empty();

    @Unique
    private long papi$cachedEpoch = -1L;

    @Unique
    private boolean papi$inputFailed;

    @Unique
    private @Nullable ItemStack papi$failedInputSnapshot;

    @Unique
    private int papi$failedInputCount;

    @Unique
    private boolean papi$outputBlocked;

    @Unique
    private @Nullable ItemStack papi$blockedInputSnapshot;

    @Unique
    private int papi$blockedInputCount;

    @Unique
    private @Nullable ItemStack papi$blockedOutputSnapshot;

    @Unique
    private int papi$blockedOutputCount;

    @Unique
    private boolean papi$fuelInvalid;

    @Unique
    private @Nullable ItemStack papi$invalidFuelSnapshot;

    @Unique
    private int papi$invalidFuelCount;

    @Unique
    private boolean papi$burnAllowedThisTick;

    @Unique
    private static long papi$currentCombinedEpoch() {
        return ((long) PapiRecipeEpoch.current() << 32) | (PapiTagEpoch.current() & 0xFFFFFFFFL);
    }

    @Unique
    private void papi$syncEpoch(long epoch) {
        if (this.papi$cachedEpoch != epoch) {
            this.papi$cachedEpoch = epoch;
            this.papi$curRecipe = null;
            this.papi$curRecipeOptional = Optional.empty();
            this.papi$clearFailedInput();
            this.papi$clearBlockedOutput();
            this.papi$clearInvalidFuel();
        }
    }

    @Unique
    private void papi$clearFailedInput() {
        this.papi$inputFailed = false;
        this.papi$failedInputSnapshot = null;
        this.papi$failedInputCount = 0;
    }

    @Unique
    private void papi$clearBlockedOutput() {
        this.papi$outputBlocked = false;
        this.papi$blockedInputSnapshot = null;
        this.papi$blockedInputCount = 0;
        this.papi$blockedOutputSnapshot = null;
        this.papi$blockedOutputCount = 0;
    }

    @Unique
    private void papi$clearInvalidFuel() {
        this.papi$fuelInvalid = false;
        this.papi$invalidFuelSnapshot = null;
        this.papi$invalidFuelCount = 0;
    }

    @Unique
    private boolean papi$matchesFailedInput(ItemStack input) {
        ItemStack snap = this.papi$failedInputSnapshot;
        return snap != null
                && this.papi$failedInputCount == input.getCount()
                && ItemStack.isSameItemSameComponents(snap, input);
    }

    @Unique
    private boolean papi$matchesBlockedInput(ItemStack input) {
        if (input.isEmpty()) {
            return this.papi$blockedInputSnapshot == null && this.papi$blockedInputCount == 0;
        }
        ItemStack snap = this.papi$blockedInputSnapshot;
        return snap != null
                && this.papi$blockedInputCount == input.getCount()
                && ItemStack.isSameItemSameComponents(snap, input);
    }

    @Unique
    private boolean papi$matchesBlockedOutput(ItemStack output) {
        if (output.isEmpty()) {
            return this.papi$blockedOutputSnapshot == null && this.papi$blockedOutputCount == 0;
        }
        ItemStack snap = this.papi$blockedOutputSnapshot;
        return snap != null
                && this.papi$blockedOutputCount == output.getCount()
                && ItemStack.isSameItemSameComponents(snap, output);
    }

    @Unique
    private boolean papi$matchesInvalidFuel(ItemStack fuel) {
        ItemStack snap = this.papi$invalidFuelSnapshot;
        return snap != null
                && this.papi$invalidFuelCount == fuel.getCount()
                && ItemStack.isSameItemSameComponents(snap, fuel);
    }

    @WrapMethod(method = "serverTick")
    private static void papi$sleepIdleFurnace(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            AbstractFurnaceBlockEntity furnace,
            Operation<Void> original
    ) {
        FastFurnaceSleepMixin access = (FastFurnaceSleepMixin) (Object) furnace;

        if (access.litTimeRemaining <= 0 && access.cookingTimer <= 0) {
            ItemStack input = access.items.get(0);
            ItemStack fuel = access.items.get(1);

            if (input.isEmpty()) {
                if (access.papi$inputFailed) {
                    access.papi$clearFailedInput();
                }
                if (access.papi$outputBlocked) {
                    access.papi$clearBlockedOutput();
                }
                return;
            }
            if (fuel.isEmpty()) {
                if (access.papi$fuelInvalid) {
                    access.papi$clearInvalidFuel();
                }
                return;
            }

            access.papi$syncEpoch(papi$currentCombinedEpoch());

            if (access.papi$inputFailed) {
                if (access.papi$matchesFailedInput(input)) {
                    return;
                }
                access.papi$clearFailedInput();
            }

            if (access.papi$outputBlocked) {
                if (access.papi$matchesBlockedInput(input) && access.papi$matchesBlockedOutput(access.items.get(2))) {
                    return;
                }
                access.papi$clearBlockedOutput();
            }

            if (access.papi$fuelInvalid) {
                if (access.papi$matchesInvalidFuel(fuel)) {
                    return;
                }
                access.papi$clearInvalidFuel();
            }

            access.papi$burnAllowedThisTick = false;
            original.call(level, pos, state, furnace);

            if (access.papi$burnAllowedThisTick && access.litTimeRemaining <= 0 && access.cookingTimer <= 0) {
                ItemStack postFuel = access.items.get(1);
                if (!postFuel.isEmpty()) {
                    access.papi$fuelInvalid = true;
                    access.papi$invalidFuelSnapshot = postFuel.copyWithCount(1);
                    access.papi$invalidFuelCount = postFuel.getCount();
                }
            }
            return;
        }

        access.papi$burnAllowedThisTick = false;
        original.call(level, pos, state, furnace);
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(
            method = {
                "serverTick",
                "getTotalCookTime(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)I"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Optional;"
            )
    )
    private static Optional<RecipeHolder<? extends AbstractCookingRecipe>> papi$cachedFurnaceRecipeLookup(
            RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> check,
            RecipeInput recipeInput,
            ServerLevel level,
            Operation<Optional<RecipeHolder<? extends AbstractCookingRecipe>>> original,
            @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace
    ) {
        if (furnace == null || !(recipeInput instanceof SingleRecipeInput single)) {
            return original.call(check, recipeInput, level);
        }
        ItemStack input = single.item();
        if (input.isEmpty()) {
            return Optional.empty();
        }
        FastFurnaceSleepMixin access = (FastFurnaceSleepMixin) (Object) furnace;
        access.papi$syncEpoch(papi$currentCombinedEpoch());

        if (access.papi$inputFailed) {
            if (access.papi$matchesFailedInput(input)) {
                return Optional.empty();
            }
            access.papi$clearFailedInput();
        }

        RecipeHolder<? extends AbstractCookingRecipe> cached = access.papi$curRecipe;
        if (cached != null && ((RecipeHolder<AbstractCookingRecipe>) cached).value().matches(single, level)) {
            return access.papi$curRecipeOptional;
        }

        Optional<RecipeHolder<? extends AbstractCookingRecipe>> result = original.call(check, recipeInput, level);
        if (result.isPresent()) {
            access.papi$curRecipe = result.get();
            access.papi$curRecipeOptional = result;
            access.papi$clearFailedInput();
        } else {
            access.papi$curRecipe = null;
            access.papi$curRecipeOptional = Optional.empty();
            access.papi$inputFailed = true;
            access.papi$failedInputSnapshot = input.copyWithCount(1);
            access.papi$failedInputCount = input.getCount();
            access.papi$clearBlockedOutput();
        }
        return result;
    }

    @WrapOperation(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;canBurn(Lnet/minecraft/core/NonNullList;ILnet/minecraft/world/item/ItemStack;)Z"
            )
    )
    private static boolean papi$trackBlockedFurnaceOutput(
            NonNullList<ItemStack> items,
            int maxStackSize,
            ItemStack assembled,
            Operation<Boolean> original,
            @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace
    ) {
        boolean canBurn = original.call(items, maxStackSize, assembled);
        if (furnace != null) {
            FastFurnaceSleepMixin access = (FastFurnaceSleepMixin) (Object) furnace;
            if (canBurn) {
                access.papi$burnAllowedThisTick = true;
                if (access.papi$outputBlocked) {
                    access.papi$clearBlockedOutput();
                }
            } else {
                ItemStack in = items.get(0);
                ItemStack out = items.get(2);
                if (!access.papi$outputBlocked
                        || !access.papi$matchesBlockedInput(in)
                        || !access.papi$matchesBlockedOutput(out)) {
                    access.papi$outputBlocked = true;
                    if (in.isEmpty()) {
                        access.papi$blockedInputSnapshot = null;
                        access.papi$blockedInputCount = 0;
                    } else {
                        access.papi$blockedInputSnapshot = in.copyWithCount(1);
                        access.papi$blockedInputCount = in.getCount();
                    }
                    if (out.isEmpty()) {
                        access.papi$blockedOutputSnapshot = null;
                        access.papi$blockedOutputCount = 0;
                    } else {
                        access.papi$blockedOutputSnapshot = out.copyWithCount(1);
                        access.papi$blockedOutputCount = out.getCount();
                    }
                }
            }
        }
        return canBurn;
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"))
    private void papi$onFurnaceSetItemHead(int slot, ItemStack stack, boolean isSetChanged, CallbackInfo ci) {
        if (slot == 0) {
            this.papi$clearFailedInput();
            this.papi$clearBlockedOutput();
        } else if (slot == 1) {
            this.papi$clearInvalidFuel();
        } else if (slot == 2) {
            this.papi$clearBlockedOutput();
        }
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("RETURN"))
    private void papi$onFurnaceSetItemReturn(int slot, ItemStack stack, boolean isSetChanged, CallbackInfo ci) {
        ((PapiEpochContainer) this).papi$incrementEpoch();
    }

    @Inject(method = "setItems", at = @At("HEAD"))
    private void papi$onFurnaceSetItems(NonNullList<ItemStack> items, CallbackInfo ci) {
        this.papi$clearFailedInput();
        this.papi$clearBlockedOutput();
        this.papi$clearInvalidFuel();
    }
}
