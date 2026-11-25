package net.ashpapi.papioptimization.mixin.math;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Direction.class, priority = 1500)
public abstract class DirectionFastMixin {

    @Shadow
    @Final
    private Vec3i normal;

    @Shadow
    @Final
    private int oppositeIndex;

    @Shadow
    public static Direction from3DDataValue(int index) {
        throw new AssertionError();
    }

    @Shadow
    public static Direction[] values() {
        throw new AssertionError();
    }

    @Unique
    private static Direction[] PAPI$ALL;

    @Unique
    private int papi$stepX;

    @Unique
    private int papi$stepY;

    @Unique
    private int papi$stepZ;

    @Unique
    private Direction papi$opposite;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$cacheStepComponents(CallbackInfo ci) {
        this.papi$stepX = this.normal.getX();
        this.papi$stepY = this.normal.getY();
        this.papi$stepZ = this.normal.getZ();
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void papi$cacheOpposites(CallbackInfo ci) {
        Direction[] all = values();
        PAPI$ALL = all;
        for (int i = 0; i < all.length; i++) {
            Direction dir = all[i];
            DirectionFastMixin m = (DirectionFastMixin) (Object) dir;
            m.papi$opposite = from3DDataValue(m.oppositeIndex);
        }
    }

    @WrapMethod(method = "getStepX")
    private int papi$fastGetStepX(Operation<Integer> original) {
        return this.papi$stepX;
    }

    @WrapMethod(method = "getStepY")
    private int papi$fastGetStepY(Operation<Integer> original) {
        return this.papi$stepY;
    }

    @WrapMethod(method = "getStepZ")
    private int papi$fastGetStepZ(Operation<Integer> original) {
        return this.papi$stepZ;
    }

    @WrapMethod(method = "getOpposite")
    private Direction papi$fastGetOpposite(Operation<Direction> original) {
        Direction opp = this.papi$opposite;
        return opp != null ? opp : original.call();
    }

    @WrapMethod(method = "getRandom")
    private static Direction papi$fastGetRandom(RandomSource random, Operation<Direction> original) {
        Direction[] all = PAPI$ALL;
        if (all != null) {
            return all[random.nextInt(all.length)];
        }
        return original.call(random);
    }
}
