package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.access.PapiClientLivingTick;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class ClientLivingEntityTickMixin extends Entity implements PapiClientLivingTick {

    @Shadow
    @Final
    private LivingEntity.SwingState swingState;

    @Shadow
    @Final
    public WalkAnimationState walkAnimation;

    @Shadow
    public float yBodyRot;

    @Shadow
    public float yBodyRotO;

    @Shadow
    public float yHeadRot;

    @Shadow
    public float yHeadRotO;

    @Shadow
    protected double lerpYHeadRot;

    @Shadow
    protected int lerpHeadSteps;

    @Shadow
    protected abstract void lerpHeadRotationStep(int steps, double targetYaw);

    @Shadow
    protected abstract float getMaxHeadRotationRelativeToBody();

    @Shadow
    private void refreshDirtyAttributes() {
    }

    @Shadow
    private void updateSwimAmount() {
    }

    @Shadow
    public abstract void calculateEntityAnimation(boolean includeHeight);

    protected ClientLivingEntityTickMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void papi$clientMinimalLivingStep() {
        this.swingState.tick();
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
        this.updateSwimAmount();
        this.refreshDirtyAttributes();
        if (this.lerpHeadSteps > 0) {
            this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
            this.lerpHeadSteps--;
        }
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        if (dx * dx + dz * dz > 1.0E-6) {
            this.yBodyRot = this.getYRot();
            this.calculateEntityAnimation(false);
        } else {
            this.yBodyRot = Mth.rotateIfNecessary(this.yBodyRot, this.yHeadRot, this.getMaxHeadRotationRelativeToBody());
            if (this.walkAnimation.isMoving()) {
                this.calculateEntityAnimation(false);
            }
        }
        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }
        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }
        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }
        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }
        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }
        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }
        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }
        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }
    }
}
