package net.ashpapi.papioptimization.ai;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class NoOpBehaviorControl<E extends LivingEntity> implements BehaviorControl<E> {

    private static final NoOpBehaviorControl<LivingEntity> INSTANCE = new NoOpBehaviorControl<>();

    private NoOpBehaviorControl() {}

    @SuppressWarnings("unchecked")
    public static <E extends LivingEntity> BehaviorControl<E> instance() {
        return (BehaviorControl<E>) INSTANCE;
    }

    @Override
    public Behavior.Status getStatus() {
        return Behavior.Status.STOPPED;
    }

    @Override
    public Set<MemoryModuleType<?>> getRequiredMemories() {
        return Set.of();
    }

    @Override
    public boolean tryStart(ServerLevel level, E body, long timestamp) {
        return false;
    }

    @Override
    public void tickOrStop(ServerLevel level, E body, long timestamp) {
    }

    @Override
    public void doStop(ServerLevel level, E body, long timestamp) {
    }

    @Override
    public String debugString() {
        return "NoOpBehaviorControl";
    }
}
