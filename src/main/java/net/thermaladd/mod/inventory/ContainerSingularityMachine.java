package net.thermaladd.mod.inventory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cofh.api.energy.IEnergyContainerItem;
import cofh.lib.gui.slot.SlotEnergy;
import net.thermaladd.mod.tileentity.TileSingularityMachine;

/**
 * Container shared by the TileSingularityMachine family: the machine's own slots (added by the
 * subclass), nine augment slots parked off-screen until the Augments tab moves them in, the
 * charge slot under the energy bar, and the player's inventory.
 *
 * Every number the GUI shows is synced as exact 16-bit halves, progress included - the older
 * machines still send progress whole, which is what capped their per-line cost in ModConfig.
 */
public abstract class ContainerSingularityMachine extends Container {

    public static final int SLOT_SIZE = 18;
    public static final int PLAYER_INV_Y = 92;
    public static final int PLAYER_HOTBAR_Y = 150;
    public static final int CHARGE_X = 8;
    public static final int CHARGE_Y = 17 + 45;

    private static final int PARKED = -1000;

    private static final int ID_ENERGY = 0;
    private static final int ID_MAX_ENERGY = 2;
    private static final int ID_RATE = 4;
    private static final int ID_MAX_RATE = 6;
    private static final int ID_SIDES = 8;
    private static final int ID_RECONFIG = 14;
    private static final int ID_AUTO_IN = 15;
    private static final int ID_AUTO_OUT = 16;
    private static final int ID_REDSTONE = 17;
    private static final int ID_CONTROL = 18;
    private static final int ID_MODE = 19;
    private static final int ID_FLUID_ID = 20;
    private static final int ID_FLUID_AMOUNT = 21;
    /** Four per line: progress low/high, max low/high. */
    private static final int ID_PROGRESS = 23;

    protected final TileSingularityMachine tile;
    private final Slot[] augmentSlots = new Slot[TileSingularityMachine.AUGMENT_SLOTS];
    private final int playerInvStart;

    private final int[] last;

    protected ContainerSingularityMachine(InventoryPlayer playerInv, TileSingularityMachine tile) {
        this.tile = tile;

        // The machine's own slots, in tile-index order, so container index == tile index for them.
        addMachineSlots();

        for (int i = 0; i < TileSingularityMachine.AUGMENT_SLOTS; i++) {
            augmentSlots[i] = new SlotValidated(tile, tile.getAugmentStart() + i, PARKED, PARKED) {
                /** One augment per slot, as in real TE's SlotAugment. */
                @Override
                public int getSlotStackLimit() {
                    return 1;
                }
            };
            addSlotToContainer(augmentSlots[i]);
        }
        addSlotToContainer(new SlotEnergy(tile, tile.getChargeSlot(), CHARGE_X, CHARGE_Y));

        playerInvStart = inventorySlots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * SLOT_SIZE, PLAYER_INV_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * SLOT_SIZE, PLAYER_HOTBAR_Y));
        }

        last = new int[ID_PROGRESS + tile.getLineCount() * 4];
        java.util.Arrays.fill(last, Integer.MIN_VALUE);
    }

    /** Adds one Slot per machine slot, indices 0..getAugmentStart()-1 in order. Runs inside the constructor - use only the tile and constants. */
    protected abstract void addMachineSlots();

    public TileSingularityMachine getTile() {
        return tile;
    }

    public Slot getAugmentSlot(int index) {
        return augmentSlots[index];
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stackInSlot = slot.getStack();
        ItemStack result = stackInSlot.copy();

        if (slotIndex < playerInvStart) {
            if (!mergeItemStack(stackInSlot, playerInvStart, playerInvStart + 36, true)) {
                return null;
            }
        } else if (tile.isValidAugment(stackInSlot)) {
            int start = tile.getAugmentStart();
            if (!SlotMerge.mergeOnePerSlot(inventorySlots, stackInSlot, start, start + TileSingularityMachine.AUGMENT_SLOTS)) {
                return null;
            }
        } else if (stackInSlot.getItem() instanceof IEnergyContainerItem && tile.isItemValidForSlot(tile.getChargeSlot(), stackInSlot)
                && !anyMachineSlotAccepts(stackInSlot)) {
            if (!mergeItemStack(stackInSlot, tile.getChargeSlot(), tile.getChargeSlot() + 1, false)) {
                return null;
            }
        } else if (!mergeIntoMachine(stackInSlot)) {
            return null;
        }

        if (stackInSlot.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }

    private boolean anyMachineSlotAccepts(ItemStack stack) {
        for (int slot = 0; slot < tile.getAugmentStart(); slot++) {
            if (tile.isItemValidForSlot(slot, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shift-click into the machine: a slot already holding the item first, then empty slots locked
     * to it, then empty unlocked ones - see ContainerAdvancedPulverizer#mergeIntoLines. Validity is
     * rechecked before every merge, since filling one input changes what its partner accepts.
     */
    private boolean mergeIntoMachine(ItemStack stack) {
        boolean moved = false;
        for (int pass = 0; pass < 3 && stack.stackSize > 0; pass++) {
            for (int slot = 0; slot < tile.getAugmentStart() && stack.stackSize > 0; slot++) {
                boolean occupied = tile.getStackInSlot(slot) != null;
                int lock = tile.lockIndexOf(slot);
                boolean locked = lock >= 0 && tile.getLineLocks().isLocked(lock);
                boolean inThisPass = pass == 0 ? occupied : !occupied && (pass == 1) == locked;
                if (inThisPass && tile.isItemValidForSlot(slot, stack) && mergeItemStack(stack, slot, slot + 1, false)) {
                    moved = true;
                }
            }
        }
        return moved;
    }

    /** Shift + right-click on a lockable slot toggles its lock - see ContainerAdvancedPulverizer#slotClick. */
    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (mode == 1 && button == 1 && player.inventory.getItemStack() == null
                && slotId >= 0 && slotId < tile.getAugmentStart() && tile.lockIndexOf(slotId) >= 0) {
            if (!player.worldObj.isRemote) {
                tile.toggleLineLock(slotId);
            }
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] now = new int[last.length];
        now[ID_ENERGY] = tile.getEnergy();
        now[ID_MAX_ENERGY] = tile.getMaxEnergy();
        now[ID_RATE] = tile.getEnergyPerTick();
        now[ID_MAX_RATE] = tile.getMaxEnergyPerTick();
        for (int side = 0; side < 6; side++) {
            now[ID_SIDES + side] = tile.getSideMode(side);
        }
        now[ID_RECONFIG] = tile.augmentReconfigSides ? 1 : 0;
        now[ID_AUTO_IN] = tile.augmentAutoInput ? 1 : 0;
        now[ID_AUTO_OUT] = tile.augmentAutoOutput ? 1 : 0;
        now[ID_REDSTONE] = tile.augmentRedstoneControl ? 1 : 0;
        now[ID_CONTROL] = tile.getControl().ordinal();
        now[ID_MODE] = tile.getMachineMode();
        FluidStack fluid = tile.getTankFluid();
        now[ID_FLUID_ID] = fluid != null && fluid.amount > 0 ? FluidRegistry.getFluidID(fluid.getFluid()) : -1;
        now[ID_FLUID_AMOUNT] = fluid != null ? fluid.amount : 0;
        for (int line = 0; line < tile.getLineCount(); line++) {
            now[ID_PROGRESS + line * 4] = tile.getProgress(line);
            now[ID_PROGRESS + line * 4 + 2] = tile.getProgressMax(line);
        }

        List<ICrafting> list = (List<ICrafting>) crafters;
        for (int i = 0; i < list.size(); i++) {
            ICrafting crafter = list.get(i);
            sendWide(crafter, ID_ENERGY, now);
            sendWide(crafter, ID_MAX_ENERGY, now);
            sendWide(crafter, ID_RATE, now);
            sendWide(crafter, ID_MAX_RATE, now);
            for (int id = ID_SIDES; id <= ID_FLUID_ID; id++) {
                if (last[id] != now[id]) {
                    crafter.sendProgressBarUpdate(this, id, now[id]);
                }
            }
            sendWide(crafter, ID_FLUID_AMOUNT, now);
            for (int line = 0; line < tile.getLineCount(); line++) {
                sendWide(crafter, ID_PROGRESS + line * 4, now);
                sendWide(crafter, ID_PROGRESS + line * 4 + 2, now);
            }
        }
        System.arraycopy(now, 0, last, 0, now.length);
    }

    /** An int as two shorts at {@code id} and {@code id + 1}; {@code now[id + 1]} is unused. */
    private void sendWide(ICrafting crafter, int id, int[] now) {
        if (last[id] != now[id]) {
            crafter.sendProgressBarUpdate(this, id, now[id] & 0xFFFF);
            crafter.sendProgressBarUpdate(this, id + 1, now[id] >>> 16);
        }
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == ID_ENERGY || id == ID_ENERGY + 1) {
            tile.setEnergyHalfClient(id != ID_ENERGY, value);
        } else if (id == ID_MAX_ENERGY || id == ID_MAX_ENERGY + 1) {
            tile.setMaxEnergyHalfClient(id != ID_MAX_ENERGY, value);
        } else if (id == ID_RATE || id == ID_RATE + 1) {
            tile.setEnergyPerTickHalfClient(id != ID_RATE, value);
        } else if (id == ID_MAX_RATE || id == ID_MAX_RATE + 1) {
            tile.setMaxEnergyPerTickHalfClient(id != ID_MAX_RATE, value);
        } else if (id >= ID_SIDES && id < ID_SIDES + 6) {
            tile.setSideModeClient(id - ID_SIDES, value);
        } else if (id == ID_RECONFIG) {
            tile.augmentReconfigSides = value != 0;
        } else if (id == ID_AUTO_IN) {
            tile.augmentAutoInput = value != 0;
        } else if (id == ID_AUTO_OUT) {
            tile.augmentAutoOutput = value != 0;
        } else if (id == ID_REDSTONE) {
            tile.augmentRedstoneControl = value != 0;
        } else if (id == ID_CONTROL) {
            tile.setControlClient(value);
        } else if (id == ID_MODE) {
            tile.setMachineModeClient(value);
        } else if (id == ID_FLUID_ID) {
            // Sent as a short: -1 (no fluid) arrives intact, registry ids stay far below 32767.
            tile.setTankFluidIdClient((short) value);
        } else if (id == ID_FLUID_AMOUNT || id == ID_FLUID_AMOUNT + 1) {
            tile.setTankAmountHalfClient(id != ID_FLUID_AMOUNT, value);
        } else if (id >= ID_PROGRESS) {
            int offset = id - ID_PROGRESS;
            int line = offset / 4;
            int part = offset % 4;
            if (part < 2) {
                tile.setProgressHalfClient(line, part == 1, value);
            } else {
                tile.setProgressMaxHalfClient(line, part == 3, value);
            }
        }
    }
}
