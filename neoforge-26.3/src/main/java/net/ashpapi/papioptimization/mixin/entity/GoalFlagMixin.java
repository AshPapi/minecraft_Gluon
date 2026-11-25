package net.ashpapi.papioptimization.mixin.entity;

import java.util.EnumSet;
import net.ashpapi.papioptimization.access.WrappedGoalFlagMask;
import net.ashpapi.papioptimization.ai.PapiGoalFlagClassCache;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Goal.class, priority = 1500)
public abstract class GoalFlagMixin implements WrappedGoalFlagMask {

    @Shadow
    @Final
    private EnumSet<Goal.Flag> flags;

    @Shadow
    public abstract EnumSet<Goal.Flag> getFlags();

    @Unique
    private long papi$flagMask = 0L;

    @Unique
    private final boolean papi$standardFlags = PapiGoalFlagClassCache.hasStandardGetFlags(this.getClass());

    @Inject(method = "setFlags", at = @At("RETURN"))
    private void papi$onSetFlags(EnumSet<Goal.Flag> requiredControlFlags, CallbackInfo ci) {
        long mask = 0L;
        for (Goal.Flag flag : this.flags) {
            int ord = flag.ordinal();
            if (ord >= 0 && ord < 64) {
                mask |= (1L << ord);
            }
        }
        this.papi$flagMask = mask;
    }

    @Override
    public long papi$getFlagMask() {
        if (this.papi$standardFlags) {
            return this.papi$flagMask;
        }
        long mask = 0L;
        for (Goal.Flag flag : this.getFlags()) {
            int ord = flag.ordinal();
            if (ord >= 0 && ord < 64) {
                mask |= (1L << ord);
            }
        }
        return mask;
    }
}
