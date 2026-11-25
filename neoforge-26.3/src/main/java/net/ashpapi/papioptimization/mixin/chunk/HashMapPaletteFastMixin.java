package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.PaletteResize;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = HashMapPalette.class, priority = 1500)
public abstract class HashMapPaletteFastMixin<T> {

    @Shadow @Final private CrudeIncrementalIntIdentityHashBiMap<T> values;
    @Shadow @Final private int bits;

    @Unique
    private T papi$lastKey;

    @Unique
    private int papi$lastId = -1;

    @WrapMethod(method = "idFor")
    private int papi$fastIdFor(T state, PaletteResize<T> resizeHandler, Operation<Integer> original) {
        if (state == this.papi$lastKey && this.papi$lastId >= 0) {
            return this.papi$lastId;
        }
        int id = this.values.getId(state);
        if (id == -1) {
            id = this.values.add(state);
            if (id >= (1 << this.bits)) {
                return resizeHandler.onResize(this.bits + 1, state);
            }
        }
        this.papi$lastKey = state;
        this.papi$lastId = id;
        return id;
    }
}
