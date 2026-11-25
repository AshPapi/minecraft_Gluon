package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Queue;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PacketProcessor.class, priority = 1500)
public abstract class PacketProcessorMixin {

    @Unique
    private Object papi$polled;

    @WrapOperation(
            method = "processQueuedPackets",
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z")
    )
    private boolean papi$pollOnceOnEmptyCheck(Queue<?> queue, Operation<Boolean> original) {
        Object next = queue.poll();
        this.papi$polled = next;
        return next == null;
    }

    @WrapOperation(
            method = "processQueuedPackets",
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;")
    )
    private Object papi$consumePolledPacket(Queue<?> queue, Operation<Object> original) {
        Object next = this.papi$polled;
        if (next != null) {
            this.papi$polled = null;
            return next;
        }
        return original.call(queue);
    }
}
