package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.ashpapi.papioptimization.worldgen.PapiStructureEpoch;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StructureTemplateManager.class, priority = 1500)
public abstract class StructureTemplateManagerEpochMixin {

    @Shadow
    @Final
    private Map<Identifier, Optional<StructureTemplate>> structureRepository;

    @Shadow
    private Optional<StructureTemplate> tryLoad(Identifier id) {
        throw new AssertionError();
    }

    @Unique
    private static final Object PAPI$EMPTY_TEMPLATE_MARKER = new Object();

    @Unique
    private final Map<Identifier, SoftReference<Object>> papi$softTemplates = new ConcurrentHashMap<>();

    @WrapMethod(method = "get")
    private Optional<StructureTemplate> papi$nonBlockingSoftGet(
            Identifier id,
            Operation<Optional<StructureTemplate>> original
    ) {
        Optional<StructureTemplate> explicit = this.structureRepository.get(id);
        if (explicit != null) {
            return explicit;
        }
        SoftReference<Object> softRef = this.papi$softTemplates.get(id);
        if (softRef != null) {
            Object cached = softRef.get();
            if (cached == PAPI$EMPTY_TEMPLATE_MARKER) {
                return Optional.empty();
            }
            if (cached instanceof StructureTemplate tpl) {
                return Optional.of(tpl);
            }
        }
        Optional<StructureTemplate> loaded = this.tryLoad(id);
        Object valueToStore = loaded.isPresent() ? loaded.get() : PAPI$EMPTY_TEMPLATE_MARKER;
        this.papi$softTemplates.put(id, new SoftReference<>(valueToStore));
        return loaded;
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void papi$removeSoftTemplate(Identifier id, CallbackInfo ci) {
        this.papi$softTemplates.remove(id);
    }

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void papi$bumpStructureEpochOnReload(ResourceManager resourceManager, CallbackInfo ci) {
        this.papi$softTemplates.clear();
        PapiStructureEpoch.bump();
    }
}
