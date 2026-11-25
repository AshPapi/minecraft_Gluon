package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.NoOpBehaviorControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VillagerGoalPackages.class, priority = 1500)
public abstract class VillagerGoalPackagesFastMixin {

    @WrapOperation(
        method = "getCorePackage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/AcquirePoi;create(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;ZLjava/util/Optional;Ljava/util/function/BiPredicate;)Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"
        )
    )
    private static BehaviorControl<PathfinderMob> papi$skipAcquirePoiWhenNone(
            Predicate<Holder<PoiType>> acquirableType,
            MemoryModuleType<GlobalPos> existingAbsentMemory,
            MemoryModuleType<GlobalPos> acquiringMemory,
            boolean onlyIfAdult,
            Optional<Byte> entityEventId,
            BiPredicate<ServerLevel, BlockPos> validPredicate,
            Operation<BehaviorControl<PathfinderMob>> original
    ) {
        if (acquirableType == PoiType.NONE) {
            return NoOpBehaviorControl.instance();
        }
        return original.call(acquirableType, existingAbsentMemory, acquiringMemory, onlyIfAdult, entityEventId, validPredicate);
    }
}
