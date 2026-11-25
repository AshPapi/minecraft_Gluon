package net.ashpapi.papioptimization.mixin.biome;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.ashpapi.papioptimization.core.PapiEnumCache;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MobSpawnSettings.class, priority = 1500)
public abstract class MobSpawnSettingsFastMixin {

    @Shadow
    @Final
    public static WeightedList<MobSpawnSettings.SpawnerData> EMPTY_MOB_LIST;

    @Shadow
    @Final
    private Map<MobCategory, WeightedList<MobSpawnSettings.SpawnerData>> spawnsByCategory;

    @Unique
    private WeightedList<MobSpawnSettings.SpawnerData>[] papi$byOrdinal;

    @Unique
    private WeightedList<MobSpawnSettings.SpawnerData>[] papi$rawByOrdinal;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$buildCategoryArray(
            Map<MobCategory, WeightedList<MobSpawnSettings.SpawnerData>> spawns,
            Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> costs,
            CallbackInfo ci
    ) {
        MobCategory[] categories = PapiEnumCache.MOB_CATEGORIES;
        int len = categories.length;
        WeightedList<MobSpawnSettings.SpawnerData>[] resolved = new WeightedList[len];
        WeightedList<MobSpawnSettings.SpawnerData>[] raw = new WeightedList[len];
        for (int i = 0; i < len; i++) {
            MobCategory cat = categories[i];
            WeightedList<MobSpawnSettings.SpawnerData> list = this.spawnsByCategory.get(cat);
            raw[i] = list;
            resolved[i] = list != null ? list : EMPTY_MOB_LIST;
        }
        this.papi$rawByOrdinal = raw;
        this.papi$byOrdinal = resolved;
    }

    @WrapMethod(method = "getMobsToSpawn")
    private WeightedList<MobSpawnSettings.SpawnerData> papi$fastGetMobsToSpawn(
            MobCategory category,
            Operation<WeightedList<MobSpawnSettings.SpawnerData>> original
    ) {
        WeightedList<MobSpawnSettings.SpawnerData>[] arr = this.papi$byOrdinal;
        if (arr != null && category != null) {
            int ord = category.ordinal();
            if (Integer.compareUnsigned(ord, arr.length) < 0) {
                return arr[ord];
            }
        }
        return original.call(category);
    }

    @WrapMethod(method = "getMobsInCategory")
    private @Nullable WeightedList<MobSpawnSettings.SpawnerData> papi$fastGetMobsInCategory(
            MobCategory category,
            Operation<WeightedList<MobSpawnSettings.SpawnerData>> original
    ) {
        WeightedList<MobSpawnSettings.SpawnerData>[] arr = this.papi$rawByOrdinal;
        if (arr != null && category != null) {
            int ord = category.ordinal();
            if (Integer.compareUnsigned(ord, arr.length) < 0) {
                return arr[ord];
            }
        }
        return original.call(category);
    }
}
