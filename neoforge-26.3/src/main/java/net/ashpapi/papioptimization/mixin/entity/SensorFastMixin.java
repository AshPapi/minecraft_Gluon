package net.ashpapi.papioptimization.mixin.entity;

import net.ashpapi.papioptimization.ai.PapiSleepableSensor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Sensor.class, priority = 1500)
public abstract class SensorFastMixin<E extends LivingEntity> implements PapiSleepableSensor {

    @Unique
    private static final long PAPI$SLEEP_OFFSET = 0x3FFFFFFFFFFFFFFFL;

    @Shadow
    private long timeToTick;

    @Unique
    private boolean papi$sleeping;

    @Override
    public boolean papi$isSensorSleeping() {
        return this.papi$sleeping;
    }

    @Override
    public void papi$setSensorSleeping(boolean sleeping) {
        if (sleeping) {
            if (!this.papi$sleeping) {
                this.papi$sleeping = true;
                if (this.timeToTick <= Long.MAX_VALUE - PAPI$SLEEP_OFFSET) {
                    this.timeToTick += PAPI$SLEEP_OFFSET;
                } else {
                    this.timeToTick = Long.MAX_VALUE;
                }
            }
        } else if (this.papi$sleeping) {
            this.papi$sleeping = false;
            if (this.timeToTick >= PAPI$SLEEP_OFFSET) {
                this.timeToTick -= PAPI$SLEEP_OFFSET;
            } else {
                this.timeToTick = 0L;
            }
        }
    }
}
