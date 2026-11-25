package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collection;
import java.util.Set;
import net.ashpapi.papioptimization.core.PapiTagEpoch;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = CreativeModeTab.class, priority = 1500)
public abstract class CreativeModeTabMemoMixin {

    @Unique
    private static long papi$categoryBuildGeneration;

    @Shadow
    @Final
    private CreativeModeTab.Type type;

    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Unique
    private CreativeModeTab.@Nullable ItemDisplayParameters papi$lastParams;

    @Unique
    private int papi$lastTagEpoch = -1;

    @Unique
    private long papi$lastCategoryGen = -1L;

    @WrapMethod(method = "buildContents")
    private void papi$memoizeBuildContents(
            CreativeModeTab.ItemDisplayParameters parameters,
            Operation<Void> original
    ) {
        synchronized (CreativeModeTab.class) {
            int tagEpoch = PapiTagEpoch.current();
            if (this.type == CreativeModeTab.Type.CATEGORY) {
                if (this.papi$lastParams != null
                        && this.papi$lastTagEpoch == tagEpoch
                        && !this.displayItems.isEmpty()
                        && this.papi$lastParams.equals(parameters)) {
                    return;
                }
                original.call(parameters);
                this.papi$lastParams = parameters;
                this.papi$lastTagEpoch = tagEpoch;
                papi$categoryBuildGeneration++;
                this.papi$lastCategoryGen = papi$categoryBuildGeneration;
            } else {
                if (this.papi$lastParams != null
                        && this.papi$lastTagEpoch == tagEpoch
                        && this.papi$lastCategoryGen == papi$categoryBuildGeneration
                        && (!this.displayItems.isEmpty() || !this.displayItemsSearchTab.isEmpty())
                        && this.papi$lastParams.equals(parameters)) {
                    return;
                }
                original.call(parameters);
                this.papi$lastParams = parameters;
                this.papi$lastTagEpoch = tagEpoch;
                this.papi$lastCategoryGen = papi$categoryBuildGeneration;
            }
        }
    }
}
