package net.ashpapi.papioptimization.mixin.startup;

import java.io.File;
import java.util.zip.ZipFile;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess", priority = 1500)
public interface SharedZipFileAccessAccessor {

    @Invoker("getOrCreateZipFile")
    @Nullable ZipFile papi$getOrCreateZipFile();

    @Accessor("file")
    File papi$getFile();
}
