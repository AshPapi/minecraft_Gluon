package net.ashpapi.papioptimization.ai;

import java.util.AbstractList;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos;

public final class FastLongJumpChoiceList extends AbstractList<LongJumpToRandomPos.PossibleJump> implements RandomAccess {

    private static final ConcurrentHashMap<Long, JumpTemplate> TEMPLATES = new ConcurrentHashMap<>(4);

    private final int originX;
    private final int originY;
    private final int originZ;
    private final JumpTemplate template;
    private final long[] activeWords;
    private int remainingCount;
    private int remainingWeight;

    private FastLongJumpChoiceList(BlockPos origin, JumpTemplate template) {
        this.originX = origin.getX();
        this.originY = origin.getY();
        this.originZ = origin.getZ();
        this.template = template;
        this.activeWords = template.initialWords.clone();
        this.remainingCount = template.count;
        this.remainingWeight = template.totalWeight;
    }

    public static FastLongJumpChoiceList create(BlockPos origin, int maxWidth, int maxHeight) {
        JumpTemplate tpl = getOrCreateTemplate(maxWidth, maxHeight);
        return new FastLongJumpChoiceList(origin, tpl);
    }

    private static JumpTemplate getOrCreateTemplate(int maxWidth, int maxHeight) {
        long key = (((long) maxWidth) << 32) | (maxHeight & 0xFFFFFFFFL);
        JumpTemplate cached = TEMPLATES.get(key);
        if (cached != null) {
            return cached;
        }
        if (maxWidth > 63 || maxHeight > 63 || maxWidth < 0 || maxHeight < 0) {
            return buildTemplate(maxWidth, maxHeight);
        }
        return TEMPLATES.computeIfAbsent(key, k -> buildTemplate(maxWidth, maxHeight));
    }

    private static JumpTemplate buildTemplate(int maxWidth, int maxHeight) {
        int spanX = (maxWidth << 1) + 1;
        int spanY = (maxHeight << 1) + 1;
        int total = spanX * spanY * spanX - 1;
        if (total <= 0) {
            return new JumpTemplate(new int[0], new int[0], new int[0], new int[0], new long[0], 0, 0);
        }
        int[] dxs = new int[total];
        int[] dys = new int[total];
        int[] dzs = new int[total];
        int[] weights = new int[total];
        int totalWeight = 0;
        int idx = 0;
        for (int dy = -maxHeight; dy <= maxHeight; dy++) {
            for (int dz = -maxWidth; dz <= maxWidth; dz++) {
                for (int dx = -maxWidth; dx <= maxWidth; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int distSq = dx * dx + dy * dy + dz * dz;
                    int w = Mth.ceil((double) distSq);
                    dxs[idx] = dx;
                    dys[idx] = dy;
                    dzs[idx] = dz;
                    weights[idx] = w;
                    totalWeight += w;
                    idx++;
                }
            }
        }
        int wordCount = (idx + 63) >>> 6;
        long[] words = new long[wordCount];
        for (int w = 0; w < wordCount - 1; w++) {
            words[w] = -1L;
        }
        int remBits = idx & 63;
        words[wordCount - 1] = remBits == 0 ? -1L : (1L << remBits) - 1L;
        return new JumpTemplate(dxs, dys, dzs, weights, words, idx, totalWeight);
    }

    public Optional<LongJumpToRandomPos.PossibleJump> pickAndRemove(RandomSource random) {
        if (this.remainingCount <= 0 || this.remainingWeight <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(this.remainingWeight);
        long[] words = this.activeWords;
        int[] weights = this.template.weights;
        for (int w = 0, wLen = words.length; w < wLen; w++) {
            long word = words[w];
            while (word != 0L) {
                int bitPos = Long.numberOfTrailingZeros(word);
                int idx = (w << 6) + bitPos;
                int weight = weights[idx];
                roll -= weight;
                if (roll < 0) {
                    words[w] &= ~(1L << bitPos);
                    this.remainingCount--;
                    this.remainingWeight -= weight;
                    return Optional.of(this.materialize(idx));
                }
                word &= word - 1L;
            }
        }
        return Optional.empty();
    }

    private LongJumpToRandomPos.PossibleJump materialize(int rawIdx) {
        JumpTemplate tpl = this.template;
        BlockPos pos = new BlockPos(
                this.originX + tpl.dxs[rawIdx],
                this.originY + tpl.dys[rawIdx],
                this.originZ + tpl.dzs[rawIdx]
        );
        return new LongJumpToRandomPos.PossibleJump(pos, tpl.weights[rawIdx]);
    }

    private int findRawIndexForLogical(int logicalIndex) {
        if (logicalIndex < 0 || logicalIndex >= this.remainingCount) {
            throw new IndexOutOfBoundsException(logicalIndex);
        }
        int rem = logicalIndex;
        long[] words = this.activeWords;
        for (int w = 0, wLen = words.length; w < wLen; w++) {
            long word = words[w];
            int pop = Long.bitCount(word);
            if (rem < pop) {
                while (rem > 0) {
                    word &= word - 1L;
                    rem--;
                }
                return (w << 6) + Long.numberOfTrailingZeros(word);
            }
            rem -= pop;
        }
        throw new IndexOutOfBoundsException(logicalIndex);
    }

    @Override
    public int size() {
        return this.remainingCount;
    }

    @Override
    public boolean isEmpty() {
        return this.remainingCount == 0;
    }

    @Override
    public LongJumpToRandomPos.PossibleJump get(int index) {
        return this.materialize(this.findRawIndexForLogical(index));
    }

    @Override
    public LongJumpToRandomPos.PossibleJump remove(int index) {
        int raw = this.findRawIndexForLogical(index);
        int w = raw >>> 6;
        long bit = 1L << (raw & 63);
        this.activeWords[w] &= ~bit;
        this.remainingCount--;
        this.remainingWeight -= this.template.weights[raw];
        return this.materialize(raw);
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof LongJumpToRandomPos.PossibleJump pj)) {
            return false;
        }
        BlockPos p = pj.targetPos();
        int dx = p.getX() - this.originX;
        int dy = p.getY() - this.originY;
        int dz = p.getZ() - this.originZ;
        JumpTemplate tpl = this.template;
        long[] words = this.activeWords;
        for (int w = 0, wLen = words.length; w < wLen; w++) {
            long word = words[w];
            while (word != 0L) {
                int bitPos = Long.numberOfTrailingZeros(word);
                int raw = (w << 6) + bitPos;
                if (tpl.dxs[raw] == dx && tpl.dys[raw] == dy && tpl.dzs[raw] == dz && tpl.weights[raw] == pj.weight()) {
                    words[w] &= ~(1L << bitPos);
                    this.remainingCount--;
                    this.remainingWeight -= tpl.weights[raw];
                    return true;
                }
                word &= word - 1L;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        for (int i = 0; i < this.activeWords.length; i++) {
            this.activeWords[i] = 0L;
        }
        this.remainingCount = 0;
        this.remainingWeight = 0;
    }

    private record JumpTemplate(
            int[] dxs,
            int[] dys,
            int[] dzs,
            int[] weights,
            long[] initialWords,
            int count,
            int totalWeight
    ) {}
}
