package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ByteBufferBuilder.class, priority = 1500)
public abstract class ByteBufferBuilderMixin {

    @Shadow private long pointer;
    @Shadow private long capacity;
    @Shadow private long writeOffset;
    @Shadow private void ensureCapacity(long requiredCapacity) {}
    @Shadow private void checkOpen() {}

    @WrapMethod(method = "reserve")
    private long papi$fastReserve(int bytes, Operation<Long> original) {
        this.checkOpen();
        if (bytes < 0) {
            return original.call(bytes);
        }
        long old = this.writeOffset;
        long next = old + (long) bytes;
        if (next < 0L) {
            return original.call(bytes);
        }
        if (next > this.capacity) {
            this.ensureCapacity(next);
        }
        this.writeOffset = next;
        return this.pointer + old;
    }
}
