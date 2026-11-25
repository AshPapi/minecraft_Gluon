package net.ashpapi.papioptimization.mixin.crafting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiContainerMenuAccess;
import net.ashpapi.papioptimization.access.PapiCraftingMenuCache;
import net.ashpapi.papioptimization.core.PapiRecipeEpoch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingMenu.class, priority = 1500)
public abstract class CraftingMenuCacheMixin {

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "slotChangedCraftingGrid")
    private static void papi$reuseLastMatchedRecipe(
            AbstractContainerMenu menu,
            ServerLevel level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> lastRecipe,
            Operation<Void> original
    ) {
        int epoch = PapiRecipeEpoch.current();
        PapiCraftingMenuCache resultCache = (resultSlots instanceof PapiCraftingMenuCache rc) ? rc : null;
        PapiCraftingMenuCache menuCache = (menu instanceof PapiCraftingMenuCache mc) ? mc : null;
        PapiCraftingMenuCache primaryCache = resultCache != null ? resultCache : menuCache;

        RecipeHolder<CraftingRecipe> effectiveLast = lastRecipe;
        if (effectiveLast == null && primaryCache != null) {
            if (primaryCache.papi$getLastRecipeEpoch() == epoch) {
                effectiveLast = primaryCache.papi$getLastRecipe();
            } else {
                primaryCache.papi$setLastRecipe(null);
            }
            if (effectiveLast == null && menuCache != null && menuCache != primaryCache) {
                if (menuCache.papi$getLastRecipeEpoch() == epoch) {
                    effectiveLast = menuCache.papi$getLastRecipe();
                } else {
                    menuCache.papi$setLastRecipe(null);
                }
            }
        }

        original.call(menu, level, player, craftSlots, resultSlots, effectiveLast);

        int postEpoch = PapiRecipeEpoch.current();
        if (postEpoch != epoch) {
            if (resultCache != null) {
                resultCache.papi$setLastRecipe(null);
                resultCache.papi$setLastRecipeEpoch(postEpoch);
            }
            if (menuCache != null) {
                menuCache.papi$setLastRecipe(null);
                menuCache.papi$setLastRecipeEpoch(postEpoch);
            }
        } else if (!resultSlots.getItem(0).isEmpty()) {
            RecipeHolder<?> used = resultSlots.getRecipeUsed();
            RecipeHolder<CraftingRecipe> nextCached =
                    (used != null && used.value() instanceof CraftingRecipe)
                            ? (RecipeHolder<CraftingRecipe>) used
                            : null;
            if (resultCache != null) {
                resultCache.papi$setLastRecipe(nextCached);
                resultCache.papi$setLastRecipeEpoch(postEpoch);
            }
            if (menuCache != null) {
                menuCache.papi$setLastRecipe(nextCached);
                menuCache.papi$setLastRecipeEpoch(postEpoch);
            }
        }
    }

    @Inject(
            method = "slotChangedCraftingGrid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;setRemoteSlot(ILnet/minecraft/world/item/ItemStack;)V"
            ),
            cancellable = true
    )
    private static void papi$suppressRedundantSetSlotPacket(
            AbstractContainerMenu menu,
            ServerLevel level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> lastRecipe,
            CallbackInfo ci
    ) {
        if (menu instanceof PapiContainerMenuAccess access && access.papi$isSuppressingRemoteUpdates()) {
            ci.cancel();
        }
    }
}
