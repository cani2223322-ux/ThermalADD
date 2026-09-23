package net.thermaladd.mod.util;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Shared "the machine travels with its settings" logic for all six blocks in this mod.
 *
 * WHY THIS REPLACED THE OLD PendingAugmentDrops HANDOFF: the previous design captured the tile's
 * augment NBT in {@code breakBlock} into a static position-keyed map and picked it back up in
 * {@code getDrops}, because {@code getDrops} runs after the tile is already gone. That ordering is
 * only true for a player harvest ({@code removedByPlayer} then {@code harvestBlock}). Every other
 * destruction path - {@code World#func_147480_a}, which most mod block-breakers and the Wither
 * use, and {@code Explosion#doExplosionB} - drops the block BEFORE clearing it, so {@code getDrops}
 * ran with nothing in the map (losing the augments and the stored RF) and {@code breakBlock} then
 * left an entry behind that nothing ever collected. A later machine broken at the same coordinates
 * would pick up that stale entry and duplicate the previous machine's augments and charge, and
 * since the key carried no dimension id, "the same coordinates" included the same x/y/z in another
 * dimension entirely.
 *
 * The fix is to not need the handoff at all: {@link #createDrop} reads the live tile, and the
 * blocks delay their own removal on the player-harvest path (see the removedByPlayer/harvestBlock
 * pair on each block) so the tile is still there when {@code getDrops} asks. Every destruction path
 * now reads real state, and there is no cross-position or cross-dimension state to leak.
 */
public final class MachineDismantle {

    public static final String TAG_AUGMENTS = "Augments";
    public static final String TAG_SIDES = "Sides";
    public static final String TAG_ENERGY = "Energy";
    public static final String TAG_FACING = "Facing";

    private MachineDismantle() {
    }

    /**
     * The single ItemStack a machine drops, carrying its installed augments, its per-side
     * configuration and whatever RF was left in its buffer.
     *
     * The augment list is always written (see the comment in the body for why); side
     * configuration and energy only when they carry something.
     */
    public static ItemStack createDrop(Block block, int metadata, IPortableMachineState state) {
        ItemStack drop = new ItemStack(Item.getItemFromBlock(block), 1, metadata);
        NBTTagCompound tag = new NBTTagCompound();

        // ALWAYS written, even as an empty list. The placing block reads "no Augments tag" as "a
        // freshly crafted machine" and installs the real-TE default set of three - so omitting the
        // tag for an empty augment bay (done once, so emptied machines would stack with crafted
        // ones) let a player pull the three defaults out, break the machine, place it, and get
        // three brand-new ones: an infinite augment generator. Losing stackability for a machine
        // that has actually been placed is the right trade.
        tag.setTag(TAG_AUGMENTS, state.writeAugmentsToNBT(new NBTTagCompound()).getTagList(TAG_AUGMENTS, 10));
        byte[] sides = state.getSideModesCopy();
        if (isConfigured(sides)) {
            tag.setByteArray(TAG_SIDES, sides);
            // Sides are stored by absolute world direction, so the facing they were captured at
            // has to travel too - restoreSidesAndEnergy rotates them onto the new placement.
            tag.setByte(TAG_FACING, (byte) state.getFacing());
        }
        int energy = state.getEnergy();
        if (energy > 0) {
            tag.setInteger(TAG_ENERGY, energy);
        }

        if (!tag.hasNoTags()) {
            drop.setTagCompound(tag);
        }
        // A machine renamed in an anvil keeps that name through being picked up and placed again.
        // Every implementor is also an IInventory; the interface itself stays name-agnostic.
        if (state instanceof IInventory) {
            IInventory inventory = (IInventory) state;
            if (inventory.hasCustomInventoryName()) {
                drop.setStackDisplayName(inventory.getInventoryName());
            }
        }
        return drop;
    }

    /**
     * Restores the side configuration and buffered RF {@link #createDrop} saved. Augments are
     * deliberately left to the caller: each block already decides between "this item carries
     * augments" and "this is a fresh machine, install the real-TE default set".
     */
    public static void restoreSidesAndEnergy(ItemStack stack, IPortableMachineState state) {
        if (stack == null || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey(TAG_SIDES)) {
            // -1 for items dropped before the facing was recorded: no rotation, but the front is
            // still forced Disabled, so an old item can never leave a hidden working mode behind.
            int sourceFacing = tag.hasKey(TAG_FACING) ? tag.getByte(TAG_FACING) : -1;
            state.applySideModes(tag.getByteArray(TAG_SIDES), sourceFacing);
        }
        if (tag.hasKey(TAG_ENERGY)) {
            state.setStoredEnergy(tag.getInteger(TAG_ENERGY));
        }
    }

    /** All-zero means every side is still Disabled, i.e. nobody has configured this machine. */
    private static boolean isConfigured(byte[] sides) {
        for (int i = 0; i < sides.length; i++) {
            if (sides[i] != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Crescent-Hammer dismantle, shared by all six blocks' IDismantleable implementation. Takes
     * the block's own drops (so each block keeps deciding what it drops and with what NBT), clears
     * the block - which runs breakBlock and spills the inventory contents as usual - and hands the
     * machine item to the player, falling back to dropping it at their feet if their inventory is
     * full.
     */
    public static ArrayList<ItemStack> dismantle(Block block, EntityPlayer player, World world,
            int x, int y, int z, boolean returnDrops) {
        ArrayList<ItemStack> drops = block.getDrops(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
        world.setBlockToAir(x, y, z);
        if (!returnDrops) {
            for (int i = 0; i < drops.size(); i++) {
                give(player, world, x, y, z, drops.get(i));
            }
        }
        return drops;
    }

    private static void give(EntityPlayer player, World world, int x, int y, int z, ItemStack stack) {
        if (stack == null) {
            return;
        }
        if (player != null && player.inventory.addItemStackToInventory(stack)) {
            player.inventoryContainer.detectAndSendChanges();
            if (stack.stackSize <= 0) {
                return;
            }
        }
        EntityItem entity = new EntityItem(world, x + 0.5D, y + 0.5D, z + 0.5D, stack);
        entity.motionX = 0.0D;
        entity.motionY = 0.15D;
        entity.motionZ = 0.0D;
        entity.delayBeforeCanPickup = 10;
        world.spawnEntityInWorld(entity);
    }
}
