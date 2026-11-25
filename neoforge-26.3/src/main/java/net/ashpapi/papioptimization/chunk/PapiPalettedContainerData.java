package net.ashpapi.papioptimization.chunk;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;

public final class PapiPalettedContainerData {

    public static final @Nullable VarHandle DATA_HANDLE;
    public static final @Nullable MethodHandle CREATE_OR_REUSE_DATA_HANDLE;

    static {
        VarHandle dataVh = null;
        MethodHandle createMh = null;
        try {
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data");
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PalettedContainer.class, MethodHandles.lookup());
            dataVh = lookup.findVarHandle(PalettedContainer.class, "data", dataClass);
            createMh = lookup.findVirtual(
                    PalettedContainer.class,
                    "createOrReuseData",
                    MethodType.methodType(dataClass, dataClass, int.class)
            );
        } catch (ReflectiveOperationException ignored) {
        }
        DATA_HANDLE = dataVh;
        CREATE_OR_REUSE_DATA_HANDLE = createMh;
    }

    private PapiPalettedContainerData() {
    }
}
