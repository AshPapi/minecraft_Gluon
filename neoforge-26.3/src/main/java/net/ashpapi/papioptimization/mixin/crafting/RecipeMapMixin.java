package net.ashpapi.papioptimization.mixin.crafting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.ashpapi.papioptimization.access.FastRecipeMap;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.ashpapi.papioptimization.crafting.PapiRecipeIndex;
import net.ashpapi.papioptimization.crafting.PapiRecipeMapCache;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeMap.class, priority = 1500)
public abstract class RecipeMapMixin implements FastRecipeMap {

    @Shadow
    public abstract <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type);

    @Unique
    private final AtomicReference<PapiRecipeMapCache> papi$cache = new AtomicReference<>(new PapiRecipeMapCache(-1));

    @Inject(method = "order", at = @At("RETURN"))
    private void papi$onOrder(Object2IntMap<ResourceKey<Recipe<?>>> map, CallbackInfo ci) {
        this.papi$cache.set(new PapiRecipeMapCache(PapiTagEpoch.current()));
    }

    @WrapMethod(method = "getRecipesFor")
    private <I extends RecipeInput, T extends Recipe<I>> Stream<RecipeHolder<T>> papi$fastGetRecipesFor(
            RecipeType<T> type,
            I input,
            Level level,
            Operation<Stream<RecipeHolder<T>>> original
    ) {
        if (input.isEmpty()) {
            return Stream.empty();
        }
        return this.papi$getOrCreateIndex(type).findMatchingStream(input, level);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, T extends Recipe<I>> PapiRecipeIndex<I, T> papi$getOrCreateIndex(RecipeType<T> type) {
        int currentEpoch = PapiTagEpoch.current();
        PapiRecipeMapCache cache = this.papi$cache.get();
        if (cache.epoch() != currentEpoch) {
            PapiRecipeMapCache next = new PapiRecipeMapCache(currentEpoch);
            if (this.papi$cache.compareAndSet(cache, next)) {
                cache = next;
            } else {
                cache = this.papi$cache.get();
            }
        }
        PapiRecipeIndex<?, ?> index = cache.indices().get(type);
        if (index != null) {
            return (PapiRecipeIndex<I, T>) index;
        }
        PapiRecipeIndex<I, T> built = new PapiRecipeIndex<>(this.byType(type));
        if (!built.hadUnboundTag() && cache.epoch() == PapiTagEpoch.current()) {
            PapiRecipeIndex<?, ?> existing = cache.indices().putIfAbsent(type, built);
            if (existing != null) {
                return (PapiRecipeIndex<I, T>) existing;
            }
        }
        return built;
    }

    @Override
    public <I extends RecipeInput, T extends Recipe<I>> RecipeHolder<T>[] papi$getRecipesArray(RecipeType<T> type) {
        return this.papi$getOrCreateIndex(type).orderedRecipes();
    }
}
