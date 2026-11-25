package net.ashpapi.papioptimization.crafting;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.crafting.RecipeType;

public final class PapiRecipeMapCache {

    private final int epoch;
    private final ConcurrentHashMap<RecipeType<?>, PapiRecipeIndex<?, ?>> indices;

    public PapiRecipeMapCache(int epoch) {
        this.epoch = epoch;
        this.indices = new ConcurrentHashMap<>();
    }

    public int epoch() {
        return this.epoch;
    }

    public ConcurrentHashMap<RecipeType<?>, PapiRecipeIndex<?, ?>> indices() {
        return this.indices;
    }
}
