package net.thermaladd.mod.tileentity;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import cofh.lib.util.helpers.MathHelper;

/**
 * Singularity Strongbox - past real Thermal Expansion's Resonant Strongbox (72 slots, 104 with
 * four Holding enchantments) at 120 slots, 15 by 8. Like TE's {@code TileStrongbox} it keeps its
 * whole inventory when broken or dismantled - the contents ride inside the dropped item - and it
 * opens its lid while someone is looking inside, with the same easing and the same sounds.
 *
 * TE's access control (public/restricted/private) is left out; the box is always public, and pipes
 * reach every slot from every side.
 */
public class TileSingularityStrongbox extends TileEntity implements ISidedInventory {

    public static final int COLUMNS = 15;
    public static final int ROWS = 8;
    public static final int SIZE = COLUMNS * ROWS;

    public static final String TAG_INVENTORY = "Inventory";
    private static final int[] ALL_SLOTS = new int[SIZE];

    static {
        for (int i = 0; i < SIZE; i++) {
            ALL_SLOTS[i] = i;
        }
    }

    private final ItemStack[] inventory = new ItemStack[SIZE];
    private byte facing = 3;
    private String customName;

    public int numUsingPlayers;
    public double prevLidAngle;
    public double lidAngle;

    public int getFacing() {
        return facing;
    }

    public void setFacing(int side) {
        facing = (byte) side;
        markDirty();
    }

    public void setCustomName(String name) {
        customName = name;
    }

    /** TE's lid curve: eased so it snaps open and settles shut. Negative - the model is drawn upside down. */
    public double getRadianLidAngle(float partialTicks) {
        double angle = MathHelper.interpolate(prevLidAngle, lidAngle, (double) partialTicks);
        angle = 1.0 - angle;
        angle = 1.0 - angle * angle * angle;
        return angle * Math.PI * -0.5;
    }

    public int getComparatorSignal() {
        return net.minecraft.inventory.Container.calcRedstoneFromInventory(this);
    }

    // ---------------------------------------------------------------- lid (as TE's TileStrongbox / vanilla chest)

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote && numUsingPlayers > 0 && worldObj.getTotalWorldTime() % 200 == 0) {
            worldObj.addBlockEvent(xCoord, yCoord, zCoord, getBlockType(), 1, numUsingPlayers);
        }
        prevLidAngle = lidAngle;
        lidAngle = MathHelper.approachLinear(lidAngle, numUsingPlayers > 0 ? 1.0 : 0.0, 0.1);
        if (prevLidAngle >= 0.5 && lidAngle < 0.5) {
            worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.chestclosed", 0.5F,
                    worldObj.rand.nextFloat() * 0.1F + 0.9F);
        } else if (prevLidAngle == 0.0 && lidAngle > 0.0) {
            worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.chestopen", 0.5F,
                    worldObj.rand.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public boolean receiveClientEvent(int id, int value) {
        if (id == 1) {
            numUsingPlayers = value;
            return true;
        }
        return super.receiveClientEvent(id, value);
    }

    @Override
    public void openInventory() {
        if (numUsingPlayers < 0) {
            numUsingPlayers = 0;
        }
        numUsingPlayers++;
        notifyUsers();
    }

    @Override
    public void closeInventory() {
        numUsingPlayers--;
        notifyUsers();
    }

    private void notifyUsers() {
        Block block = getBlockType();
        if (block != null) {
            worldObj.addBlockEvent(xCoord, yCoord, zCoord, block, 1, numUsingPlayers);
            worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, block);
        }
    }

    // ---------------------------------------------------------------- inventory

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (inventory[slot] == null) {
            return null;
        }
        ItemStack result;
        if (inventory[slot].stackSize <= amount) {
            result = inventory[slot];
            inventory[slot] = null;
        } else {
            result = inventory[slot].splitStack(amount);
            if (inventory[slot].stackSize == 0) {
                inventory[slot] = null;
            }
        }
        markDirty();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inventory[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return customName != null && customName.length() > 0 ? customName : "tile.singularityStrongbox.name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return customName != null && customName.length() > 0;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64.0;
    }

    /**
     * A strongbox that already carries an inventory cannot go inside another one - otherwise boxes
     * could be nested without limit, and one item's NBT would grow past what a packet can carry.
     */
    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return !carriesInventory(stack);
    }

    public static boolean carriesInventory(ItemStack stack) {
        return stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey(TAG_INVENTORY);
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return true;
    }

    // ---------------------------------------------------------------- item form

    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (inventory[i] != null) {
                return false;
            }
        }
        return true;
    }

    public void writeInventoryToItem(NBTTagCompound tag) {
        if (!isEmpty()) {
            tag.setTag(TAG_INVENTORY, writeItems());
        }
    }

    public void readInventoryFromItem(NBTTagCompound tag) {
        if (tag.hasKey(TAG_INVENTORY)) {
            readItems(tag.getTagList(TAG_INVENTORY, 10));
            markDirty();
        }
    }

    /** Called by the block as it is removed - the contents already live in the dropped item. */
    public void clearContents() {
        for (int i = 0; i < SIZE; i++) {
            inventory[i] = null;
        }
    }

    private NBTTagList writeItems() {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SIZE; i++) {
            if (inventory[i] != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) i);
                inventory[i].writeToNBT(itemTag);
                list.appendTag(itemTag);
            }
        }
        return list;
    }

    private void readItems(NBTTagList list) {
        for (int i = 0; i < SIZE; i++) {
            inventory[i] = null;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < SIZE) {
                inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
    }

    // ---------------------------------------------------------------- nbt / sync

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("Facing", facing);
        if (hasCustomInventoryName()) {
            tag.setString("CustomName", customName);
        }
        tag.setTag("Items", writeItems());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        facing = tag.getByte("Facing");
        if (tag.hasKey("CustomName")) {
            customName = tag.getString("CustomName");
        }
        readItems(tag.getTagList("Items", 10));
    }

    /** Only the facing is needed client-side - the contents reach a viewer through the container. */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Facing", facing);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        facing = packet.func_148857_g().getByte("Facing");
    }
}
