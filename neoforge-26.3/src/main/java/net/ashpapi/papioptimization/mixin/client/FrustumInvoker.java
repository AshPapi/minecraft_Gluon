package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@OnlyIn(Dist.CLIENT)
@Mixin(Frustum.class)
public interface FrustumInvoker {

    @Invoker("cubeInFrustum")
    int papi$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
