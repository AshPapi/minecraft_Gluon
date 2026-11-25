package net.ashpapi.papioptimization.mixin.entity;

import java.util.List;
import java.util.Map;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ClassInstanceMultiMap.class, priority = 1500)
public interface ClassInstanceMultiMapAccessor<T> {

    @Accessor("allInstances")
    List<T> papi$getAllInstances();

    @Accessor("byClass")
    Map<Class<?>, List<T>> papi$getByClass();
}
