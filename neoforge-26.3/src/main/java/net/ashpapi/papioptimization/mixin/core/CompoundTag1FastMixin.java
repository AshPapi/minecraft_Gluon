package net.ashpapi.papioptimization.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.DataInput;
import java.io.IOException;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.TagTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.nbt.CompoundTag$1", priority = 1500)
public abstract class CompoundTag1FastMixin {

    @Shadow
    private static byte readNamedTagType(DataInput input, NbtAccounter accounter) throws IOException {
        throw new AssertionError();
    }

    @WrapMethod(method = "loadCompound")
    private static CompoundTag papi$fastLoadCompound(
            DataInput input,
            NbtAccounter accounter,
            Operation<CompoundTag> original
    ) throws IOException {
        accounter.accountBytes(48L);
        CompoundTag compound = new CompoundTag();
        byte type;
        while ((type = readNamedTagType(input, accounter)) != 0) {
            String key = accounter.readUTF(input.readUTF());
            accounter.accountBytes(4L);
            Tag value = papi$readNamedTagData(TagTypes.getType(type), key, input, accounter);
            if (compound.put(key, value) == null) {
                accounter.accountBytes(36L);
            }
        }
        return compound;
    }

    @Unique
    private static Tag papi$readNamedTagData(
            TagType<?> type,
            String key,
            DataInput input,
            NbtAccounter accounter
    ) {
        try {
            return type.load(input, accounter);
        } catch (IOException e) {
            CrashReport report = CrashReport.forThrowable(e, "Loading NBT data");
            CrashReportCategory category = report.addCategory("NBT Tag");
            category.setDetail("Tag name", key);
            category.setDetail("Tag type", type.getName());
            throw new ReportedNbtException(report);
        }
    }
}
