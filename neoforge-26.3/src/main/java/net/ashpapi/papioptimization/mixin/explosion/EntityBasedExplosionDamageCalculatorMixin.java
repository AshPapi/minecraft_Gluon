package net.ashpapi.papioptimization.mixin.explosion;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = EntityBasedExplosionDamageCalculator.class, priority = 1500)
public abstract class EntityBasedExplosionDamageCalculatorMixin extends ExplosionDamageCalculator {

    @Shadow
    @Final
    private Entity source;

    @WrapMethod(method = "getBlockExplosionResistance")
    private Optional<Float> papi$streamlessResistance(
            Explosion explosion,
            BlockGetter reader,
            BlockPos pos,
            BlockState state,
            FluidState fluid,
            Operation<Optional<Float>> original
    ) {
        Optional<Float> base = super.getBlockExplosionResistance(explosion, reader, pos, state, fluid);
        if (base.isEmpty()) {
            return base;
        }
        float baseVal = base.get();
        float adjusted = this.source.getBlockExplosionResistance(explosion, reader, pos, state, fluid, baseVal);
        return adjusted == baseVal ? base : Optional.of(adjusted);
    }
}
