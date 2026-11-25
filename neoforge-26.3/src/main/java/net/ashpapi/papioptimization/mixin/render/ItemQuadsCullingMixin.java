package net.ashpapi.papioptimization.mixin.render;

import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.client.culling.PapiCullableItemQuads;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ItemQuads.class, priority = 1500)
public abstract class ItemQuadsCullingMixin implements PapiCullableItemQuads {

    @Shadow
    @Final
    private List<BakedQuad> all;

    @Shadow
    @Final
    private List<BakedQuad> solid;

    @Shadow
    @Final
    private List<BakedQuad> translucent;

    @Unique
    private volatile ItemQuads @Nullable [] papi$filteredCache;

    @Override
    public ItemQuads papi$getFiltered(int dirMask6) {
        int mask = dirMask6 & 0x3F;
        if (mask == 0x3F || this.all.isEmpty()) {
            return (ItemQuads) (Object) this;
        }
        if (mask == 0) {
            return ItemQuads.EMPTY;
        }
        ItemQuads[] cache = this.papi$filteredCache;
        if (cache == null) {
            cache = new ItemQuads[64];
            this.papi$filteredCache = cache;
        }
        ItemQuads existing = cache[mask];
        if (existing != null) {
            return existing;
        }
        List<BakedQuad> fAll = papi$filterQuadList(this.all, mask);
        ItemQuads created;
        if (fAll.isEmpty()) {
            created = ItemQuads.EMPTY;
        } else {
            List<BakedQuad> fSolid = this.solid.isEmpty() ? List.of() : papi$filterQuadList(this.solid, mask);
            List<BakedQuad> fTrans = this.translucent.isEmpty() ? List.of() : papi$filterQuadList(this.translucent, mask);
            created = new ItemQuads(fAll, fSolid, fTrans);
        }
        cache[mask] = created;
        return created;
    }

    @Unique
    private static List<BakedQuad> papi$filterQuadList(List<BakedQuad> src, int mask) {
        int len = src.size();
        List<BakedQuad> dst = null;
        for (int i = 0; i < len; i++) {
            BakedQuad q = src.get(i);
            Direction d = q.direction();
            boolean keep = d == null || (mask & (1 << d.ordinal())) != 0;
            if (!keep) {
                if (dst == null) {
                    dst = new ArrayList<>(len - 1);
                    for (int j = 0; j < i; j++) {
                        dst.add(src.get(j));
                    }
                }
            } else if (dst != null) {
                dst.add(q);
            }
        }
        if (dst == null) {
            return src;
        }
        return dst.isEmpty() ? List.of() : List.copyOf(dst);
    }
}
