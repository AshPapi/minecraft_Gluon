package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ListTag.class, priority = 1500)
public abstract class ListTagFastMixin {

    @Shadow
    @Final
    @Mutable
    private List<Tag> list;

    @Shadow
    abstract byte identifyRawElementType();

    @Shadow
    private static Tag wrapIfNeeded(byte elementType, Tag tag) {
        throw new AssertionError();
    }

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void papi$useFastListDefault(CallbackInfo ci) {
        this.list = new ObjectArrayList<>();
    }

    @WrapMethod(method = "copy()Lnet/minecraft/nbt/ListTag;")
    private ListTag papi$fastCopy(Operation<ListTag> original) {
        List<Tag> src = this.list;
        int size = src.size();
        if (size == 0) {
            return new ListTag();
        }
        ObjectArrayList<Tag> copyList = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            copyList.add(src.get(i).copy());
        }
        ListTag copy = new ListTag();
        ((ListTagFastMixin) (Object) copy).list = copyList;
        return copy;
    }

    @WrapMethod(method = "write")
    private void papi$fastWrite(DataOutput output, Operation<Void> original) throws IOException {
        byte type = this.identifyRawElementType();
        output.writeByte(type);
        List<Tag> src = this.list;
        int size = src.size();
        output.writeInt(size);
        for (int i = 0; i < size; i++) {
            wrapIfNeeded(type, src.get(i)).write(output);
        }
    }

    @WrapMethod(method = "identifyRawElementType")
    private byte papi$fastIdentifyRawElementType(Operation<Byte> original) {
        List<Tag> src = this.list;
        int size = src.size();
        if (size == 0) {
            return 0;
        }
        byte type = src.get(0).getId();
        for (int i = 1; i < size; i++) {
            byte id = src.get(i).getId();
            if (type == 0) {
                type = id;
            } else if (type != id) {
                return 10;
            }
        }
        return type;
    }

    @WrapMethod(method = "sizeInBytes")
    private int papi$fastSizeInBytes(Operation<Integer> original) {
        List<Tag> src = this.list;
        int size = src.size();
        int bytes = 36 + 4 * size;
        for (int i = 0; i < size; i++) {
            bytes += src.get(i).sizeInBytes();
        }
        return bytes;
    }
}
