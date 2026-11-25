package net.ashpapi.papioptimization.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collections;
import java.util.Map;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChunkAccess.class, priority = 1500)
public abstract class ChunkAccessReferencesMixin {

    @Shadow
    @Final
    private Map<Structure, LongSet> structureReferences;

    @Unique
    private Map<Structure, LongSet> papi$unmodifiableReferences;

    @WrapMethod(method = "getAllReferences")
    private Map<Structure, LongSet> papi$fastGetAllReferences(Operation<Map<Structure, LongSet>> original) {
        if (this.structureReferences.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Structure, LongSet> cached = this.papi$unmodifiableReferences;
        if (cached == null) {
            cached = Collections.unmodifiableMap(this.structureReferences);
            this.papi$unmodifiableReferences = cached;
        }
        return cached;
    }
}
