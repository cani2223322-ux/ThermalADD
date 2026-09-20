package net.thermaladd.mod.util;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Bridges a tile's "carry this NBT into the dropped item" state from {@code Block#breakBlock}
 * (the last point the tile entity is still alive) to {@code Block#getDrops} (called afterward,
 * once the tile is already gone, to build the actual dropped ItemStack). Keyed by position
 * rather than held in a single field so several blocks breaking in the same tick (e.g. an
 * explosion) can't clobber each other's pending data. Despite the name, it's a plain position ->
 * NBTTagCompound bridge with no augment-specific logic, so any tile with this same "the tile is
 * gone by drop time, but the item needs to carry some of its state" problem can reuse it - see
 * BlockAdvancedPulverizer (augments) and BlockSingularityCell (stored charge).
 *
 * Originally built to fix an augment duplication bug: augments used to drop as separate loose
 * item entities, which meant a re-placed block got handed a brand new set of default augments
 * on top of the ones already lying on the ground.
 *
 * {@code take()} isn't guaranteed to run for every {@code put()}: a creative-mode break never
 * calls {@code getDrops} at all (Block#breakBlock still runs, {@code World#func_147480_a} is
 * called with {@code dropBlock=false}), and an explosion's own drop-chance roll can skip it too -
 * either way, the pending entry would otherwise sit in this map forever, a slow unbounded leak
 * on any long-running server with creative-mode use. Capped as an eviction-oldest LRU instead of
 * a plain HashMap so a missed take() can never accumulate past MAX_PENDING entries - comfortably
 * above anything a single tick's worth of simultaneous breaks (an explosion, WorldEdit, etc.)
 * would ever need alive at once.
 */
public final class PendingAugmentDrops {

    private static final int MAX_PENDING = 64;

    private static final Map<Long, NBTTagCompound> PENDING = new LinkedHashMap<Long, NBTTagCompound>(16, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, NBTTagCompound> eldest) {
            return size() > MAX_PENDING;
        }
    };

    private PendingAugmentDrops() {
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }

    public static void put(int x, int y, int z, NBTTagCompound augmentTag) {
        PENDING.put(key(x, y, z), augmentTag);
    }

    /** Returns and clears the pending tag for this position, or null if there was none. */
    public static NBTTagCompound take(int x, int y, int z) {
        return PENDING.remove(key(x, y, z));
    }
}
