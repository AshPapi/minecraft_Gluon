package net.ashpapi.papioptimization.mixin.crafting;

import net.ashpapi.papioptimization.access.PapiCraftingMenuCache;
import net.ashpapi.papioptimization.core.PapiRecipeEpoch;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ResultContainer.class, priority = 1500)
public abstract class ResultContainerCacheMixin implements PapiCraftingMenuCache {

    @Unique
    private @Nullable RecipeHolder<CraftingRecipe> papi$lastCraftingRecipe;

    @Unique
    private int papi$lastCraftingEpoch = -1;

    @SuppressWarnings("unchecked")
    @Inject(method = "setRecipeUsed", at = @At("HEAD"))
    private void papi$captureNonNullCraftingRecipe(@Nullable RecipeHolder<?> recipe, CallbackInfo ci) {
        if (recipe != null && recipe.value() instanceof CraftingRecipe) {
            this.papi$lastCraftingRecipe = (RecipeHolder<CraftingRecipe>) recipe;
            this.papi$lastCraftingEpoch = PapiRecipeEpoch.current();
        }
    }

    @Override
    public @Nullable RecipeHolder<CraftingRecipe> papi$getLastRecipe() {
        return this.papi$lastCraftingRecipe;
    }

    @Override
    public void papi$setLastRecipe(@Nullable RecipeHolder<CraftingRecipe> recipe) {
        this.papi$lastCraftingRecipe = recipe;
    }

    @Override
    public int papi$getLastRecipeEpoch() {
        return this.papi$lastCraftingEpoch;
    }

    @Override
    public void papi$setLastRecipeEpoch(int epoch) {
        this.papi$lastCraftingEpoch = epoch;
    }
}
