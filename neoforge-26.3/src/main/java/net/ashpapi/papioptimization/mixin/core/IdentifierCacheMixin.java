package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Identifier.class, priority = 1500)
public abstract class IdentifierCacheMixin {

    @Shadow
    @Final
    @Mutable
    private String namespace;

    @Shadow
    @Final
    private String path;

    @Unique
    private @Nullable String papi$cachedString;

    @Unique
    private int papi$cachedHash;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$internCommonNamespace(String namespace, String path, CallbackInfo ci) {
        if ("minecraft".equals(namespace)) {
            this.namespace = "minecraft";
        } else if ("neoforge".equals(namespace)) {
            this.namespace = "neoforge";
        } else if ("c".equals(namespace)) {
            this.namespace = "c";
        }
    }

    @WrapMethod(method = "toString")
    private String papi$cachedToString(Operation<String> original) {
        String s = this.papi$cachedString;
        if (s == null) {
            s = this.namespace + ":" + this.path;
            this.papi$cachedString = s;
        }
        return s;
    }

    @WrapMethod(method = "hashCode")
    private int papi$cachedHashCode(Operation<Integer> original) {
        int h = this.papi$cachedHash;
        if (h == 0) {
            h = 31 * this.namespace.hashCode() + this.path.hashCode();
            this.papi$cachedHash = h;
        }
        return h;
    }
}
