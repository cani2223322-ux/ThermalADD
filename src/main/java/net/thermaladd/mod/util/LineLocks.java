package net.thermaladd.mod.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Per-line item locks for the parallel machines. A locked line accepts only the item it was
 * locked to - through the GUI, shift-click, pipes and auto-input alike - so automation feeding a
 * mixed stream cannot fill every line with whichever item happens to arrive first.
 *
 * A lock is a 1-item copy of the stack (item, damage and NBT all matter; the count does not). It
 * is set from the line's current contents, so an empty line cannot be locked - there would be
 * nothing to lock it to.
 */
public final class LineLocks {

    private static final String TAG = "LineLocks";

    private final ItemStack[] filters;

    public LineLocks(int lines) {
        filters = new ItemStack[lines];
    }

    public int size() {
        return filters.length;
    }

    /** The item a line is locked to, or null. The returned stack must not be modified. */
    public ItemStack get(int line) {
        return line >= 0 && line < filters.length ? filters[line] : null;
    }

    public boolean isLocked(int line) {
        return get(line) != null;
    }

    public boolean accepts(int line, ItemStack stack) {
        ItemStack filter = get(line);
        return filter == null || stack != null && filter.getItem() == stack.getItem()
                && filter.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(filter, stack);
    }

    /**
     * Unlocks a locked line, or locks an unlocked one to {@code current}. Returns false when there
     * was nothing to do: the line is unlocked and empty.
     */
    public boolean toggle(int line, ItemStack current) {
        if (line < 0 || line >= filters.length) {
            return false;
        }
        if (filters[line] != null) {
            filters[line] = null;
            return true;
        }
        if (current == null) {
            return false;
        }
        ItemStack filter = current.copy();
        filter.stackSize = 1;
        filters[line] = filter;
        return true;
    }

    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < filters.length; i++) {
            if (filters[i] != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte("Line", (byte) i);
                filters[i].writeToNBT(entry);
                list.appendTag(entry);
            }
        }
        tag.setTag(TAG, list);
    }

    /** Clears first, so a description packet that drops a lock actually removes it client-side. */
    public void readFromNBT(NBTTagCompound tag) {
        for (int i = 0; i < filters.length; i++) {
            filters[i] = null;
        }
        NBTTagList list = tag.getTagList(TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int line = entry.getByte("Line") & 0xFF;
            if (line < filters.length) {
                filters[line] = ItemStack.loadItemStackFromNBT(entry);
            }
        }
    }
}
