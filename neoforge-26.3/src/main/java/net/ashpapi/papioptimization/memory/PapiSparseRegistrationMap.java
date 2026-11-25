package net.ashpapi.papioptimization.memory;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistrationInfo;
import org.jspecify.annotations.Nullable;

public final class PapiSparseRegistrationMap<K> extends AbstractMap<K, RegistrationInfo> {

    private final Map<K, ?> keyPresence;
    private @Nullable Reference2ReferenceOpenHashMap<K, RegistrationInfo> nonBuiltIn;

    public PapiSparseRegistrationMap(Map<K, ?> keyPresence) {
        this.keyPresence = keyPresence;
    }

    @Override
    public int size() {
        return this.keyPresence.size();
    }

    @Override
    public boolean isEmpty() {
        return this.keyPresence.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return (this.nonBuiltIn != null && this.nonBuiltIn.containsKey(key)) || this.keyPresence.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable RegistrationInfo get(Object key) {
        if (this.nonBuiltIn != null) {
            RegistrationInfo custom = this.nonBuiltIn.get(key);
            if (custom != null) {
                return custom;
            }
        }
        return this.keyPresence.containsKey((K) key) ? RegistrationInfo.BUILT_IN : null;
    }

    @Override
    public @Nullable RegistrationInfo put(K key, RegistrationInfo value) {
        RegistrationInfo prev = this.nonBuiltIn != null ? this.nonBuiltIn.remove(key) : null;
        if (!RegistrationInfo.BUILT_IN.equals(value)) {
            if (this.nonBuiltIn == null) {
                this.nonBuiltIn = new Reference2ReferenceOpenHashMap<>(8);
            }
            this.nonBuiltIn.put(key, value);
        } else if (this.nonBuiltIn != null && this.nonBuiltIn.isEmpty()) {
            this.nonBuiltIn = null;
        }
        return prev;
    }

    @Override
    public @Nullable RegistrationInfo remove(Object key) {
        if (this.nonBuiltIn != null) {
            RegistrationInfo removed = this.nonBuiltIn.remove(key);
            if (this.nonBuiltIn.isEmpty()) {
                this.nonBuiltIn = null;
            }
            if (removed != null) {
                return removed;
            }
        }
        return this.keyPresence.containsKey(key) ? RegistrationInfo.BUILT_IN : null;
    }

    @Override
    public void clear() {
        if (this.nonBuiltIn != null) {
            this.nonBuiltIn.clear();
            this.nonBuiltIn = null;
        }
    }

    @Override
    public Set<Entry<K, RegistrationInfo>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<K, RegistrationInfo>> iterator() {
                Iterator<K> keyIter = PapiSparseRegistrationMap.this.keyPresence.keySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return keyIter.hasNext();
                    }

                    @Override
                    public Entry<K, RegistrationInfo> next() {
                        K key = keyIter.next();
                        RegistrationInfo info = PapiSparseRegistrationMap.this.nonBuiltIn != null
                                ? PapiSparseRegistrationMap.this.nonBuiltIn.get(key)
                                : null;
                        return Map.entry(key, info != null ? info : RegistrationInfo.BUILT_IN);
                    }
                };
            }

            @Override
            public int size() {
                return PapiSparseRegistrationMap.this.size();
            }
        };
    }
}
