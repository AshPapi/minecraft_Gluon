package net.ashpapi.papioptimization.physics;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class PapiFluidSectionCursor {

    public static final ThreadLocal<PapiFluidSectionCursor> LOCAL = ThreadLocal.withInitial(PapiFluidSectionCursor::new);

    public Level level;
    public int chunkX = Integer.MIN_VALUE;
    public int chunkZ = Integer.MIN_VALUE;
    public int secY = Integer.MIN_VALUE;
    public LevelChunk chunk;
    public LevelChunkSection section;

    public void reset() {
        this.level = null;
        this.chunk = null;
        this.section = null;
        this.chunkX = Integer.MIN_VALUE;
        this.chunkZ = Integer.MIN_VALUE;
        this.secY = Integer.MIN_VALUE;
    }
}
