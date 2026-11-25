package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompoundTag.class, priority = 1500)
public abstract class CompoundTagFastMixin {

    @Shadow
    @Final
    @Mutable
    private Map<String, Tag> tags;

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void papi$useFastMapDefault(CallbackInfo ci) {
        this.tags = new Object2ObjectOpenHashMap<>(4);
    }

    @Inject(method = "<init>(I)V", at = @At("RETURN"))
    private void papi$useFastMapSized(int expectedSize, CallbackInfo ci) {
        this.tags = new Object2ObjectOpenHashMap<>(Math.max(expectedSize, 0));
    }

    @WrapMethod(method = "copy()Lnet/minecraft/nbt/CompoundTag;")
    private CompoundTag papi$fastCopy(Operation<CompoundTag> original) {
        Map<String, Tag> src = this.tags;
        int size = src.size();
        if (size == 0) {
            return new CompoundTag();
        }
        CompoundTag copy = new CompoundTag(size);
        Map<String, Tag> dst = ((CompoundTagFastMixin) (Object) copy).tags;
        if (src instanceof Object2ObjectMap<String, Tag> fastSrc) {
            for (Object2ObjectMap.Entry<String, Tag> entry : Object2ObjectMaps.fastIterable(fastSrc)) {
                dst.put(entry.getKey(), entry.getValue().copy());
            }
        } else {
            for (Map.Entry<String, Tag> entry : src.entrySet()) {
                dst.put(entry.getKey(), entry.getValue().copy());
            }
        }
        return copy;
    }
}
