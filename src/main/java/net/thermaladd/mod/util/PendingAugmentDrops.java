package net.thermaladd.mod.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Bridges a machine tile's augment NBT from {@code Block#breakBlock} (the last point the tile
 * entity is still alive) to {@code Block#getDrops} (called afterward, once the tile is already
 * gone, to build the actual dropped ItemStack). Keyed by position rather than held in a single
 * field so several of these blocks breaking in the same tick (e.g. an explosion) can't clobber
 * each other's pending data.
 *
 * Without this bridge, a machine's augments would have to be dropped as separate loose item
 * entities (as they briefly were), which meant a re-placed block got handed a brand new set of
 * default augments on top of the ones already lying on the ground - an augment duplication bug.
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
