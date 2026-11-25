package net.ashpapi.papioptimization.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.ashpapi.papioptimization.startup.PapiZipPackIndex;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
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

@Mixin(value = FilePackResources.class, priority = 1500)
public abstract class FilePackResourcesMixin {

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private FilePackResources.SharedZipFileAccess zipFileAccess;

    @Shadow
    @Final
    private String prefix;

    @Unique
    private volatile @Nullable PapiZipPackIndex papi$zipIndex;

    @Unique
    private @Nullable PapiZipPackIndex papi$getOrBuildIndex(ZipFile zipFile) {
        PapiZipPackIndex idx = this.papi$zipIndex;
        if (idx != null) {
            return idx;
        }
        synchronized (this) {
            idx = this.papi$zipIndex;
            if (idx == null) {
                SharedZipFileAccessAccessor acc = (SharedZipFileAccessAccessor) this.zipFileAccess;
                idx = PapiZipPackIndex.build(zipFile, this.prefix, acc.papi$getFile(), LOGGER);
                this.papi$zipIndex = idx;
            }
            return idx;
        }
    }

    @WrapMethod(method = "getResource(Ljava/lang/String;)Lnet/minecraft/server/packs/resources/IoSupplier;")
    private @Nullable IoSupplier<InputStream> papi$fastGetResource(
            String resourcePath,
            Operation<IoSupplier<InputStream>> original
    ) {
        SharedZipFileAccessAccessor acc = (SharedZipFileAccessAccessor) this.zipFileAccess;
        ZipFile zipFile = acc.papi$getOrCreateZipFile();
        if (zipFile == null) {
            return null;
        }
        String fullPath = this.prefix.isEmpty() ? resourcePath : (this.prefix + "/" + resourcePath);
        PapiZipPackIndex idx = this.papi$getOrBuildIndex(zipFile);
        if (idx != null && !idx.containsFile(fullPath)) {
            return null;
        }
        ZipEntry entry = zipFile.getEntry(fullPath);
        return entry == null ? null : IoSupplier.create(zipFile, entry);
    }

    @WrapMethod(method = "getNamespaces(Lnet/minecraft/server/packs/PackType;)Ljava/util/Set;")
    private Set<String> papi$fastGetNamespaces(PackType type, Operation<Set<String>> original) {
        SharedZipFileAccessAccessor acc = (SharedZipFileAccessAccessor) this.zipFileAccess;
        ZipFile zipFile = acc.papi$getOrCreateZipFile();
        if (zipFile == null) {
            return Collections.emptySet();
        }
        PapiZipPackIndex idx = this.papi$getOrBuildIndex(zipFile);
        return idx != null ? idx.getNamespaces(type) : original.call(type);
    }

    @WrapMethod(method = "listResources")
    private void papi$fastListResources(
            PackType type,
            String namespace,
            String path,
            PackResources.ResourceOutput output,
            Operation<Void> original
    ) {
        SharedZipFileAccessAccessor acc = (SharedZipFileAccessAccessor) this.zipFileAccess;
        ZipFile zipFile = acc.papi$getOrCreateZipFile();
        if (zipFile == null) {
            return;
        }
        PapiZipPackIndex idx = this.papi$getOrBuildIndex(zipFile);
        if (idx == null) {
            original.call(type, namespace, path, output);
            return;
        }
        idx.listResources(zipFile, this.prefix, type, namespace, path, output, LOGGER);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void papi$clearIndexOnClose(CallbackInfo ci) {
        this.papi$zipIndex = null;
    }
}
