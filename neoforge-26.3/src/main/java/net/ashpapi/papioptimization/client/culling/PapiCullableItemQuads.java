package net.ashpapi.papioptimization.client.culling;

import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PapiCullableItemQuads {

    default ItemQuads papi$getFiltered(int dirMask6) {
        return (ItemQuads) (Object) this;
    }
}
