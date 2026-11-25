package net.ashpapi.papioptimization.mixin.entity;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Entity.class, priority = 1500)
public abstract class EntityPassengersFastMixin {

    @Shadow
    private ImmutableList<Entity> passengers;

    @WrapMethod(method = "getIndirectPassengers")
    private Iterable<Entity> papi$fastGetIndirectPassengers(Operation<Iterable<Entity>> original) {
        ImmutableList<Entity> direct = this.passengers;
        int size = direct.size();
        if (size == 0) {
            return List.of();
        }
        if (size == 1) {
            Entity p0 = direct.get(0);
            if (((EntityPassengersFastMixin) (Object) p0).passengers.isEmpty()) {
                return direct;
            }
        }
        ObjectArrayList<Entity> out = new ObjectArrayList<>(size + 2);
        for (int i = 0; i < size; i++) {
            papi$collectPreOrder(direct.get(i), out);
        }
        return out;
    }

    @WrapMethod(method = "getSelfAndPassengers")
    private Stream<Entity> papi$fastGetSelfAndPassengers(Operation<Stream<Entity>> original) {
        Entity self = (Entity) (Object) this;
        ImmutableList<Entity> direct = this.passengers;
        int size = direct.size();
        if (size == 0) {
            return Stream.of(self);
        }
        if (size == 1) {
            Entity p0 = direct.get(0);
            if (((EntityPassengersFastMixin) (Object) p0).passengers.isEmpty()) {
                return Stream.of(self, p0);
            }
        }
        ObjectArrayList<Entity> out = new ObjectArrayList<>(size + 2);
        papi$collectPreOrder(self, out);
        return out.stream();
    }

    @WrapMethod(method = "getPassengersAndSelf")
    private Stream<Entity> papi$fastGetPassengersAndSelf(Operation<Stream<Entity>> original) {
        Entity self = (Entity) (Object) this;
        ImmutableList<Entity> direct = this.passengers;
        int size = direct.size();
        if (size == 0) {
            return Stream.of(self);
        }
        if (size == 1) {
            Entity p0 = direct.get(0);
            if (((EntityPassengersFastMixin) (Object) p0).passengers.isEmpty()) {
                return Stream.of(p0, self);
            }
        }
        ObjectArrayList<Entity> out = new ObjectArrayList<>(size + 2);
        papi$collectPostOrder(self, out);
        return out.stream();
    }

    @Unique
    private static void papi$collectPreOrder(Entity current, ObjectArrayList<Entity> out) {
        out.add(current);
        ImmutableList<Entity> children = ((EntityPassengersFastMixin) (Object) current).passengers;
        for (int i = 0, len = children.size(); i < len; i++) {
            papi$collectPreOrder(children.get(i), out);
        }
    }

    @Unique
    private static void papi$collectPostOrder(Entity current, ObjectArrayList<Entity> out) {
        ImmutableList<Entity> children = ((EntityPassengersFastMixin) (Object) current).passengers;
        for (int i = 0, len = children.size(); i < len; i++) {
            papi$collectPostOrder(children.get(i), out);
        }
        out.add(current);
    }
}
