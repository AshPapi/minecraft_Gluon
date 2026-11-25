package net.ashpapi.papioptimization.mixin.crafting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.ashpapi.papioptimization.access.FastRecipeMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RecipeManager.class, priority = 1500)
public abstract class RecipeManagerFastMixin {

    @Shadow
    private RecipeMap recipes;

    @Shadow
    private <T extends Recipe<?>> @Nullable RecipeHolder<T> byKeyTyped(
            RecipeType<T> type,
            ResourceKey<Recipe<?>> key
    ) {
        return null;
    }

    @WrapMethod(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;"
    )
    private <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> papi$fastGetRecipeForByKey(
            RecipeType<T> type,
            I input,
            Level level,
            @Nullable ResourceKey<Recipe<?>> lastRecipe,
            Operation<Optional<RecipeHolder<T>>> original
    ) {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        if (lastRecipe != null) {
            RecipeHolder<T> holder = this.byKeyTyped(type, lastRecipe);
            if (holder != null && holder.value().matches(input, level)) {
                return Optional.of(holder);
            }
        }
        RecipeMap currentMap = this.recipes;
        if (currentMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(((FastRecipeMap) currentMap).papi$getOrCreateIndex(type).findFirstMatch(input, level));
    }

    @WrapMethod(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;"
    )
    private <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> papi$fastGetRecipeForByHolder(
            RecipeType<T> type,
            I input,
            Level level,
            @Nullable RecipeHolder<T> lastRecipe,
            Operation<Optional<RecipeHolder<T>>> original
    ) {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        if (lastRecipe != null && lastRecipe.value().matches(input, level)) {
            return Optional.of(lastRecipe);
        }
        RecipeMap currentMap = this.recipes;
        if (currentMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(((FastRecipeMap) currentMap).papi$getOrCreateIndex(type).findFirstMatch(input, level));
    }

    @WrapMethod(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"
    )
    private <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> papi$fastGetRecipeFor(
            RecipeType<T> type,
            I input,
            Level level,
            Operation<Optional<RecipeHolder<T>>> original
    ) {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        RecipeMap currentMap = this.recipes;
        if (currentMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(((FastRecipeMap) currentMap).papi$getOrCreateIndex(type).findFirstMatch(input, level));
    }
}
