package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.math.PapiUnsyncedRandomSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {Entity.class, Level.class}, priority = 1500)
public abstract class UnsyncedRandomInitMixin {

    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"
        ),
        require = 0
    )
    private RandomSource papi$useUnsyncedRandom(Operation<RandomSource> original) {
        return new PapiUnsyncedRandomSource();
    }
}
