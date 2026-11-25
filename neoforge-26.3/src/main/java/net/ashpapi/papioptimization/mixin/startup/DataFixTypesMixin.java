package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.ashpapi.papioptimization.startup.PapiLazyDfuBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = DataFixTypes.class, priority = 1500)
public abstract class DataFixTypesMixin {

    @WrapMethod(
        method = "update(Lcom/mojang/datafixers/DataFixer;Lcom/mojang/serialization/Dynamic;II)Lcom/mojang/serialization/Dynamic;"
    )
    private <T> Dynamic<T> papi$skipSameVersionDynamic(
            DataFixer fixer,
            Dynamic<T> input,
            int version,
            int newVersion,
            Operation<Dynamic<T>> original
    ) {
        if (version >= newVersion) {
            return input;
        }
        PapiLazyDfuBridge.recordLegacyUpdate(fixer);
        return original.call(fixer, input, version, newVersion);
    }

    @WrapMethod(
        method = "update(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;"
    )
    private CompoundTag papi$skipSameVersionCompoundTag(
            DataFixer fixer,
            CompoundTag tag,
            int version,
            int newVersion,
            Operation<CompoundTag> original
    ) {
        if (version >= newVersion) {
            return tag;
        }
        PapiLazyDfuBridge.recordLegacyUpdate(fixer);
        return original.call(fixer, tag, version, newVersion);
    }
}
