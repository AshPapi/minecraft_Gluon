package net.ashpapi.papioptimization.mixin.memory;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.ashpapi.papioptimization.memory.PapiCompactThreadingDetector;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PalettedContainer.class, priority = 1500)
public abstract class PalettedContainerThreadingMixin {

    @Unique
    private static final @Nullable VarHandle PAPI$LOCK_STATE_HANDLE = papi$resolveLockHandle();

    @Shadow
    @Final
    private @Nullable ThreadingDetector threadingDetector;

    @Unique
    @SuppressWarnings("unused")
    private volatile byte papi$lockState;

    @Unique
    private static @Nullable VarHandle papi$resolveLockHandle() {
        try {
            return MethodHandles.lookup().findVarHandle(PalettedContainer.class, "papi$lockState", byte.class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @WrapOperation(
        method = "<init>*",
        at = @At(
            value = "NEW",
            target = "(Ljava/lang/String;)Lnet/minecraft/util/ThreadingDetector;"
        )
    )
    private static @Nullable ThreadingDetector papi$suppressThreadingDetectorAllocation(
            String name,
            Operation<ThreadingDetector> original
    ) {
        if (PAPI$LOCK_STATE_HANDLE == null) {
            return original.call(name);
        }
        return null;
    }

    @WrapMethod(method = "acquire")
    private void papi$fastAcquire(Operation<Void> original) {
        if (this.threadingDetector != null || PAPI$LOCK_STATE_HANDLE == null) {
            original.call();
            return;
        }
        if (!PAPI$LOCK_STATE_HANDLE.compareAndSet(this, PapiCompactThreadingDetector.UNLOCKED, PapiCompactThreadingDetector.LOCKED)) {
            PapiCompactThreadingDetector.handleAcquireContention(this, PAPI$LOCK_STATE_HANDLE, "PalettedContainer");
        }
    }

    @WrapMethod(method = "release")
    private void papi$fastRelease(Operation<Void> original) {
        if (this.threadingDetector != null || PAPI$LOCK_STATE_HANDLE == null) {
            original.call();
            return;
        }
        if (!PAPI$LOCK_STATE_HANDLE.compareAndSet(this, PapiCompactThreadingDetector.LOCKED, PapiCompactThreadingDetector.UNLOCKED)) {
            PapiCompactThreadingDetector.handleReleaseContention(this, PAPI$LOCK_STATE_HANDLE, "PalettedContainer");
        }
    }
}
