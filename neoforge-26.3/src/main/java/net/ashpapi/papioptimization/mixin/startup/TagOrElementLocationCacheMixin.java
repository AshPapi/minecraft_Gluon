package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ExtraCodecs.TagOrElementLocation.class, priority = 1500)
public abstract class TagOrElementLocationCacheMixin {

    @Shadow @Final private Identifier id;
    @Shadow @Final private boolean tag;

    @Unique
    private String papi$cachedDecoratedId;

    @WrapMethod(method = "decoratedId")
    private String papi$fastDecoratedId(Operation<String> original) {
        String s = this.papi$cachedDecoratedId;
        if (s == null) {
            s = this.tag ? "#" + this.id : this.id.toString();
            this.papi$cachedDecoratedId = s;
        }
        return s;
    }
}
