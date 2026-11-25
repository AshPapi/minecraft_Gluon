package net.ashpapi.papioptimization.mixin.render;

import java.util.function.Function;
import net.ashpapi.papioptimization.access.PapiSignTextCache;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SignText.class, priority = 1500)
public abstract class SignTextEmptyCacheMixin implements PapiSignTextCache {

    @Unique
    private byte papi$visibleCacheMask;

    @Unique
    private @Nullable Object papi$clientRenderCache;

    @Inject(
        method = "getRenderMessages",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/block/entity/SignText;renderMessages:[Lnet/minecraft/util/FormattedCharSequence;",
            opcode = Opcodes.PUTFIELD
        )
    )
    private void papi$invalidatePreparedCacheOnRebuild(
            boolean filtered,
            Function<Component, FormattedCharSequence> splitter,
            CallbackInfoReturnable<FormattedCharSequence[]> cir
    ) {
        this.papi$clientRenderCache = null;
    }

    @Override
    public boolean papi$hasVisibleMessage(boolean filtered) {
        int readyBit = filtered ? 4 : 1;
        int valBit = filtered ? 8 : 2;
        byte mask = this.papi$visibleCacheMask;
        if ((mask & readyBit) != 0) {
            return (mask & valBit) != 0;
        }
        boolean nonEmpty = PapiSignTextCache.papi$computeVisibleMessage((SignText) (Object) this, filtered);
        byte updated = (byte) (mask | readyBit | (nonEmpty ? valBit : 0));
        this.papi$visibleCacheMask = updated;
        return nonEmpty;
    }

    @Override
    public @Nullable Object papi$getClientRenderCache() {
        return this.papi$clientRenderCache;
    }

    @Override
    public void papi$setClientRenderCache(@Nullable Object cache) {
        this.papi$clientRenderCache = cache;
    }
}
