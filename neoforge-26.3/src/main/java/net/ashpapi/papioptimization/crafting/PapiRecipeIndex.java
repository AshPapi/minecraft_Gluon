package net.ashpapi.papioptimization.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.ashpapi.papioptimization.mixin.crafting.IngredientAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class PapiRecipeIndex<I extends RecipeInput, T extends Recipe<I>> {

    private static final int[] EMPTY_INDICES = new int[0];
    private static final int LINEAR_DEDUP_LIMIT = 16;

    private static final ThreadLocal<MergeScratch> SCRATCH = ThreadLocal.withInitial(MergeScratch::new);

    private final RecipeHolder<T>[] orderedRecipes;
    private final IdentityHashMap<Item, int[]> byItem;
    private final int[] wildcards;
    private final boolean hadUnboundTag;

    @SuppressWarnings({"unchecked", "deprecation"})
    public PapiRecipeIndex(Collection<RecipeHolder<T>> ordered) {
        this.orderedRecipes = ordered.toArray(RecipeHolder[]::new);
        int count = this.orderedRecipes.length;
        if (count == 0) {
            this.byItem = new IdentityHashMap<>(0);
            this.wildcards = EMPTY_INDICES;
            this.hadUnboundTag = false;
            return;
        }
        IdentityHashMap<Item, IntArrayList> tempBuckets = new IdentityHashMap<>();
        IntArrayList tempWildcards = new IntArrayList();
        ReferenceArrayList<Item> scratchItems = new ReferenceArrayList<>();
        ReferenceOpenHashSet<Item> scratchSet = new ReferenceOpenHashSet<>();
        ReferenceArrayList<Item> bestItems = new ReferenceArrayList<>();
        boolean unboundTag = false;

        for (int idx = 0; idx < count; idx++) {
            Recipe<I> recipe = this.orderedRecipes[idx].value();
            if (!isExactConjunctiveVanillaRecipe(recipe)) {
                tempWildcards.add(idx);
                continue;
            }
            PlacementInfo placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace()) {
                tempWildcards.add(idx);
                continue;
            }
            List<Ingredient> ingredients = placement.ingredients();
            int n = ingredients.size();
            if (n == 0) {
                tempWildcards.add(idx);
                continue;
            }
            bestItems.clear();
            int bestSize = Integer.MAX_VALUE;
            boolean deadEmptyTag = false;

            for (int i = 0; i < n; i++) {
                Ingredient ing = ingredients.get(i);
                if (((IngredientAccessor) (Object) ing).papi$getCustomIngredient() != null) {
                    continue;
                }
                scratchItems.clear();
                boolean useSet = false;
                boolean prunedByBest = false;
                try {
                    Iterator<Holder<Item>> it = ing.items().iterator();
                    if (!it.hasNext()) {
                        deadEmptyTag = true;
                        break;
                    }
                    while (it.hasNext()) {
                        Item item = it.next().value();
                        boolean added;
                        if (useSet) {
                            added = scratchSet.add(item);
                            if (added) {
                                scratchItems.add(item);
                            }
                        } else if (!scratchItems.contains(item)) {
                            scratchItems.add(item);
                            added = true;
                            if (scratchItems.size() > LINEAR_DEDUP_LIMIT && it.hasNext()) {
                                scratchSet.clear();
                                scratchSet.addAll(scratchItems);
                                useSet = true;
                            }
                        } else {
                            added = false;
                        }
                        if (added && scratchItems.size() >= bestSize) {
                            prunedByBest = true;
                            break;
                        }
                    }
                } catch (IllegalStateException ignored) {
                    unboundTag = true;
                    continue;
                } finally {
                    if (useSet) {
                        scratchSet.clear();
                    }
                }
                if (prunedByBest) {
                    continue;
                }
                int size = scratchItems.size();
                if (size > 0 && size < bestSize) {
                    bestSize = size;
                    bestItems.clear();
                    bestItems.addAll(scratchItems);
                    if (size == 1) {
                        break;
                    }
                }
            }

            if (deadEmptyTag) {
                continue;
            }
            if (bestItems.isEmpty()) {
                tempWildcards.add(idx);
            } else {
                for (int j = 0, bSize = bestItems.size(); j < bSize; j++) {
                    Item item = bestItems.get(j);
                    tempBuckets.computeIfAbsent(item, k -> new IntArrayList()).add(idx);
                }
            }
        }

        IdentityHashMap<Item, int[]> frozen = new IdentityHashMap<>(tempBuckets.size());
        for (var entry : tempBuckets.entrySet()) {
            frozen.put(entry.getKey(), entry.getValue().toIntArray());
        }
        this.byItem = frozen;
        this.wildcards = tempWildcards.isEmpty() ? EMPTY_INDICES : tempWildcards.toIntArray();
        this.hadUnboundTag = unboundTag;
    }

    private static boolean isExactConjunctiveVanillaRecipe(Recipe<?> recipe) {
        if (recipe.isSpecial()) {
            return false;
        }
        Class<?> cls = recipe.getClass();
        return cls == ShapedRecipe.class
                || cls == ShapelessRecipe.class
                || cls == TransmuteRecipe.class
                || cls == SmeltingRecipe.class
                || cls == BlastingRecipe.class
                || cls == SmokingRecipe.class
                || cls == CampfireCookingRecipe.class
                || cls == StonecutterRecipe.class
                || cls == SmithingTransformRecipe.class
                || cls == SmithingTrimRecipe.class;
    }

    public boolean hadUnboundTag() {
        return this.hadUnboundTag;
    }

    public RecipeHolder<T>[] orderedRecipes() {
        return this.orderedRecipes;
    }

    public @Nullable RecipeHolder<T> findFirstMatch(I input, Level level) {
        RecipeHolder<T>[] recipes = this.orderedRecipes;
        if (recipes.length == 0 || input.isEmpty()) {
            return null;
        }
        int inputSize = input.size();
        MergeScratch scratch = SCRATCH.get();
        if (scratch.inUse) {
            scratch = new MergeScratch();
        }
        scratch.inUse = true;
        int distinctCount = 0;
        int listCount = 0;
        try {
            distinctCount = collectDistinctItems(input, inputSize, scratch);
            if (distinctCount == 0 && this.wildcards.length == 0) {
                return null;
            }
            listCount = collectCandidateLists(scratch, distinctCount);
            int m = listCount;
            if (m == 0) {
                return null;
            }
            int[][] lists = scratch.lists;
            if (m == 1) {
                int[] arr = lists[0];
                for (int i = 0, len = arr.length; i < len; i++) {
                    RecipeHolder<T> holder = recipes[arr[i]];
                    if (holder.value().matches(input, level)) {
                        return holder;
                    }
                }
                return null;
            }
            if (m == 2) {
                return findFirstFromTwo(recipes, lists[0], lists[1], input, level);
            }
            int[] cursors = scratch.cursors;
            int[] heads = scratch.heads;
            int last = -1;
            while (m > 0) {
                int minVal = heads[0];
                for (int j = 1; j < m; j++) {
                    int v = heads[j];
                    if (v < minVal) {
                        minVal = v;
                    }
                }
                int j = 0;
                while (j < m) {
                    if (heads[j] == minVal) {
                        int nextPos = cursors[j] + 1;
                        int[] arr = lists[j];
                        if (nextPos < arr.length) {
                            cursors[j] = nextPos;
                            heads[j] = arr[nextPos];
                            j++;
                        } else {
                            int tail = --m;
                            if (j < tail) {
                                lists[j] = lists[tail];
                                cursors[j] = cursors[tail];
                                heads[j] = heads[tail];
                            }
                        }
                    } else {
                        j++;
                    }
                }
                if (minVal != last) {
                    last = minVal;
                    RecipeHolder<T> holder = recipes[minVal];
                    if (holder.value().matches(input, level)) {
                        return holder;
                    }
                }
            }
            return null;
        } finally {
            if (distinctCount > 0) {
                Arrays.fill(scratch.distinctItems, 0, distinctCount, null);
            }
            if (listCount > 0) {
                Arrays.fill(scratch.lists, 0, listCount, null);
            }
            scratch.inUse = false;
        }
    }

    public Stream<RecipeHolder<T>> findMatchingStream(I input, Level level) {
        RecipeHolder<T>[] recipes = this.orderedRecipes;
        if (recipes.length == 0 || input.isEmpty()) {
            return Stream.empty();
        }
        int inputSize = input.size();
        MergeScratch scratch = SCRATCH.get();
        if (scratch.inUse) {
            scratch = new MergeScratch();
        }
        scratch.inUse = true;
        int distinctCount = 0;
        int listCount = 0;
        int[] singleA = null;
        int[] singleB = null;
        int mode = 0;
        try {
            distinctCount = collectDistinctItems(input, inputSize, scratch);
            if (distinctCount == 0 && this.wildcards.length == 0) {
                return Stream.empty();
            }
            listCount = collectCandidateLists(scratch, distinctCount);
            if (listCount == 0) {
                return Stream.empty();
            }
            if (listCount == 1) {
                singleA = scratch.lists[0];
                mode = 1;
            } else if (listCount == 2) {
                singleA = scratch.lists[0];
                singleB = scratch.lists[1];
                mode = 2;
            } else {
                singleA = mergeCandidateIndices(scratch, listCount);
                mode = 1;
            }
        } finally {
            if (distinctCount > 0) {
                Arrays.fill(scratch.distinctItems, 0, distinctCount, null);
            }
            if (listCount > 0) {
                Arrays.fill(scratch.lists, 0, listCount, null);
            }
            scratch.inUse = false;
        }

        if (mode == 1) {
            if (singleA == null || singleA.length == 0) {
                return Stream.empty();
            }
            return StreamSupport.stream(new SingleBucketSpliterator<>(recipes, singleA, input, level), false);
        }
        return StreamSupport.stream(new TwoBucketMergeSpliterator<>(recipes, singleA, singleB, input, level), false);
    }

    private static <I extends RecipeInput> int collectDistinctItems(I input, int inputSize, MergeScratch scratch) {
        scratch.ensureItems(inputSize);
        Item[] distinct = scratch.distinctItems;
        int distinctCount = 0;
        for (int s = 0; s < inputSize; s++) {
            ItemStack stack = input.getItem(s);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            boolean dup = false;
            for (int d = 0; d < distinctCount; d++) {
                if (distinct[d] == item) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                distinct[distinctCount++] = item;
            }
        }
        return distinctCount;
    }

    private int collectCandidateLists(MergeScratch scratch, int distinctCount) {
        scratch.ensureLists(distinctCount + 1);
        Item[] distinct = scratch.distinctItems;
        int[][] lists = scratch.lists;
        int[] cursors = scratch.cursors;
        int[] heads = scratch.heads;
        int listCount = 0;
        for (int d = 0; d < distinctCount; d++) {
            int[] bucket = this.byItem.get(distinct[d]);
            if (bucket != null && bucket.length > 0) {
                lists[listCount] = bucket;
                cursors[listCount] = 0;
                heads[listCount] = bucket[0];
                listCount++;
            }
        }
        if (this.wildcards.length > 0) {
            lists[listCount] = this.wildcards;
            cursors[listCount] = 0;
            heads[listCount] = this.wildcards[0];
            listCount++;
        }
        return listCount;
    }

    private static int[] mergeCandidateIndices(MergeScratch scratch, int m) {
        int[][] lists = scratch.lists;
        int[] cursors = scratch.cursors;
        int[] heads = scratch.heads;
        int maxCap = 0;
        for (int i = 0; i < m; i++) {
            maxCap += lists[i].length;
        }
        scratch.ensureMerged(maxCap);
        int[] merged = scratch.merged;
        int outLen = 0;
        int last = -1;
        while (m > 0) {
            int minVal = heads[0];
            for (int j = 1; j < m; j++) {
                int v = heads[j];
                if (v < minVal) {
                    minVal = v;
                }
            }
            int j = 0;
            while (j < m) {
                if (heads[j] == minVal) {
                    int nextPos = cursors[j] + 1;
                    int[] arr = lists[j];
                    if (nextPos < arr.length) {
                        cursors[j] = nextPos;
                        heads[j] = arr[nextPos];
                        j++;
                    } else {
                        int tail = --m;
                        if (j < tail) {
                            lists[j] = lists[tail];
                            cursors[j] = cursors[tail];
                            heads[j] = heads[tail];
                        }
                    }
                } else {
                    j++;
                }
            }
            if (minVal != last) {
                last = minVal;
                merged[outLen++] = minVal;
            }
        }
        return outLen == 0 ? EMPTY_INDICES : Arrays.copyOf(merged, outLen);
    }

    private static <I extends RecipeInput, T extends Recipe<I>> @Nullable RecipeHolder<T> findFirstFromTwo(
            RecipeHolder<T>[] recipes,
            int[] a,
            int[] b,
            I input,
            Level level
    ) {
        int ia = 0;
        int ib = 0;
        int la = a.length;
        int lb = b.length;
        int last = -1;
        while (ia < la && ib < lb) {
            int va = a[ia];
            int vb = b[ib];
            int idx;
            if (va < vb) {
                idx = va;
                ia++;
            } else if (vb < va) {
                idx = vb;
                ib++;
            } else {
                idx = va;
                ia++;
                ib++;
            }
            if (idx != last) {
                last = idx;
                RecipeHolder<T> holder = recipes[idx];
                if (holder.value().matches(input, level)) {
                    return holder;
                }
            }
        }
        while (ia < la) {
            int idx = a[ia++];
            if (idx != last) {
                last = idx;
                RecipeHolder<T> holder = recipes[idx];
                if (holder.value().matches(input, level)) {
                    return holder;
                }
            }
        }
        while (ib < lb) {
            int idx = b[ib++];
            if (idx != last) {
                last = idx;
                RecipeHolder<T> holder = recipes[idx];
                if (holder.value().matches(input, level)) {
                    return holder;
                }
            }
        }
        return null;
    }

    private static final class SingleBucketSpliterator<I extends RecipeInput, T extends Recipe<I>>
            extends Spliterators.AbstractSpliterator<RecipeHolder<T>> {

        private final RecipeHolder<T>[] recipes;
        private final int[] indices;
        private final I input;
        private final Level level;
        private int cursor;

        SingleBucketSpliterator(RecipeHolder<T>[] recipes, int[] indices, I input, Level level) {
            super(indices.length, Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE);
            this.recipes = recipes;
            this.indices = indices;
            this.input = input;
            this.level = level;
        }

        @Override
        public boolean tryAdvance(Consumer<? super RecipeHolder<T>> action) {
            int[] arr = this.indices;
            int len = arr.length;
            while (this.cursor < len) {
                RecipeHolder<T> holder = this.recipes[arr[this.cursor++]];
                if (holder.value().matches(this.input, this.level)) {
                    action.accept(holder);
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TwoBucketMergeSpliterator<I extends RecipeInput, T extends Recipe<I>>
            extends Spliterators.AbstractSpliterator<RecipeHolder<T>> {

        private final RecipeHolder<T>[] recipes;
        private final int[] a;
        private final int[] b;
        private final I input;
        private final Level level;
        private int ia;
        private int ib;
        private int last = -1;

        TwoBucketMergeSpliterator(RecipeHolder<T>[] recipes, int[] a, int[] b, I input, Level level) {
            super(a.length + b.length, Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE);
            this.recipes = recipes;
            this.a = a;
            this.b = b;
            this.input = input;
            this.level = level;
        }

        @Override
        public boolean tryAdvance(Consumer<? super RecipeHolder<T>> action) {
            int la = this.a.length;
            int lb = this.b.length;
            while (this.ia < la && this.ib < lb) {
                int va = this.a[this.ia];
                int vb = this.b[this.ib];
                int idx;
                if (va < vb) {
                    idx = va;
                    this.ia++;
                } else if (vb < va) {
                    idx = vb;
                    this.ib++;
                } else {
                    idx = va;
                    this.ia++;
                    this.ib++;
                }
                if (idx != this.last) {
                    this.last = idx;
                    RecipeHolder<T> holder = this.recipes[idx];
                    if (holder.value().matches(this.input, this.level)) {
                        action.accept(holder);
                        return true;
                    }
                }
            }
            while (this.ia < la) {
                int idx = this.a[this.ia++];
                if (idx != this.last) {
                    this.last = idx;
                    RecipeHolder<T> holder = this.recipes[idx];
                    if (holder.value().matches(this.input, this.level)) {
                        action.accept(holder);
                        return true;
                    }
                }
            }
            while (this.ib < lb) {
                int idx = this.b[this.ib++];
                if (idx != this.last) {
                    this.last = idx;
                    RecipeHolder<T> holder = this.recipes[idx];
                    if (holder.value().matches(this.input, this.level)) {
                        action.accept(holder);
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class MergeScratch {
        boolean inUse;
        Item[] distinctItems = new Item[16];
        int[][] lists = new int[16][];
        int[] cursors = new int[16];
        int[] heads = new int[16];
        int[] merged = new int[64];

        void ensureItems(int cap) {
            if (this.distinctItems.length < cap) {
                this.distinctItems = new Item[cap];
            }
        }

        void ensureLists(int cap) {
            if (this.lists.length < cap) {
                int n = Math.max(cap, this.lists.length << 1);
                this.lists = new int[n][];
                this.cursors = new int[n];
                this.heads = new int[n];
            }
        }

        void ensureMerged(int cap) {
            if (this.merged.length < cap) {
                this.merged = new int[Math.max(cap, this.merged.length << 1)];
            }
        }
    }
}
