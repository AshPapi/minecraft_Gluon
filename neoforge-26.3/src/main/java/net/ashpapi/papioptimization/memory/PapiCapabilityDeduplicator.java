package net.ashpapi.papioptimization.memory;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Map;
import net.ashpapi.papioptimization.mixin.memory.BlockCapabilityAccessor;
import net.ashpapi.papioptimization.mixin.memory.EntityCapabilityAccessor;
import net.ashpapi.papioptimization.mixin.memory.ItemCapabilityAccessor;
import net.neoforged.neoforge.capabilities.BaseCapability;

public final class PapiCapabilityDeduplicator {

    private static final ReferenceOpenHashSet<BaseCapability<?, ?>> TOUCHED = new ReferenceOpenHashSet<>();

    private PapiCapabilityDeduplicator() {
    }

    public static synchronized void track(BaseCapability<?, ?> capability) {
        if (capability != null) {
            TOUCHED.add(capability);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized void compactAll() {
        if (TOUCHED.isEmpty()) {
            return;
        }
        ObjectOpenHashSet<List<?>> pool = new ObjectOpenHashSet<>(256);
        for (BaseCapability<?, ?> cap : TOUCHED) {
            Map<?, List<?>> map = null;
            if (cap instanceof BlockCapabilityAccessor<?, ?> bAcc) {
                map = (Map<?, List<?>>) (Map) bAcc.papi$getProviders();
            } else if (cap instanceof ItemCapabilityAccessor<?, ?> iAcc) {
                map = (Map<?, List<?>>) (Map) iAcc.papi$getProviders();
            } else if (cap instanceof EntityCapabilityAccessor<?, ?> eAcc) {
                map = (Map<?, List<?>>) (Map) eAcc.papi$getProviders();
            }
            if (map == null || map.isEmpty()) {
                continue;
            }
            for (Map.Entry<?, List<?>> entry : map.entrySet()) {
                List<?> raw = entry.getValue();
                if (raw == null) {
                    continue;
                }
                List<?> immutable = List.copyOf(raw);
                List<?> canonical = pool.addOrGet(immutable);
                entry.setValue(canonical);
            }
        }
        TOUCHED.clear();
        TOUCHED.trim();
    }
}
