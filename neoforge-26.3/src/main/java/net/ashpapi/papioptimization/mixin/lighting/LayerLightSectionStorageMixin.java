package net.ashpapi.papioptimization.mixin.lighting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LayerLightSectionStorage.class, priority = 1500)
public abstract class LayerLightSectionStorageMixin {

    @WrapOperation(
        method = "setStoredLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;aroundAndAtBlockPos(JLit/unimi/dsi/fastutil/longs/LongConsumer;)V"
        )
    )
    private void papi$fastAroundAndAtBlockPos(long blockNode, LongConsumer consumer, Operation<Void> original) {
        int x = BlockPos.getX(blockNode) & 15;
        int y = BlockPos.getY(blockNode) & 15;
        int z = BlockPos.getZ(blockNode) & 15;
        if (Integer.compareUnsigned(x - 1, 14) < 0
                && Integer.compareUnsigned(y - 1, 14) < 0
                && Integer.compareUnsigned(z - 1, 14) < 0) {
            consumer.accept(SectionPos.blockToSection(blockNode));
        } else {
            original.call(blockNode, consumer);
        }
    }
}
