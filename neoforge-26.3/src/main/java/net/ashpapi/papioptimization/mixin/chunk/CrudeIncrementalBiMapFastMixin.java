package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = CrudeIncrementalIntIdentityHashBiMap.class, priority = 1500)
public abstract class CrudeIncrementalBiMapFastMixin<K> {

    @Shadow private K[] keys;
    @Shadow private int[] values;
    @Shadow private K[] byId;

    @WrapMethod(method = "byId")
    private K papi$fastById(int id, Operation<K> original) {
        K[] arr = this.byId;
        return Integer.compareUnsigned(id, arr.length) < 0 ? arr[id] : null;
    }

    @WrapMethod(method = "getId")
    private int papi$fastGetId(K value, Operation<Integer> original) {
        K[] ks = this.keys;
        int len = ks.length;
        if (len == 0) {
            return -1;
        }
        int start = (HashCommon.mix(System.identityHashCode(value)) & Integer.MAX_VALUE) % len;
        for (int i = start; i < len; i++) {
            K k = ks[i];
            if (k == value) {
                return this.values[i];
            }
            if (k == null) {
                return -1;
            }
        }
        for (int i = 0; i < start; i++) {
            K k = ks[i];
            if (k == value) {
                return this.values[i];
            }
            if (k == null) {
                return -1;
            }
        }
        return -1;
    }

    @WrapMethod(method = "hash")
    private int papi$consistentHash(K value, Operation<Integer> original) {
        return (HashCommon.mix(System.identityHashCode(value)) & Integer.MAX_VALUE) % this.keys.length;
    }
}
