package net.ashpapi.papioptimization.mixin.network;

import javax.crypto.Cipher;
import net.minecraft.network.CipherBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CipherBase.class, priority = 1500)
public interface CipherBaseAccessor {

    @Accessor("cipher")
    Cipher papi$getCipher();
}
