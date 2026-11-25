package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.SpatialEntityQueryHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GolemRandomStrollInVillageGoal.class, priority = 1500)
public abstract class GolemRandomStrollInVillageGoalMixin {

    @WrapOperation(
        method = "getPositionTowardsVillagerWhoWantsGolem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private List<Villager> papi$useVillagerClassBucket(
            ServerLevel level,
            EntityTypeTest<Entity, Villager> typeTest,
            AABB box,
            Predicate<? super Villager> predicate,
            Operation<List<Villager>> original
    ) {
        if (typeTest == EntityTypes.VILLAGER) {
            return original.call(level, SpatialEntityQueryHelper.EXACT_VILLAGER_TEST, box, predicate);
        }
        return original.call(level, typeTest, box, predicate);
    }
}
