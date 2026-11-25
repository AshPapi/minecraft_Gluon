package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.ashpapi.papioptimization.access.PapiRenderBoxCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockEntity.class, priority = 1500)
public abstract class BlockEntityClientStateMixin implements PapiRenderBoxCache, PapiEbeDynamicTracker {

    @Unique
    private @Nullable AABB papi$cachedChestBox;

    @Unique
    private long papi$cachedBoxPos = Long.MIN_VALUE;

    @Unique
    private volatile boolean papi$ebeDynamic;

    @Unique
    private volatile boolean papi$ebeBaked;

    @Unique
    private int papi$ebeRebuildTick = Integer.MIN_VALUE / 2;

    @Override
    public AABB papi$getOrComputeChestBox(BlockPos pos) {
        long posLong = pos.asLong();
        if (posLong != this.papi$cachedBoxPos) {
            this.papi$cachedBoxPos = posLong;
            this.papi$cachedChestBox = null;
        } else if (this.papi$cachedChestBox != null) {
            return this.papi$cachedChestBox;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AABB box = new AABB(x - 1, y, z - 1, x + 2, y + 2, z + 2);
        this.papi$cachedChestBox = box;
        return box;
    }

    @Override
    public boolean papi$wasEbeDynamic() {
        return this.papi$ebeDynamic;
    }

    @Override
    public void papi$setEbeDynamic(boolean dynamic) {
        this.papi$ebeDynamic = dynamic;
    }

    @Override
    public int papi$getEbeRebuildTick() {
        return this.papi$ebeRebuildTick;
    }

    @Override
    public void papi$setEbeRebuildTick(int tick) {
        this.papi$ebeRebuildTick = tick;
    }

    @Override
    public boolean papi$isEbeBaked() {
        return this.papi$ebeBaked;
    }

    @Override
    public void papi$setEbeBaked(boolean baked) {
        this.papi$ebeBaked = baked;
    }
}
