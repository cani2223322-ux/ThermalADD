package net.thermaladd.mod.util;

import java.util.HashMap;
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
 */
public final class PendingAugmentDrops {

    private static final Map<Long, NBTTagCompound> PENDING = new HashMap<Long, NBTTagCompound>();

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
