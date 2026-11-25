package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.SoftReference;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = VanillaRegistries.class, priority = 1500)
public abstract class VanillaRegistriesMemoMixin {

    @Unique
    private static volatile @Nullable SoftReference<HolderLookup.Provider> papi$cachedWorldLookup;

    @WrapMethod(method = "createWorldLookup")
    private static HolderLookup.Provider papi$memoizeWorldLookup(Operation<HolderLookup.Provider> original) {
        SoftReference<HolderLookup.Provider> ref = papi$cachedWorldLookup;
        HolderLookup.Provider cached = ref != null ? ref.get() : null;
        if (cached != null) {
            return cached;
        }
        synchronized (VanillaRegistries.class) {
            ref = papi$cachedWorldLookup;
            cached = ref != null ? ref.get() : null;
            if (cached == null) {
                cached = original.call();
                papi$cachedWorldLookup = new SoftReference<>(cached);
            }
            return cached;
        }
    }
}
