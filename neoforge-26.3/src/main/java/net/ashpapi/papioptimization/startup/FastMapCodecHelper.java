package net.ashpapi.papioptimization.startup;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class FastMapCodecHelper {

    private FastMapCodecHelper() {
    }

    public static <K, V, T> DataResult<Map<K, V>> decodeBase(
            Codec<K> keyCodec,
            Codec<V> elementCodec,
            DynamicOps<T> ops,
            MapLike<T> input
    ) {
        Iterator<Pair<T, T>> it = input.entries().iterator();
        if (!it.hasNext()) {
            return DataResult.success(ImmutableMap.of(), Lifecycle.stable());
        }

        Object2ObjectLinkedOpenHashMap<K, V> read = new Object2ObjectLinkedOpenHashMap<>();
        Lifecycle lifecycle = Lifecycle.stable();
        Stream.Builder<Pair<T, T>> failed = null;
        DataResult<Unit> errorAccumulator = null;

        while (it.hasNext()) {
            Pair<T, T> pair = it.next();
            DataResult<K> kRes = keyCodec.parse(ops, pair.getFirst());
            DataResult<V> vRes = elementCodec.parse(ops, pair.getSecond());

            Optional<DataResult.Error<K>> kErr = kRes.error();
            Optional<DataResult.Error<V>> vErr = vRes.error();
            Optional<K> kOpt = kRes.resultOrPartial();
            Optional<V> vOpt = vRes.resultOrPartial();

            if (errorAccumulator == null && kErr.isEmpty() && vErr.isEmpty() && kOpt.isPresent() && vOpt.isPresent()) {
                K k = kOpt.get();
                V v = vOpt.get();
                V existing = read.putIfAbsent(k, v);
                if (existing == null) {
                    lifecycle = lifecycle.add(kRes.lifecycle()).add(vRes.lifecycle());
                    continue;
                }
                failed = Stream.builder();
                failed.add(pair);
                DataResult<Unit> baseState = DataResult.success(Unit.INSTANCE, lifecycle);
                DataResult<Unit> dupErr = DataResult.error(() -> "Duplicate entry for key: '" + k + "'");
                errorAccumulator = baseState.apply2stable((u1, u2) -> u1, dupErr).apply2stable((u1, vIgnore) -> u1, vRes);
                continue;
            }

            if (failed == null) {
                failed = Stream.builder();
                errorAccumulator = DataResult.success(Unit.INSTANCE, lifecycle);
            }

            DataResult<Pair<K, V>> entryRes = kRes.apply2stable(Pair::of, vRes);
            Optional<Pair<K, V>> partialPair = entryRes.resultOrPartial();
            if (partialPair.isPresent()) {
                Pair<K, V> kv = partialPair.get();
                V existing = read.putIfAbsent(kv.getFirst(), kv.getSecond());
                if (existing != null) {
                    failed.add(pair);
                    DataResult<Unit> dupErr = DataResult.error(() -> "Duplicate entry for key: '" + kv.getFirst() + "'");
                    errorAccumulator = errorAccumulator.apply2stable((u1, u2) -> u1, dupErr);
                    continue;
                }
            }
            if (entryRes.isError()) {
                failed.add(pair);
            }
            errorAccumulator = errorAccumulator.apply2stable((u1, p) -> u1, entryRes);
        }

        Map<K, V> elements = ImmutableMap.copyOf(read);
        if (errorAccumulator == null) {
            return DataResult.success(elements, lifecycle);
        }
        T errors = ops.createMap(failed.build());
        return errorAccumulator
                .map(unit -> elements)
                .setPartial(elements)
                .mapError(e -> e + " missed input: " + errors);
    }

    public static <K, V, T> DataResult<Map<K, V>> decodeStrict(
            Codec<K> keyCodec,
            Codec<V> elementCodec,
            DynamicOps<T> ops,
            MapLike<T> input
    ) {
        Iterator<Pair<T, T>> it = input.entries().iterator();
        if (!it.hasNext()) {
            return DataResult.success(ImmutableMap.of());
        }
        Object2ObjectLinkedOpenHashMap<K, V> read = new Object2ObjectLinkedOpenHashMap<>();
        while (it.hasNext()) {
            Pair<T, T> pair = it.next();
            DataResult<K> kRes = keyCodec.parse(ops, pair.getFirst());
            DataResult<V> vRes = elementCodec.parse(ops, pair.getSecond());
            Optional<DataResult.Error<K>> kErr = kRes.error();
            Optional<DataResult.Error<V>> vErr = vRes.error();
            if (kErr.isPresent() || vErr.isPresent()) {
                DataResult<Pair<K, V>> entry = kRes.apply2stable(Pair::of, vRes);
                String msg = entry.error().map(DataResult.Error::message).orElse("Empty or invalid map contents are not allowed");
                return DataResult.error(() -> kRes.result().map(k -> "[" + k + "] " + msg).orElse(msg));
            }
            Optional<K> kOpt = kRes.result();
            Optional<V> vOpt = vRes.result();
            if (kOpt.isEmpty() || vOpt.isEmpty()) {
                return DataResult.error(() -> "Empty or invalid map contents are not allowed");
            }
            K key = kOpt.get();
            V existing = read.putIfAbsent(key, vOpt.get());
            if (existing != null) {
                return DataResult.error(() -> "Duplicate entry for key: '" + key + "'");
            }
        }
        return DataResult.success(ImmutableMap.copyOf(read));
    }

    public static <K, V, T> RecordBuilder<T> encode(
            Codec<K> keyCodec,
            Codec<V> elementCodec,
            Map<K, V> input,
            DynamicOps<T> ops,
            RecordBuilder<T> prefix
    ) {
        if (input.isEmpty()) {
            return prefix;
        }
        if (input instanceof Object2ObjectMap<K, V> fastMap) {
            for (Object2ObjectMap.Entry<K, V> entry : Object2ObjectMaps.fastIterable(fastMap)) {
                prefix.add(keyCodec.encodeStart(ops, entry.getKey()), elementCodec.encodeStart(ops, entry.getValue()));
            }
        } else {
            for (Map.Entry<K, V> entry : input.entrySet()) {
                prefix.add(keyCodec.encodeStart(ops, entry.getKey()), elementCodec.encodeStart(ops, entry.getValue()));
            }
        }
        return prefix;
    }
}
