package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClassInstanceMultiMap.class, priority = 1500)
public abstract class ClassInstanceMultiMapFastMixin<T> {

    @Shadow
    @Final
    @Mutable
    private Map<Class<?>, List<T>> byClass;

    @Shadow
    @Final
    private Class<T> baseClass;

    @Shadow
    @Final
    @Mutable
    private List<T> allInstances;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$initFastCollections(Class<T> baseClass, CallbackInfo ci) {
        ObjectArrayList<T> instances = new ObjectArrayList<>();
        Reference2ObjectOpenHashMap<Class<?>, List<T>> map = new Reference2ObjectOpenHashMap<>(4);
        map.put(baseClass, instances);
        this.allInstances = instances;
        this.byClass = map;
    }

    @WrapMethod(method = "add")
    private boolean papi$fastAdd(T instance, Operation<Boolean> original) {
        if (!(this.byClass instanceof Reference2ObjectMap<Class<?>, List<T>> fastMap)) {
            return original.call(instance);
        }
        boolean added = false;
        for (Reference2ObjectMap.Entry<Class<?>, List<T>> entry : Reference2ObjectMaps.fastIterable(fastMap)) {
            if (entry.getKey().isInstance(instance)) {
                added |= entry.getValue().add(instance);
            }
        }
        return added;
    }

    @WrapMethod(method = "remove")
    private boolean papi$fastRemove(Object instance, Operation<Boolean> original) {
        if (!(this.byClass instanceof Reference2ObjectMap<Class<?>, List<T>> fastMap)) {
            return original.call(instance);
        }
        boolean removed = false;
        for (Reference2ObjectMap.Entry<Class<?>, List<T>> entry : Reference2ObjectMaps.fastIterable(fastMap)) {
            if (entry.getKey().isInstance(instance)) {
                removed |= entry.getValue().remove(instance);
            }
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "find")
    private <S> Collection<S> papi$fastFind(Class<S> type, Operation<Collection<S>> original) {
        List<T> cached = this.byClass.get(type);
        if (cached != null) {
            return (Collection<S>) Collections.unmodifiableCollection(cached);
        }
        if (!this.baseClass.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Don't know how to search for " + type);
        }
        List<T> all = this.allInstances;
        int n = all.size();
        ObjectArrayList<T> filtered = new ObjectArrayList<>(Math.min(n, 8));
        for (int i = 0; i < n; i++) {
            T elem = all.get(i);
            if (type.isInstance(elem)) {
                filtered.add(elem);
            }
        }
        this.byClass.put(type, filtered);
        return (Collection<S>) Collections.unmodifiableCollection(filtered);
    }
}
