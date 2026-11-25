package net.ashpapi.papioptimization.mixin.client;

import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Level.class, priority = 1500)
public interface LevelLeakAccessor {

    @Accessor("blockEntityTickers")
    List<TickingBlockEntity> papi$getBlockEntityTickers();

    @Accessor("pendingBlockEntityTickers")
    List<TickingBlockEntity> papi$getPendingBlockEntityTickers();
}
