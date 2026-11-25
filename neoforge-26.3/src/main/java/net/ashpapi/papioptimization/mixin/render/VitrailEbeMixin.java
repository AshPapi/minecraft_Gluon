package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.client.ebe.PapiSodiumEbeBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", priority = 1500, remap = false)
public abstract class VitrailEbeMixin {

    @WrapMethod(
        method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
        remap = false,
        require = 0
    )
    @Coerce
    private Object papi$wrapVitrailEbeTask(
            @Coerce Object buildContext,
            @Coerce Object cancellationToken,
            Operation<Object> original
    ) {
        PapiSodiumEbeBridge.beginMeshTask("vitrail");
        Object result = null;
        try {
            result = original.call(buildContext, cancellationToken);
            return result;
        } finally {
            PapiSodiumEbeBridge.finishMeshTask(result);
        }
    }

    @Inject(
        method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"
        ),
        remap = false,
        require = 0
    )
    private void papi$bakeStaticBlockEntityIntoVitrail(
            @Coerce Object buildContext,
            @Coerce Object cancellationToken,
            CallbackInfoReturnable<Object> cir,
            @Local(ordinal = 0) BlockPos.MutableBlockPos blockPos,
            @Local(ordinal = 1) BlockPos.MutableBlockPos modelOffset,
            @Local BlockState blockState
    ) {
        PapiSodiumEbeBridge.bakeIntoSodium(buildContext, blockPos, modelOffset, blockState);
    }
}
