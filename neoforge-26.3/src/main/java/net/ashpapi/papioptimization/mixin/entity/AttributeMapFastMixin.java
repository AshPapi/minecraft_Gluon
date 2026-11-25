package net.ashpapi.papioptimization.mixin.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AttributeMap.class, priority = 1500)
public abstract class AttributeMapFastMixin {

    @Shadow @Final @Mutable
    private Map<Holder<Attribute>, AttributeInstance> attributes;

    @Shadow @Final @Mutable
    private Set<AttributeInstance> attributesToSync;

    @Shadow @Final @Mutable
    private Set<AttributeInstance> attributesToUpdate;

    @Shadow @Final
    private AttributeSupplier supplier;

    @Shadow
    private void onAttributeModified(AttributeInstance instance) {}

    @Unique
    private Consumer<AttributeInstance> papi$onDirtyCallback;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$useReferenceCollections(AttributeSupplier supplier, CallbackInfo ci) {
        if (!(this.attributes instanceof Reference2ObjectOpenHashMap)) {
            this.attributes = this.attributes.isEmpty()
                    ? new Reference2ObjectOpenHashMap<>(4)
                    : new Reference2ObjectOpenHashMap<>(this.attributes);
        }
        if (!(this.attributesToSync instanceof ReferenceOpenHashSet)) {
            this.attributesToSync = this.attributesToSync.isEmpty()
                    ? new ReferenceOpenHashSet<>(0)
                    : new ReferenceOpenHashSet<>(this.attributesToSync);
        }
        if (!(this.attributesToUpdate instanceof ReferenceOpenHashSet)) {
            this.attributesToUpdate = this.attributesToUpdate.isEmpty()
                    ? new ReferenceOpenHashSet<>(0)
                    : new ReferenceOpenHashSet<>(this.attributesToUpdate);
        }
        this.papi$onDirtyCallback = this::onAttributeModified;
    }

    @Inject(method = "getInstance", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetInstance(Holder<Attribute> attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        AttributeInstance inst = this.attributes.get(attribute);
        if (inst == null) {
            Consumer<AttributeInstance> cb = this.papi$onDirtyCallback;
            if (cb == null) {
                cb = this::onAttributeModified;
                this.papi$onDirtyCallback = cb;
            }
            inst = this.supplier.createInstance(cb, attribute);
            if (inst != null) {
                this.attributes.put(attribute, inst);
            }
        }
        cir.setReturnValue(inst);
    }

    @Inject(method = "getSyncableAttributes", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetSyncableAttributes(CallbackInfoReturnable<Collection<AttributeInstance>> cir) {
        Collection<AttributeInstance> vals = this.attributes.values();
        ObjectArrayList<AttributeInstance> result = new ObjectArrayList<>(vals.size());
        for (AttributeInstance inst : vals) {
            if (inst.getAttribute().value().isClientSyncable()) {
                result.add(inst);
            }
        }
        cir.setReturnValue(result);
    }
}
