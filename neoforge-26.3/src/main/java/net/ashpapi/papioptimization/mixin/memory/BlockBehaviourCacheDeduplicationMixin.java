package net.ashpapi.papioptimization.mixin.memory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.core.PapiEnumCache;
import net.ashpapi.papioptimization.memory.PapiShapeCanonicalizer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase$Cache", priority = 1500)
public abstract class BlockBehaviourCacheDeduplicationMixin {

    @Shadow @Final @Mutable public VoxelShape collisionShape;
    @Shadow @Final @Mutable private boolean[] faceSturdy;

    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/SupportType;values()[Lnet/minecraft/world/level/block/SupportType;"
        ),
        require = 0
    )
    private SupportType[] papi$reuseSupportTypes(Operation<SupportType[]> original) {
        return PapiEnumCache.SUPPORT_TYPES;
    }

    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Direction$Axis;values()[Lnet/minecraft/core/Direction$Axis;"
        ),
        require = 0
    )
    private Direction.Axis[] papi$reuseAxes(Operation<Direction.Axis[]> original) {
        return PapiEnumCache.AXES;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$deduplicateCacheArrays(CallbackInfo ci) {
        this.collisionShape = PapiShapeCanonicalizer.internShape(this.collisionShape);
        this.faceSturdy = PapiShapeCanonicalizer.internFaceSturdy(this.faceSturdy);
    }
}
