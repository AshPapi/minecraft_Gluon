package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BlockColors.class, priority = 1500)
public abstract class BlockColorsSafetyMixin {

    @Unique
    private final ReentrantLock papi$registerLock = new ReentrantLock();

    @WrapMethod(method = "register")
    private void papi$threadSafeRegister(
            List<BlockTintSource> tintSources,
            Block[] blocks,
            Operation<Void> original
    ) {
        if (!PapiConfig.modernFix()) {
            original.call(tintSources, blocks);
            return;
        }
        this.papi$registerLock.lock();
        try {
            original.call(tintSources, blocks);
        } finally {
            this.papi$registerLock.unlock();
        }
    }
}
