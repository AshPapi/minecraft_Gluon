package net.ashpapi.papioptimization.mixin.client;

import com.mojang.blaze3d.systems.ScissorState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ScissorState.class, priority = 1500)
public abstract class ScissorStateMixin {

    @Shadow
    private boolean enabled;

    @Shadow
    private int x;

    @Shadow
    private int y;

    @Shadow
    private int width;

    @Shadow
    private int height;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ScissorState other)) {
            return false;
        }
        if (!this.enabled) {
            return !other.enabled();
        }
        return other.enabled()
                && this.x == other.x()
                && this.y == other.y()
                && this.width == other.width()
                && this.height == other.height();
    }

    @Override
    public int hashCode() {
        if (!this.enabled) {
            return 1237;
        }
        int h = 1231;
        h = 31 * h + this.x;
        h = 31 * h + this.y;
        h = 31 * h + this.width;
        h = 31 * h + this.height;
        return h;
    }
}
