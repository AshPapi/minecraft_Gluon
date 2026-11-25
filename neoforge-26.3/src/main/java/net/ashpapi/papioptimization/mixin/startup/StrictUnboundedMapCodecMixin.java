package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.util.Map;
import net.ashpapi.papioptimization.startup.FastMapCodecHelper;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ExtraCodecs.StrictUnboundedMapCodec.class, priority = 1500)
public abstract class StrictUnboundedMapCodecMixin<K, V> {

    @Shadow
    @Final
    private Codec<K> keyCodec;

    @Shadow
    @Final
    private Codec<V> elementCodec;

    @WrapMethod(
        method = "decode(Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/MapLike;)Lcom/mojang/serialization/DataResult;"
    )
    private <T> DataResult<Map<K, V>> papi$fastDecodeStrict(
            DynamicOps<T> ops,
            MapLike<T> input,
            Operation<DataResult<Map<K, V>>> original
    ) {
        return FastMapCodecHelper.decodeStrict(this.keyCodec, this.elementCodec, ops, input);
    }

    @WrapMethod(
        method = "encode(Ljava/util/Map;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"
    )
    private <T> DataResult<T> papi$fastEncodeStrict(
            Map<K, V> input,
            DynamicOps<T> ops,
            T prefix,
            Operation<DataResult<T>> original
    ) {
        return FastMapCodecHelper.encode(this.keyCodec, this.elementCodec, input, ops, ops.mapBuilder()).build(prefix);
    }
}
