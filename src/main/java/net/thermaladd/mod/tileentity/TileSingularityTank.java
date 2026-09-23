package net.thermaladd.mod.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * Singularity Tank - past real Thermal Expansion's Resonant Portable Tank (512,000 mB) the same
 * way the Singularity Cell goes past the Resonant Energy Cell. Behaves like TE's own
 * {@code TileTank}: a wrench click toggles the orange auto-output mode that pours downward, a
 * stack of tanks fills bottom-up (what does not fit overflows into the tank above), buckets and
 * other containers work on a right-click, a comparator reads the fill level, and the tank keeps
 * its fluid when broken or dismantled.
 */
public class TileSingularityTank extends TileEntity implements IFluidHandler {

    /** Overridable from config/ThermalADD.cfg - eight times TE's Resonant tank. */
    public static int CAPACITY = 4096000;
    /** mB poured down per tick in auto-output mode (real TE pours 1,000). */
    public static final int AUTO_OUTPUT_RATE = 4000;
    /** Fill levels the renderer distinguishes; a client update is only sent when this changes. */
    private static final int RENDER_LEVELS = 128;

    public static final String TAG_FLUID = "Fluid";
    public static final String TAG_MODE = "Mode";

    private final FluidTank tank = new FluidTank(CAPACITY);
    /** 0 = hold (blue), 1 = pour into whatever is below (orange) - TE's own two modes. */
    private byte mode = 0;

    private int lastRenderLevel = -1;
    private int lastFluidId = -1;
    private int lastComparator = -1;
    private int lastLight = 0;

    public FluidStack getTankFluid() {
        return tank.getFluid();
    }

    public int getTankCapacity() {
        return tank.getCapacity();
    }

    public int getMode() {
        return mode;
    }

    public void toggleMode() {
        mode = (byte) (mode == 0 ? 1 : 0);
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /** 0-15 by fill level, 0 only when empty - same scaling as TE's own tank. */
    public int getComparatorSignal() {
        int amount = tank.getFluidAmount();
        if (amount <= 0) {
            return 0;
        }
        return Math.max(1, (int) ((long) amount * 15 / tank.getCapacity()));
    }

    /** TE's rule: half the fluid's own glow up to a quarter full, rising to all of it at three quarters. */
    public int getLightValue() {
        FluidStack fluid = tank.getFluid();
        if (fluid == null || fluid.getFluid() == null) {
            return 0;
        }
        int glow = fluid.getFluid().getLuminosity(fluid);
        // In long: the configurable capacity goes up to a billion, where capacity * 3 overflows.
        long capacity = tank.getCapacity();
        long amount = fluid.amount;
        if (amount <= capacity / 4) {
            return glow >> 1;
        }
        if (amount >= capacity * 3 / 4) {
            return glow;
        }
        return (int) ((glow >> 1) + (glow - (glow >> 1)) * (amount - capacity / 4) / (capacity / 2));
    }

    // ---------------------------------------------------------------- dismantling

    public void writeToItem(NBTTagCompound tag) {
        if (tank.getFluidAmount() > 0) {
            tag.setTag(TAG_FLUID, tank.getFluid().writeToNBT(new NBTTagCompound()));
        }
        if (mode != 0) {
            tag.setByte(TAG_MODE, mode);
        }
    }

    public void readFromItem(NBTTagCompound tag) {
        if (tag.hasKey(TAG_FLUID)) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(TAG_FLUID));
            if (fluid != null) {
                fluid.amount = Math.min(fluid.amount, tank.getCapacity());
                tank.setFluid(fluid);
            }
        }
        mode = tag.getByte(TAG_MODE) == 1 ? (byte) 1 : (byte) 0;
        markDirty();
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (mode == 1) {
            pourDown();
        }
        if (worldObj.getTotalWorldTime() % 4 == 0) {
            syncIfChanged();
        }
    }

    private void pourDown() {
        if (tank.getFluidAmount() <= 0) {
            return;
        }
        TileEntity below = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
        if (!(below instanceof IFluidHandler)) {
            return;
        }
        FluidStack offer = tank.getFluid().copy();
        offer.amount = Math.min(offer.amount, AUTO_OUTPUT_RATE);
        int filled = ((IFluidHandler) below).fill(ForgeDirection.UP, offer, true);
        if (filled > 0) {
            tank.drain(filled, true);
            markDirty();
        }
    }

    /** Pushes a client update only when something the renderer, a comparator or the light shows changed. */
    private void syncIfChanged() {
        FluidStack fluid = tank.getFluid();
        int level = fluid == null || fluid.amount <= 0 ? 0
                : Math.max(1, (int) ((long) fluid.amount * RENDER_LEVELS / tank.getCapacity()));
        int fluidId = fluid == null ? -1 : fluid.getFluidID();
        if (level != lastRenderLevel || fluidId != lastFluidId) {
            lastRenderLevel = level;
            lastFluidId = fluidId;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        int comparator = getComparatorSignal();
        if (comparator != lastComparator) {
            lastComparator = comparator;
            worldObj.func_147453_f(xCoord, yCoord, zCoord, getBlockType());
        }
        int light = getLightValue();
        if (light != lastLight) {
            lastLight = light;
            worldObj.func_147451_t(xCoord, yCoord, zCoord);
        }
    }

    // ---------------------------------------------------------------- IFluidHandler

    /**
     * TE's TileTank#fill: the bottom face refuses input while pouring down (it would just loop),
     * and whatever does not fit here overflows into a tank directly above.
     */
    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (resource == null || (from == ForgeDirection.DOWN && mode == 1
                && !(worldObj.getTileEntity(xCoord, yCoord - 1, zCoord) instanceof TileSingularityTank))) {
            return 0;
        }
        int filled = tank.fill(resource, doFill);
        if (filled < resource.amount && from != ForgeDirection.UP) {
            TileEntity above = worldObj.getTileEntity(xCoord, yCoord + 1, zCoord);
            if (above instanceof TileSingularityTank) {
                FluidStack rest = resource.copy();
                rest.amount -= filled;
                filled += ((TileSingularityTank) above).fill(ForgeDirection.DOWN, rest, doFill);
            }
        }
        if (filled > 0 && doFill) {
            markDirty();
        }
        return filled;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || !resource.isFluidEqual(tank.getFluid())) {
            return null;
        }
        return drain(from, resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        if (from == ForgeDirection.DOWN && mode == 1) {
            return null;
        }
        FluidStack drained = tank.drain(maxDrain, doDrain);
        if (drained != null && doDrain) {
            markDirty();
        }
        return drained;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[]{tank.getInfo()};
    }

    // ---------------------------------------------------------------- nbt / sync

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
        tag.setByte(TAG_MODE, mode);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tank.setFluid(null);
        if (tag.hasKey("Tank")) {
            tank.readFromNBT(tag.getCompoundTag("Tank"));
            // Capped to the (configurable) capacity: Forge's FluidTank#fill returns a negative
            // amount while overfull, which pipes and the overflow into the tank above mishandle.
            FluidStack fluid = tank.getFluid();
            if (fluid != null && fluid.amount > tank.getCapacity()) {
                fluid.amount = tank.getCapacity();
            }
        }
        mode = tag.getByte(TAG_MODE) == 1 ? (byte) 1 : (byte) 0;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        int oldLight = getLightValue();
        readFromNBT(packet.func_148857_g());
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        if (getLightValue() != oldLight) {
            worldObj.func_147451_t(xCoord, yCoord, zCoord);
        }
    }
}
