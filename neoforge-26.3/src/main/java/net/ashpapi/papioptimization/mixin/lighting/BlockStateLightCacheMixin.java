package net.ashpapi.papioptimization.mixin.lighting;

import net.ashpapi.papioptimization.access.PapiLightBlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1500)
public abstract class BlockStateLightCacheMixin implements PapiLightBlockState {

    @Shadow @Final private boolean canOcclude;
    @Shadow @Final private boolean useShapeForLightOcclusion;
    @Shadow @Final private int lightEmission;

    @Unique
    private int papi$lightFlags;

    @Inject(method = "initCache", at = @At("RETURN"))
    private void papi$initLightFlags(CallbackInfo ci) {
        int flags = 256;
        if (!this.canOcclude || !this.useShapeForLightOcclusion) {
            flags |= 1;
        }
        if (this.canOcclude) {
            flags |= 2;
        }
        if (this.useShapeForLightOcclusion) {
            flags |= 4;
        }
        flags |= (this.lightEmission & 15) << 4;
        this.papi$lightFlags = flags;
    }

    @Override
    public int papi$getLightFlags() {
        int flags = this.papi$lightFlags;
        if (flags != 0) {
            return flags;
        }
        int computed = 256;
        if (!this.canOcclude || !this.useShapeForLightOcclusion) {
            computed |= 1;
        }
        if (this.canOcclude) {
            computed |= 2;
        }
        if (this.useShapeForLightOcclusion) {
            computed |= 4;
        }
        computed |= (this.lightEmission & 15) << 4;
        this.papi$lightFlags = computed;
        return computed;
    }
}
