package net.ashpapi.papioptimization.mixin.startup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.BaseMapCodec;
import java.util.Map;
import net.ashpapi.papioptimization.startup.FastMapCodecHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaseMapCodec.class, priority = 1500, remap = false)
public interface BaseMapCodecMixin<K, V> {

    @Shadow
    Codec<K> keyCodec();

    @Shadow
    Codec<V> elementCodec();

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true, require = 0)
    private <T> void papi$fastDecode(
            DynamicOps<T> ops,
            MapLike<T> input,
            CallbackInfoReturnable<DataResult<Map<K, V>>> cir
    ) {
        cir.setReturnValue(FastMapCodecHelper.decodeBase(this.keyCodec(), this.elementCodec(), ops, input));
    }

    @Inject(method = "encode", at = @At("HEAD"), cancellable = true, require = 0)
    private <T> void papi$fastEncode(
            Map<K, V> input,
            DynamicOps<T> ops,
            RecordBuilder<T> prefix,
            CallbackInfoReturnable<RecordBuilder<T>> cir
    ) {
        cir.setReturnValue(FastMapCodecHelper.encode(this.keyCodec(), this.elementCodec(), input, ops, prefix));
    }
}
