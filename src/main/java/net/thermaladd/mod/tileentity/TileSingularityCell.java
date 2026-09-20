package net.thermaladd.mod.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.IEnergyHandler;
import cofh.api.energy.IEnergyReceiver;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.thermaladd.mod.network.MessageTileRenderSync;
import net.thermaladd.mod.network.PacketHandler;

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
 *
 * Side configuration mirrors real TE's own TileCell exactly (decompiled from CoFHCore/TE
 * 3.1.4-329 / 4.1.5-248): 3 modes per side - Disabled(0)/Output(1)/Input(2), never both at once
 * on the same face (unlike this mod's other machines, which also have an Auto mode) - same
 * DEFAULT_SIDES as real TE: bottom defaults to Output, the other 5 faces default to Input.
 */
public class TileSingularityCell extends TileEntity implements IEnergyHandler {

    public static final long CAPACITY = 1000000000000L;

    public static final int MODE_DISABLED = 0;
    public static final int MODE_OUTPUT = 1;
    public static final int MODE_INPUT = 2;
    public static final int MODE_COUNT = 3;

    /** Same order/values as real TE's TileCell.DEFAULT_SIDES: {Down, Up, North, South, West, East}. */
    private static final byte[] DEFAULT_SIDES = {MODE_OUTPUT, MODE_INPUT, MODE_INPUT, MODE_INPUT, MODE_INPUT, MODE_INPUT};

    private long energyStored = 0L;
    private byte[] sideCache = DEFAULT_SIDES.clone();

    /** RF actually moved in/out on the tick just finished - "Вход"/"Выход" in the GUI. */
    private long energyInLastTick = 0L;
    private long energyOutLastTick = 0L;
    private long energyInThisTick = 0L;
    private long energyOutThisTick = 0L;

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        if (from != ForgeDirection.UNKNOWN && sideCache[from.ordinal()] != MODE_INPUT) {
            return 0;
        }
        long accepted = Math.min((long) maxReceive, CAPACITY - energyStored);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            energyStored += accepted;
            energyInThisTick += accepted;
            markDirty();
        }
        return (int) accepted;
    }

    @Override
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        if (from != ForgeDirection.UNKNOWN && sideCache[from.ordinal()] != MODE_OUTPUT) {
            return 0;
        }
        long given = Math.min((long) maxExtract, energyStored);
        if (given <= 0) {
            return 0;
        }
        if (!simulate) {
            energyStored -= given;
            energyOutThisTick += given;
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
        return from == ForgeDirection.UNKNOWN || sideCache[from.ordinal()] != MODE_DISABLED;
    }

    /** The TRUE stored value, for this mod's own GUI (and NBT round-trip) - not int-capped. */
    public long getEnergyStoredLong() {
        return energyStored;
    }

    public long getCapacityLong() {
        return CAPACITY;
    }

    public long getEnergyInPerTick() {
        return energyInLastTick;
    }

    public long getEnergyOutPerTick() {
        return energyOutLastTick;
    }

    public void setEnergyStoredLong(long value) {
        energyStored = Math.max(0L, Math.min(value, CAPACITY));
        markDirty();
    }

    /** Client-side only: applies values received via MessageEnergyCellSync. */
    public void setEnergyStoredClient(long stored, long in, long out) {
        energyStored = stored;
        energyInLastTick = in;
        energyOutLastTick = out;
    }

    /** Same idea as real TE's TileCell#getLightValue - a faint glow that brightens as it fills. */
    public int getLightValue() {
        if (energyStored <= 0) {
            return 0;
        }
        double ratio = energyStored / (double) CAPACITY;
        return Math.min(15, 1 + (int) (ratio * 14.0));
    }

    // ---------------------------------------------------------------- side configuration

    public int getSideMode(int side) {
        return sideCache[side];
    }

    /**
     * {@code side} arrives straight from a client-sent network packet (MessageCycleSide's
     * {@code side} field is an unchecked byte, -128..127) - without this bounds check, an
     * out-of-range value indexes {@code sideCache}/{@code DEFAULT_SIDES} out of bounds and
     * throws, which Forge's packet handling turns into a disconnect for the sender. Every
     * cycle/reset entry point below needs this same guard for the same reason.
     */
    private static boolean isValidSide(int side) {
        return side >= 0 && side < 6;
    }

    public boolean cycleSideMode(int side, int direction) {
        if (!isValidSide(side)) {
            return false;
        }
        sideCache[side] = (byte) (((sideCache[side] + direction) % MODE_COUNT + MODE_COUNT) % MODE_COUNT);
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetSideMode(int side) {
        if (!isValidSide(side)) {
            return false;
        }
        sideCache[side] = DEFAULT_SIDES[side];
        markDirty();
        syncRenderState();
        return true;
    }

    public boolean resetAllSideModes() {
        sideCache = DEFAULT_SIDES.clone();
        markDirty();
        syncRenderState();
        return true;
    }

    /** Client-side only: applies a side mode received via MessageTileRenderSync. */
    public void setSideModeClient(int side, int mode) {
        sideCache[side] = (byte) mode;
    }

    /** Server-side: restores side modes from a picked-back-up cell's own NBT (see BlockSingularityCell#onBlockPlacedBy). */
    public void setSideModes(byte[] sides) {
        for (int i = 0; i < Math.min(sideCache.length, sides.length); i++) {
            sideCache[i] = sides[i];
        }
        markDirty();
    }

    /** Same "server can't repaint the client's own World" problem as the other tiles - see TileAdvancedPulverizer#syncRenderState. */
    private void syncRenderState() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        PacketHandler.INSTANCE.sendToAllAround(new MessageTileRenderSync(xCoord, yCoord, zCoord, (byte) 0, sideCache),
                new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 64.0));
    }

    // ---------------------------------------------------------------- tick

    /**
     * Real TE's own TileCell doesn't just sit and wait to be asked - every tick it actively
     * pushes into whatever IEnergyReceiver sits against an Output-configured face (see its
     * decompiled updateEntity/transferEnergy). Without this, our own extractEnergy() only ever
     * fires if the NEIGHBOR pulls first - most machines (this mod's own included) only
     * implement IEnergyReceiver and never call extractEnergy on anything themselves, so a cell
     * set to Output with a passive machine or a passive conduit segment against it would just
     * sit there fully charged, "connected" but never actually transferring: two ends both
     * waiting for the other to ask. This is exactly that missing push, and why the user
     * reported "Output does nothing" despite the side being set correctly and the cell full.
     */
    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        if (energyStored > 0) {
            for (int side = 0; side < 6 && energyStored > 0; side++) {
                if (sideCache[side] != MODE_OUTPUT) {
                    continue;
                }
                ForgeDirection dir = ForgeDirection.getOrientation(side);
                TileEntity neighbor = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
                if (!(neighbor instanceof IEnergyReceiver)) {
                    continue;
                }
                IEnergyReceiver receiver = (IEnergyReceiver) neighbor;
                ForgeDirection fromNeighborSide = dir.getOpposite();
                if (!receiver.canConnectEnergy(fromNeighborSide)) {
                    continue;
                }
                int offered = (int) Math.min(energyStored, Integer.MAX_VALUE);
                int accepted = receiver.receiveEnergy(fromNeighborSide, offered, false);
                if (accepted > 0) {
                    energyStored -= accepted;
                    energyOutThisTick += accepted;
                    markDirty();
                }
            }
        }

        energyInLastTick = energyInThisTick;
        energyOutLastTick = energyOutThisTick;
        energyInThisTick = 0L;
        energyOutThisTick = 0L;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("Energy", energyStored);
        tag.setByteArray("Sides", sideCache);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energyStored = tag.getLong("Energy");
        if (tag.hasKey("Sides")) {
            byte[] sides = tag.getByteArray("Sides");
            if (sides.length == sideCache.length) {
                sideCache = sides;
            }
        }
    }

    /** See TileAdvancedPulverizer#getDescriptionPacket - without this a freshly loaded chunk never tells the client the real charge/sides (or the light level charge implies). */
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
