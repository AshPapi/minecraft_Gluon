package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import net.ashpapi.papioptimization.startup.PapiPathPackIndex;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathPackResources.class, priority = 1500)
public abstract class PathPackResourcesMixin {

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private Path root;

    @Unique
    private volatile @Nullable PapiPathPackIndex papi$clientIndex;

    @Unique
    private volatile @Nullable PapiPathPackIndex papi$serverIndex;

    @Unique
    private @Nullable PapiPathPackIndex papi$getOrBuildIndex(PackType type) {
        if (type == PackType.CLIENT_RESOURCES) {
            PapiPathPackIndex idx = this.papi$clientIndex;
            if (idx != null) {
                return idx;
            }
            synchronized (this) {
                idx = this.papi$clientIndex;
                if (idx == null) {
                    idx = PapiPathPackIndex.buildForType(this.root, type, LOGGER);
                    this.papi$clientIndex = idx;
                }
                return idx;
            }
        }
        if (type == PackType.SERVER_DATA) {
            PapiPathPackIndex idx = this.papi$serverIndex;
            if (idx != null) {
                return idx;
            }
            synchronized (this) {
                idx = this.papi$serverIndex;
                if (idx == null) {
                    idx = PapiPathPackIndex.buildForType(this.root, type, LOGGER);
                    this.papi$serverIndex = idx;
                }
                return idx;
            }
        }
        return null;
    }

    @WrapMethod(method = "getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/server/packs/resources/IoSupplier;")
    private @Nullable IoSupplier<InputStream> papi$fastGetResource(
            PackType type,
            Identifier location,
            Operation<IoSupplier<InputStream>> original
    ) {
        PapiPathPackIndex idx = this.papi$getOrBuildIndex(type);
        if (idx != null && !idx.mayContain(location.getNamespace(), location.getPath())) {
            return null;
        }
        return original.call(type, location);
    }

    @WrapMethod(method = "getNamespaces(Lnet/minecraft/server/packs/PackType;)Ljava/util/Set;")
    private Set<String> papi$fastGetNamespaces(PackType type, Operation<Set<String>> original) {
        PapiPathPackIndex idx = this.papi$getOrBuildIndex(type);
        return idx != null ? idx.getNamespaces() : original.call(type);
    }

    @WrapMethod(method = "listResources(Lnet/minecraft/server/packs/PackType;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V")
    private void papi$fastListResources(
            PackType type,
            String namespace,
            String path,
            PackResources.ResourceOutput output,
            Operation<Void> original
    ) {
        PapiPathPackIndex idx = this.papi$getOrBuildIndex(type);
        if (idx != null && idx.listResources(namespace, path, output, LOGGER)) {
            return;
        }
        original.call(type, namespace, path, output);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void papi$clearIndexOnClose(CallbackInfo ci) {
        this.papi$clientIndex = null;
        this.papi$serverIndex = null;
    }
}
