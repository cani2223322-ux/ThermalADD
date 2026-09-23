package net.thermaladd.mod.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import cofh.api.tileentity.ISecurable;
import cofh.thermalexpansion.block.TEBlocks;
import cofh.thermalexpansion.block.TileAugmentable;
import cofh.thermalexpansion.block.TilePowered;
import cofh.thermalexpansion.block.TileReconfigurable;
import cofh.thermalexpansion.block.machine.BlockMachine;
import cofh.thermalexpansion.block.strongbox.TileStrongbox;
import cofh.thermalexpansion.block.tank.TileTank;
import net.thermaladd.mod.init.ModBlocks;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.tileentity.TileSingularCrucible;
import net.thermaladd.mod.tileentity.TileSingularSmelter;
import net.thermaladd.mod.tileentity.TileSingularTransposer;
import net.thermaladd.mod.tileentity.TileSingularityMachine;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;
import net.thermaladd.mod.tileentity.TileSingularityTank;

/**
 * The Singularity Upgrade Kit's work: replaces a placed Thermal Expansion machine, tank or
 * strongbox with its singularity counterpart in place, carrying over everything it held - items
 * (each to the matching slot of the new machine's first line), augments (where the new machine
 * takes them), stored RF, the tank's fluid, the facing, the side configuration (TE's mode numbers
 * are the ones these machines already use), the Transposer's direction and the custom name.
 * Anything with no place in the new block is dropped at the player's feet, never lost.
 */
public final class MachineUpgrade {

    private MachineUpgrade() {
    }

    /** One TE machine type: what it becomes and where each of its slots goes (-1 = drop). */
    private static final class Conversion {
        final Block target;
        final int[] slotMap;

        Conversion(Block target, int[] slotMap) {
            this.target = target;
            this.slotMap = slotMap;
        }
    }

    private static Conversion machineConversion(int meta) {
        BlockMachine.Types[] types = BlockMachine.Types.values();
        if (meta < 0 || meta >= types.length) {
            return null;
        }
        int smelterCharge = TileSingularSmelter.MACHINE_SLOTS + TileSingularityMachine.AUGMENT_SLOTS;
        int crucibleCharge = TileSingularCrucible.MACHINE_SLOTS + TileSingularityMachine.AUGMENT_SLOTS;
        int transposerCharge = TileSingularTransposer.MACHINE_SLOTS + TileSingularityMachine.AUGMENT_SLOTS;
        switch (types[meta]) {
            case PULVERIZER:
                // TE: input, primary x2, secondary, charge.
                return new Conversion(ModBlocks.advancedPulverizer, new int[]{
                        TileAdvancedPulverizer.INPUT_START, TileAdvancedPulverizer.OUTPUT_PRIMARY_START,
                        TileAdvancedPulverizer.OUTPUT_PRIMARY_START + 1, TileAdvancedPulverizer.OUTPUT_SECONDARY_START,
                        TileAdvancedPulverizer.CHARGE_SLOT});
            case SAWMILL:
                return new Conversion(ModBlocks.advancedSawmill, new int[]{
                        TileAdvancedSawmill.INPUT_START, TileAdvancedSawmill.OUTPUT_PRIMARY_START,
                        TileAdvancedSawmill.OUTPUT_PRIMARY_START + 1, TileAdvancedSawmill.OUTPUT_SECONDARY_START,
                        TileAdvancedSawmill.CHARGE_SLOT});
            case FURNACE:
                // TE: input, output, charge.
                return new Conversion(ModBlocks.advancedFurnace, new int[]{
                        TileAdvancedFurnace.INPUT_START, TileAdvancedFurnace.OUTPUT_START, TileAdvancedFurnace.CHARGE_SLOT});
            case CHARGER:
                // TE: input, the item being charged, output, charge.
                return new Conversion(ModBlocks.advancedCharger, new int[]{
                        TileAdvancedCharger.LINE_START, TileAdvancedCharger.LINE_START + 1,
                        TileAdvancedCharger.OUTPUT_START, TileAdvancedCharger.CHARGE_SLOT});
            case ASSEMBLER: {
                // TE: schematic, output, charge, then the 18-slot buffer.
                int[] map = new int[21];
                map[0] = TileImprovedAssembler.SCHEMATIC_START;
                map[1] = TileImprovedAssembler.OUTPUT_START;
                map[2] = TileImprovedAssembler.CHARGE_SLOT;
                for (int i = 0; i < 18; i++) {
                    map[3 + i] = TileImprovedAssembler.INPUT_START + i;
                }
                return new Conversion(ModBlocks.improvedAssembler, map);
            }
            case SMELTER:
                // TE: input A, input B, primary x2, secondary, charge.
                return new Conversion(ModBlocks.singularSmelter, new int[]{
                        0, 1, TileSingularSmelter.OUTPUT_PRIMARY_START, TileSingularSmelter.OUTPUT_PRIMARY_START + 1,
                        TileSingularSmelter.OUTPUT_SECONDARY_START, smelterCharge});
            case CRUCIBLE:
                // TE: input, charge.
                return new Conversion(ModBlocks.singularCrucible, new int[]{0, crucibleCharge});
            case TRANSPOSER:
                // TE: input, the item in process, output, charge.
                return new Conversion(ModBlocks.singularTransposer, new int[]{
                        0, 1, TileSingularTransposer.OUTPUT_START, transposerCharge});
            default:
                return null;
        }
    }

    /** Whether the kit does anything to this block - also asked client-side, to keep TE's GUI from opening. */
    public static boolean canUpgrade(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        if (block == TEBlocks.blockMachine) {
            return te instanceof TileReconfigurable && machineConversion(world.getBlockMetadata(x, y, z)) != null;
        }
        return te instanceof TileTank || te instanceof TileStrongbox;
    }

    /** Server side. Returns the new block's name key on success, or null. */
    public static String upgrade(World world, int x, int y, int z, EntityPlayer player) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof ISecurable && !((ISecurable) te).canPlayerAccess(player)) {
            return null;
        }
        NBTTagCompound teTag = new NBTTagCompound();
        te.writeToNBT(teTag);
        String name = teTag.hasKey("Name") && teTag.getString("Name").length() > 0 ? teTag.getString("Name") : null;
        List<ItemStack> leftovers = new ArrayList<ItemStack>();
        String result;

        if (te instanceof TileTank) {
            result = upgradeTank(world, x, y, z, (TileTank) te);
        } else if (te instanceof TileStrongbox) {
            result = upgradeStrongbox(world, x, y, z, (TileStrongbox) te, name, leftovers);
        } else if (world.getBlock(x, y, z) == TEBlocks.blockMachine && te instanceof TileReconfigurable) {
            result = upgradeMachine(world, x, y, z, (TileReconfigurable) te, teTag, name, leftovers);
        } else {
            return null;
        }

        for (int i = 0; i < leftovers.size(); i++) {
            drop(world, player, leftovers.get(i));
        }
        return result;
    }

    private static String upgradeMachine(World world, int x, int y, int z, TileReconfigurable te, NBTTagCompound teTag,
            String name, List<ItemStack> leftovers) {
        Conversion conversion = machineConversion(world.getBlockMetadata(x, y, z));
        if (conversion == null) {
            return null;
        }
        int facing = te.getFacing();
        byte[] sides = te.sideCache == null ? null : te.sideCache.clone();
        int energy = te instanceof TilePowered ? ((TilePowered) te).getEnergyStored(ForgeDirection.UNKNOWN) : 0;
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(teTag);
        boolean reverse = teTag.getBoolean("Rev");

        // Take everything out first, so TE's own breakBlock has nothing left to spill.
        ItemStack[] items = takeInventory((IInventory) te);
        ItemStack[] augments = new ItemStack[0];
        if (te instanceof TileAugmentable) {
            ItemStack[] slots = ((TileAugmentable) te).getAugmentSlots();
            augments = new ItemStack[slots.length];
            for (int i = 0; i < slots.length; i++) {
                augments[i] = slots[i];
                slots[i] = null;
            }
        }

        world.setBlock(x, y, z, conversion.target, facing, 3);
        TileEntity created = world.getTileEntity(x, y, z);
        if (!(created instanceof IPortableMachineState) || !(created instanceof IInventory)) {
            // Should never happen - but if it does, give the player everything back.
            addAll(leftovers, items);
            addAll(leftovers, augments);
            return null;
        }
        IPortableMachineState state = (IPortableMachineState) created;
        IInventory inventory = (IInventory) created;
        state.setFacing(facing);
        if (name != null) {
            state.setCustomName(name);
        }

        installAugments(inventory, state, augments, leftovers);
        // Without the Reconfigurable Sides augment the new machine hides its Configuration tab and
        // refuses side changes, so TE's factory sides would be stuck on it - leave them Disabled,
        // as every machine here is without that augment.
        if (sides != null && state.canReconfigureSides()) {
            state.applySideModes(sides, facing);
        }
        state.setStoredEnergy(energy);

        for (int i = 0; i < items.length; i++) {
            int slot = i < conversion.slotMap.length ? conversion.slotMap[i] : -1;
            put(inventory, slot, items[i], leftovers);
        }

        if (fluid != null && fluid.amount > 0) {
            if (created instanceof TileSingularityMachine) {
                ((TileSingularityMachine) created).setTankContents(fluid);
            } else if (created instanceof TileImprovedAssembler) {
                ((TileImprovedAssembler) created).setTankContents(fluid);
            }
        }
        if (reverse && created instanceof TileSingularTransposer) {
            ((TileSingularTransposer) created).setMachineMode(TileSingularTransposer.MODE_EXTRACT);
        }
        created.markDirty();
        world.markBlockForUpdate(x, y, z);
        return conversion.target.getUnlocalizedName() + ".name";
    }

    /** Augments go into the new machine's augment slots where it takes them; the rest is handed back. */
    private static void installAugments(IInventory inventory, IPortableMachineState state, ItemStack[] augments,
            List<ItemStack> leftovers) {
        int start = augmentStart(inventory);
        int next = 0;
        for (int i = 0; i < augments.length; i++) {
            ItemStack augment = augments[i];
            if (augment == null) {
                continue;
            }
            if (start >= 0 && next < 9 && inventory.isItemValidForSlot(start + next, augment)) {
                inventory.setInventorySlotContents(start + next, augment);
                next++;
            } else {
                leftovers.add(augment);
            }
        }
    }

    private static int augmentStart(IInventory inventory) {
        if (inventory instanceof TileSingularityMachine) {
            return ((TileSingularityMachine) inventory).getAugmentStart();
        }
        if (inventory instanceof TileAdvancedPulverizer) {
            return TileAdvancedPulverizer.AUGMENT_START;
        }
        if (inventory instanceof TileAdvancedSawmill) {
            return TileAdvancedSawmill.AUGMENT_START;
        }
        if (inventory instanceof TileAdvancedFurnace) {
            return TileAdvancedFurnace.AUGMENT_START;
        }
        if (inventory instanceof TileAdvancedCharger) {
            return TileAdvancedCharger.AUGMENT_START;
        }
        if (inventory instanceof TileImprovedAssembler) {
            return TileImprovedAssembler.AUGMENT_START;
        }
        return -1;
    }

    private static String upgradeTank(World world, int x, int y, int z, TileTank te) {
        FluidStack fluid = te.getTankFluid() == null ? null : te.getTankFluid().copy();
        byte mode = te.mode;
        world.setBlock(x, y, z, ModBlocks.singularityTank, 0, 3);
        TileEntity created = world.getTileEntity(x, y, z);
        if (!(created instanceof TileSingularityTank)) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        if (fluid != null && fluid.amount > 0) {
            tag.setTag(TileSingularityTank.TAG_FLUID, fluid.writeToNBT(new NBTTagCompound()));
        }
        tag.setByte(TileSingularityTank.TAG_MODE, mode);
        ((TileSingularityTank) created).readFromItem(tag);
        world.markBlockForUpdate(x, y, z);
        return ModBlocks.singularityTank.getUnlocalizedName() + ".name";
    }

    private static String upgradeStrongbox(World world, int x, int y, int z, TileStrongbox te, String name,
            List<ItemStack> leftovers) {
        int facing = te.getFacing();
        ItemStack[] items = takeInventory(te);
        world.setBlock(x, y, z, ModBlocks.singularityStrongbox, 0, 3);
        TileEntity created = world.getTileEntity(x, y, z);
        if (!(created instanceof TileSingularityStrongbox)) {
            addAll(leftovers, items);
            return null;
        }
        TileSingularityStrongbox box = (TileSingularityStrongbox) created;
        box.setFacing(facing >= 2 && facing <= 5 ? facing : 3);
        if (name != null) {
            box.setCustomName(name);
        }
        int next = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                continue;
            }
            if (next < box.getSizeInventory()) {
                box.setInventorySlotContents(next++, items[i]);
            } else {
                leftovers.add(items[i]);
            }
        }
        world.markBlockForUpdate(x, y, z);
        return ModBlocks.singularityStrongbox.getUnlocalizedName() + ".name";
    }

    private static ItemStack[] takeInventory(IInventory inventory) {
        ItemStack[] items = new ItemStack[inventory.getSizeInventory()];
        for (int i = 0; i < items.length; i++) {
            items[i] = inventory.getStackInSlot(i);
            inventory.setInventorySlotContents(i, null);
        }
        return items;
    }

    private static void put(IInventory inventory, int slot, ItemStack stack, List<ItemStack> leftovers) {
        if (stack == null) {
            return;
        }
        if (slot >= 0 && slot < inventory.getSizeInventory() && inventory.getStackInSlot(slot) == null) {
            inventory.setInventorySlotContents(slot, stack);
        } else {
            leftovers.add(stack);
        }
    }

    private static void addAll(List<ItemStack> list, ItemStack[] stacks) {
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                list.add(stacks[i]);
            }
        }
    }

    private static void drop(World world, EntityPlayer player, ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack) || stack.stackSize > 0) {
            if (stack.stackSize > 0) {
                EntityItem entity = new EntityItem(world, player.posX, player.posY, player.posZ, stack);
                entity.delayBeforeCanPickup = 0;
                world.spawnEntityInWorld(entity);
            }
        }
        player.inventoryContainer.detectAndSendChanges();
    }
}
