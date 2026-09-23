package net.thermaladd.mod.util;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The state that travels with a machine when it is picked back up: installed augments, the
 * per-side configuration, and whatever RF was left in its buffer. Implemented by the five machine
 * tiles so {@link MachineDismantle} can save and restore all of it through one shared code path
 * instead of six near-identical copies in the block classes.
 *
 * The Singularity Cell deliberately does NOT implement this - its energy is a long, far past the
 * int this interface (and the whole RF API) works in, so it keeps its own save/restore code.
 */
public interface IPortableMachineState {

    NBTTagCompound writeAugmentsToNBT(NBTTagCompound tag);

    void readAugmentsFromNBT(NBTTagCompound tag);

    /** A defensive copy - callers must not be able to write straight into the tile's own array. */
    byte[] getSideModesCopy();

    /**
     * Applies a side configuration that was captured on a machine facing {@code sourceFacing},
     * rotated onto this machine's own facing (see SideRotation), and always leaves the front
     * Disabled - the front is never configurable. Ignores an array of the wrong length or with an
     * out-of-range mode in it. Pass -1 when the source facing is unknown.
     */
    void applySideModes(byte[] modes, int sourceFacing);

    int getFacing();

    int getEnergy();

    void setStoredEnergy(int energy);
}
