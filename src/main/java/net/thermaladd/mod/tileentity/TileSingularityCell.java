package net.thermaladd.mod.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.IEnergyHandler;

/**
 * "Beyond spec" Energy Cell, modeled on real Thermal Expansion's TileCell (Resonant tier) but
 * pushed past what the standard CoFH energy API can actually express.
 *
 * The RF API this whole game version runs on (cofh.api.energy.*) is {@code int}-based end to
 * end - every receiveEnergy/extractEnergy/getEnergyStored call, on every pipe, conduit and
 * machine in the pack, passes energy as a 32-bit signed int. That hard-caps any single value at
 * Integer.MAX_VALUE (2,147,483,647 - about 2.15 billion), roughly 0.2% of the "1 trillion RF"
 * asked for; real TE's own Resonant Cell tops out at 80,000,000 by default (1,000,000,000 even
 * if stretched to the config's own hard limit) for the exact same reason. A literal 1 trillion
 * capacity simply cannot be reported or transferred through that API in a single call.
 *
 * What IS achievable, and what this class does: {@link #energyStored} is a {@code long}
 * internally, capped at the real 1,000,000,000,000 the request asked for, so the tile's TRUE
 * charge can exceed int range and the GUI shows the real number - it just takes more than one
 * receiveEnergy() call to fill that far, the same way it would take more than one delivery to
 * move a trillion of anything through a pipe sized for two billion at a time. Every external
 * receiveEnergy/extractEnergy call is answered as generously as the int-based API allows -
 * accepts/gives whatever is offered/requested, up to Integer.MAX_VALUE if that's what a single
 * call presents - which is the closest thing to "unlimited I/O" a compliant CoFH device can be.
 */
public class TileSingularityCell extends TileEntity implements IEnergyHandler {

    public static final long CAPACITY = 1000000000000L;

    private long energyStored = 0L;

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        long accepted = Math.min((long) maxReceive, CAPACITY - energyStored);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            energyStored += accepted;
            markDirty();
        }
        return (int) accepted;
    }

    @Override
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        long given = Math.min((long) maxExtract, energyStored);
        if (given <= 0) {
            return 0;
        }
        if (!simulate) {
            energyStored -= given;
            markDirty();
        }
        return (int) given;
    }

    /** Int-capped view for any other mod's block/pipe querying through the standard API. */
    @Override
    public int getEnergyStored(ForgeDirection from) {
        return (int) Math.min(energyStored, Integer.MAX_VALUE);
    }

    /** Reports the highest value the int-based API can express at all - see the class javadoc. */
    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    /** The TRUE stored value, for this mod's own GUI (and NBT round-trip) - not int-capped. */
    public long getEnergyStoredLong() {
        return energyStored;
    }

    public long getCapacityLong() {
        return CAPACITY;
    }

    public void setEnergyStoredLong(long value) {
        energyStored = Math.max(0L, Math.min(value, CAPACITY));
        markDirty();
    }

    /** Client-side only: applies a value received via MessageEnergyCellSync. */
    public void setEnergyStoredClient(long value) {
        energyStored = value;
    }

    /** Same idea as real TE's TileCell#getLightValue - a faint glow that brightens as it fills. */
    public int getLightValue() {
        if (energyStored <= 0) {
            return 0;
        }
        double ratio = energyStored / (double) CAPACITY;
        return Math.min(15, 1 + (int) (ratio * 14.0));
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("Energy", energyStored);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energyStored = tag.getLong("Energy");
    }

    /** See TileAdvancedPulverizer#getDescriptionPacket - without this a freshly loaded chunk never tells the client the real charge (or the light level it implies). */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /** Not an IInventory (no slots here), but ContainerSingularityCell wants the same "still there, still in range" gate every other tile's GUI uses. */
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }
}
