package net.ashpapi.papioptimization.mixin.entity;

import java.util.Optional;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.FastBlockSearchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.sensing.HoglinSpecificSensor;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HoglinSpecificSensor.class, priority = 1500)
public abstract class HoglinSpecificSensorMixin {

    @Unique
    private static final Predicate<BlockState> PAPI$HOGLIN_REPELLENT =
            state -> state.is(BlockTags.HOGLIN_REPELLENTS);

    @Inject(method = "findNearestRepellent", at = @At("HEAD"), cancellable = true)
    private void papi$fastFindNearestRepellent(
            ServerLevel level,
            Hoglin body,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        cir.setReturnValue(FastBlockSearchHelper.findNearestInManhattanBox(
                level, body.blockPosition(), 8, 4, 8, PAPI$HOGLIN_REPELLENT
        ));
    }
}
