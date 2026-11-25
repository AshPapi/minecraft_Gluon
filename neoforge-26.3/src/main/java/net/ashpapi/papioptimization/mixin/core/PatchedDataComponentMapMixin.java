package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PatchedDataComponentMap.class, priority = 1500)
public abstract class PatchedDataComponentMapMixin {

    @Shadow @Final private DataComponentMap prototype;
    @Shadow private Reference2ObjectMap<DataComponentType<?>, Object> patch;
    @Shadow private boolean copyOnWrite;

    @Unique
    private static final long PAPI$VALID_BIT = 1L << 32;

    @Unique
    private volatile long papi$hashState = 0L;

    @Unique
    private volatile int papi$modCount = 0;

    @Unique
    private void papi$afterMutation() {
        this.papi$modCount++;
        this.papi$hashState = 0L;
        if (!this.copyOnWrite && this.patch.isEmpty()) {
            this.patch = Reference2ObjectMaps.emptyMap();
            this.copyOnWrite = true;
        }
    }

    @Inject(
        method = "<init>(Lnet/minecraft/core/component/DataComponentMap;Lit/unimi/dsi/fastutil/objects/Reference2ObjectMap;Z)V",
        at = @At("RETURN")
    )
    private void papi$canonicalizeEmptyCtorPatch(
            DataComponentMap proto,
            Reference2ObjectMap<DataComponentType<?>, Object> patch,
            boolean cow,
            CallbackInfo ci
    ) {
        if (this.patch.isEmpty()) {
            this.patch = Reference2ObjectMaps.emptyMap();
            this.copyOnWrite = true;
        }
    }

    @Inject(method = "ensureMapOwnership", at = @At("HEAD"))
    private void papi$invalidateHashOnWrite(CallbackInfo ci) {
        this.papi$modCount++;
        this.papi$hashState = 0L;
    }

    @Inject(method = "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;", at = @At("RETURN"))
    private void papi$afterSet(CallbackInfoReturnable<Object> cir) {
        this.papi$afterMutation();
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void papi$afterRemove(CallbackInfoReturnable<Object> cir) {
        this.papi$afterMutation();
    }

    @Inject(method = "applyPatch(Lnet/minecraft/core/component/DataComponentPatch;)V", at = @At("RETURN"))
    private void papi$afterApplyPatch(DataComponentPatch patch, CallbackInfo ci) {
        this.papi$afterMutation();
    }

    @Inject(method = "clearPatch", at = @At("RETURN"))
    private void papi$afterClearPatch(CallbackInfo ci) {
        this.papi$afterMutation();
    }

    @Inject(method = "restorePatch", at = @At("RETURN"))
    private void papi$afterRestorePatch(DataComponentPatch patch, CallbackInfo ci) {
        this.papi$afterMutation();
    }

    @Inject(method = "setAll", at = @At("RETURN"))
    private void papi$afterSetAll(DataComponentMap map, CallbackInfo ci) {
        this.papi$afterMutation();
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void papi$fastGet(DataComponentType<? extends T> type, CallbackInfoReturnable<T> cir) {
        if (this.patch.isEmpty()) {
            cir.setReturnValue(this.prototype.get(type));
        }
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void papi$fastEquals(Object obj, CallbackInfoReturnable<Boolean> cir) {
        if (this == obj) {
            cir.setReturnValue(true);
            return;
        }
        if (!(obj instanceof PatchedDataComponentMap other)) {
            cir.setReturnValue(false);
            return;
        }
        PatchedDataComponentMapMixin otherMixin = (PatchedDataComponentMapMixin) (Object) other;
        if (this.prototype == otherMixin.prototype && this.patch == otherMixin.patch) {
            cir.setReturnValue(true);
            return;
        }
        long s1 = this.papi$hashState;
        long s2 = otherMixin.papi$hashState;
        if ((s1 & PAPI$VALID_BIT) != 0L && (s2 & PAPI$VALID_BIT) != 0L && (int) s1 != (int) s2) {
            cir.setReturnValue(false);
        }
    }

    @WrapMethod(method = "hashCode")
    private int papi$fastHashCode(Operation<Integer> original) {
        long s = this.papi$hashState;
        if ((s & PAPI$VALID_BIT) != 0L) {
            return (int) s;
        }
        int observedMod = this.papi$modCount;
        int computed = original.call();
        if (this.papi$modCount == observedMod) {
            this.papi$hashState = PAPI$VALID_BIT | Integer.toUnsignedLong(computed);
        }
        return computed;
    }
}
