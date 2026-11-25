package net.ashpapi.papioptimization.mixin.lighting;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ThreadedLevelLightEngine.class, priority = 1500)
public abstract class ThreadedLevelLightEngineMixin extends LevelLightEngine {

    @Shadow @Final private ObjectList<Pair<?, Runnable>> lightTasks;
    @Shadow private int taskPerBatch;

    @Unique
    private final ObjectArrayList<Pair<?, Runnable>> papi$batchScratch = new ObjectArrayList<>(256);

    @Unique
    private boolean papi$inBatchRun;

    protected ThreadedLevelLightEngineMixin() {
        super(null, false, false);
    }

    @Inject(method = "runUpdate", at = @At("HEAD"), cancellable = true)
    private void papi$batchRunUpdate(CallbackInfo ci) {
        if (this.lightTasks.isEmpty()) {
            super.runLightUpdates();
            ci.cancel();
            return;
        }

        int totalSize = Math.min(this.lightTasks.size(), Math.max(1, this.taskPerBatch));
        boolean usingSharedScratch = !this.papi$inBatchRun;
        ObjectArrayList<Pair<?, Runnable>> batch;
        if (usingSharedScratch) {
            this.papi$inBatchRun = true;
            batch = this.papi$batchScratch;
            batch.clear();
        } else {
            batch = new ObjectArrayList<>(totalSize);
        }

        try {
            for (int i = 0; i < totalSize; i++) {
                batch.add(this.lightTasks.get(i));
            }
            if (this.lightTasks instanceof ObjectArrayList<Pair<?, Runnable>> arrayList) {
                arrayList.removeElements(0, totalSize);
            } else {
                this.lightTasks.subList(0, totalSize).clear();
            }

            try {
                for (int i = 0; i < totalSize; i++) {
                    Pair<?, Runnable> task = batch.get(i);
                    if (((Enum<?>) task.getFirst()).ordinal() == 0) {
                        task.getSecond().run();
                    }
                }
                super.runLightUpdates();
            } finally {
                for (int i = 0; i < totalSize; i++) {
                    Pair<?, Runnable> task = batch.get(i);
                    if (((Enum<?>) task.getFirst()).ordinal() == 1) {
                        task.getSecond().run();
                    }
                }
            }
        } finally {
            batch.clear();
            if (usingSharedScratch) {
                if (batch.elements().length > 2048) {
                    batch.trim(1024);
                }
                this.papi$inBatchRun = false;
            }
        }

        ci.cancel();
    }
}
