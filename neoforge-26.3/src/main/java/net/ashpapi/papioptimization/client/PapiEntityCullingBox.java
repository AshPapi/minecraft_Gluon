package net.ashpapi.papioptimization.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface PapiEntityCullingBox {
    boolean papi$isAffectedByCulling(Entity entity);

    AABB papi$getCullingBox(Entity entity, float partialTick);

    boolean papi$shouldShowName(Entity entity, double distanceToCameraSq);

    @Nullable Component papi$getNameTag(Entity entity);
}
