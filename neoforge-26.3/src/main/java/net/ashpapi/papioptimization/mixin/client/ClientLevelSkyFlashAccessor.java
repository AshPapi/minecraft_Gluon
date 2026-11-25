package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientLevel.class, priority = 1500)
public interface ClientLevelSkyFlashAccessor {

    @Accessor("skyFlashTime")
    int papi$getSkyFlashTime();
}
