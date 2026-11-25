package net.ashpapi.papioptimization.client;

import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record CompactShortLineData(long w0, long w1, long w2, long w3) implements UnihexProvider.LineData {

    @Override
    public int line(int index) {
        long word = switch (index >>> 2) {
            case 0 -> this.w0;
            case 1 -> this.w1;
            case 2 -> this.w2;
            default -> this.w3;
        };
        return ((int) (word >>> ((index & 3) << 4))) << 16;
    }

    @Override
    public int bitWidth() {
        return 16;
    }

    @Override
    public int mask() {
        long m = this.w0 | this.w1 | this.w2 | this.w3;
        m |= (m >>> 32);
        m |= (m >>> 16);
        return ((int) m) << 16;
    }
}
