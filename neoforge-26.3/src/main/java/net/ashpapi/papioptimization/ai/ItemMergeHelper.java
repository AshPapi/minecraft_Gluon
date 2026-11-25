package net.ashpapi.papioptimization.ai;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jspecify.annotations.Nullable;

public final class ItemMergeHelper {

    public static final EntityTypeTest<net.minecraft.world.entity.Entity, ItemEntity> ITEM_ENTITY_TYPE =
            EntityTypeTest.forClass(ItemEntity.class);

    private static final ThreadLocal<MergeBuffer> BUFFER = ThreadLocal.withInitial(MergeBuffer::new);

    private ItemMergeHelper() {}

    public static MergeBuffer acquireBuffer() {
        MergeBuffer buf = BUFFER.get();
        if (buf.inUse) {
            buf = new MergeBuffer();
        }
        buf.inUse = true;
        buf.first = null;
        buf.extraCount = 0;
        return buf;
    }

    public static final class MergeBuffer {
        boolean inUse;
        public @Nullable ItemEntity first;
        private final ObjectArrayList<ItemEntity> extras = new ObjectArrayList<>(4);
        public int extraCount;
        public int projectedCount;

        public void add(ItemEntity entity) {
            if (this.first == null) {
                this.first = entity;
            } else {
                this.extras.add(entity);
                this.extraCount++;
            }
        }

        public ItemEntity getExtra(int index) {
            return this.extras.get(index);
        }

        public void release() {
            this.first = null;
            if (this.extraCount > 0) {
                this.extras.clear();
                this.extraCount = 0;
            }
            this.inUse = false;
        }
    }
}
