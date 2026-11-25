package net.ashpapi.papioptimization.mixin.crafting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiBatchCraftingContainer;
import net.ashpapi.papioptimization.access.PapiCraftingMenuCache;
import net.ashpapi.papioptimization.core.PapiRecipeEpoch;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ResultSlot.class, priority = 1500)
public abstract class ResultSlotFastMixin extends Slot {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Shadow
    @Final
    private Player player;

    protected ResultSlotFastMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @WrapMethod(method = "onTake")
    private void papi$batchGridUpdatesOnTake(Player player, ItemStack stack, Operation<Void> original) {
        if (this.craftSlots instanceof PapiBatchCraftingContainer batch) {
            batch.papi$beginBatch();
            try {
                original.call(player, stack);
            } finally {
                batch.papi$endBatchAndNotify();
            }
            return;
        }
        original.call(player, stack);
    }

    @WrapMethod(method = "getRemainingItems")
    private NonNullList<ItemStack> papi$fastRemainingItemsFromCachedRecipe(
            CraftingInput input,
            Level level,
            Operation<NonNullList<ItemStack>> original
    ) {
        if (level instanceof ServerLevel && this.container instanceof PapiCraftingMenuCache cache) {
            if (cache.papi$getLastRecipeEpoch() == PapiRecipeEpoch.current()) {
                RecipeHolder<CraftingRecipe> cached = cache.papi$getLastRecipe();
                if (cached != null) {
                    boolean assignedPlayer = CommonHooks.getCraftingPlayer() == null;
                    if (assignedPlayer) {
                        CommonHooks.setCraftingPlayer(this.player);
                    }
                    try {
                        if (cached.value().matches(input, level)) {
                            return cached.value().getRemainingItems(input);
                        }
                    } finally {
                        if (assignedPlayer) {
                            CommonHooks.setCraftingPlayer(null);
                        }
                    }
                }
            }
        }
        return original.call(input, level);
    }
}
