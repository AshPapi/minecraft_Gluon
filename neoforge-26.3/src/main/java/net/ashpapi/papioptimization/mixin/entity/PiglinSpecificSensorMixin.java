package net.ashpapi.papioptimization.mixin.entity;

import java.util.Optional;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.FastBlockSearchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PiglinSpecificSensor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PiglinSpecificSensor.class, priority = 1500)
public abstract class PiglinSpecificSensorMixin {

    @Unique
    private static final Predicate<BlockState> PAPI$PIGLIN_REPELLENT = state -> {
        if (!state.is(BlockTags.PIGLIN_REPELLENTS)) {
            return false;
        }
        return !state.is(Blocks.SOUL_CAMPFIRE) || CampfireBlock.isLitCampfire(state);
    };

    @Inject(method = "findNearestRepellent", at = @At("HEAD"), cancellable = true)
    private static void papi$fastFindNearestRepellent(
            ServerLevel level,
            LivingEntity body,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        cir.setReturnValue(FastBlockSearchHelper.findNearestInManhattanBox(
                level, body.blockPosition(), 8, 4, 8, PAPI$PIGLIN_REPELLENT
        ));
    }
}
