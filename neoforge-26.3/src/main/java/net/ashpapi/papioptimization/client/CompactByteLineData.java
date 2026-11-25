package net.ashpapi.papioptimization.client;

import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record CompactByteLineData(long low, long high) implements UnihexProvider.LineData {

    @Override
    public int line(int index) {
        long word = index < 8 ? this.low : this.high;
        return ((int) (word >>> ((index & 7) << 3))) << 24;
    }

    @Override
    public int bitWidth() {
        return 8;
    }

    @Override
    public int mask() {
        long m = this.low | this.high;
        m |= (m >>> 32);
        m |= (m >>> 16);
        m |= (m >>> 8);
        return ((int) m) << 24;
    }
}
