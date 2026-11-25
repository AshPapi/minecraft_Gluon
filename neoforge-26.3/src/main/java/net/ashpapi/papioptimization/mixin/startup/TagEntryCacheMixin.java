package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TagEntry.class, priority = 1500)
public abstract class TagEntryCacheMixin {

    @Shadow @Final private Identifier id;
    @Shadow @Final private boolean tag;

    @Unique
    private ExtraCodecs.TagOrElementLocation papi$cachedLocation;

    @WrapMethod(method = "elementOrTag")
    private ExtraCodecs.TagOrElementLocation papi$fastElementOrTag(
            Operation<ExtraCodecs.TagOrElementLocation> original
    ) {
        ExtraCodecs.TagOrElementLocation loc = this.papi$cachedLocation;
        if (loc == null) {
            loc = new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
            this.papi$cachedLocation = loc;
        }
        return loc;
    }
}
