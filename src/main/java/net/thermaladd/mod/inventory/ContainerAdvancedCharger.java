package net.thermaladd.mod.inventory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;

import cofh.api.energy.IEnergyContainerItem;
import cofh.lib.gui.slot.SlotEnergy;
import cofh.thermalexpansion.util.crafting.ChargerManager;

/**
 * 3x3 grid of (line -> output) slot pairs instead of the Pulverizer/Furnace/Sawmill's own
 * column layout - 9 lines needs more horizontal room than 3 does, see GuiAdvancedCharger's own
 * layout constants for the exact positions this mirrors.
 */
public class ContainerAdvancedCharger extends Container {

    public static final int SLOT_SIZE = 18;
    public static final int COLS = 3;

    /** Line (input/charging) slot X per column - see GuiAdvancedCharger for the matching visual layout. */
    public static final int[] LINE_X = {32, 72, 112};
    public static final int[] ROW_Y = {17, 37, 57};
    /** The output slot for a given line sits this many pixels to the right of its own line slot. */
    public static final int OUTPUT_OFFSET = 20;

    public static final int PLAYER_INV_Y = 92;
    public static final int PLAYER_HOTBAR_Y = 150;

    public static final int CHARGE_X = 8;
    public static final int CHARGE_Y = 17 + 45;

    private static final int PARKED = -1000;

    private static final int PLAYER_INV_START = TileAdvancedCharger.TOTAL_SLOTS;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_START + 36;

    private final TileAdvancedCharger tile;
    private final Slot[] augmentSlots = new Slot[TileAdvancedCharger.AUGMENT_SLOTS];

    private int lastEnergy = -1;
    private int lastMaxEnergy = -1;
    private final int[] lastProgress = new int[TileAdvancedCharger.LINE_SLOTS];
    private final int[] lastProgressMax = new int[TileAdvancedCharger.LINE_SLOTS];
    /** High halves of each line's progress / progressMax; the low halves keep ids 2-10 / 11-19. */
    private static final int PROGRESS_HIGH_ID = 37;
    private static final int PROGRESS_MAX_HIGH_ID = PROGRESS_HIGH_ID + TileAdvancedCharger.LINE_SLOTS;
    {
        // -1 = "never sent" - see ContainerAdvancedPulverizer.
        java.util.Arrays.fill(lastProgress, -1);
        java.util.Arrays.fill(lastProgressMax, -1);
    }
    private final int[] lastSideModes = new int[6];
    private int lastReconfigSides = -1;
    private int lastAutoInput = -1;
    private int lastAutoOutput = -1;
    private int lastRedstoneControl = -1;
    private int lastEnergyPerTick = -1;
    private int lastMaxEnergyPerTick = -1;
    private int lastControlMode = -1;

    public ContainerAdvancedCharger(InventoryPlayer playerInv, TileAdvancedCharger tile) {
        this.tile = tile;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                int line = row * COLS + col;
                addSlotToContainer(new SlotChargerInput(tile, TileAdvancedCharger.LINE_START + line,
                        LINE_X[col], ROW_Y[row]));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                int line = row * COLS + col;
                addSlotToContainer(new Slot(tile, TileAdvancedCharger.OUTPUT_START + line,
                        LINE_X[col] + OUTPUT_OFFSET, ROW_Y[row]));
            }
        }

        for (int i = 0; i < TileAdvancedCharger.AUGMENT_SLOTS; i++) {
            augmentSlots[i] = new SlotAugmentCharger(tile, TileAdvancedCharger.AUGMENT_START + i, PARKED, PARKED);
            addSlotToContainer(augmentSlots[i]);
        }

        addSlotToContainer(new SlotEnergy(tile, TileAdvancedCharger.CHARGE_SLOT, CHARGE_X, CHARGE_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * SLOT_SIZE, PLAYER_INV_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * SLOT_SIZE, PLAYER_HOTBAR_Y));
        }
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
        ItemStack result = null;
        Slot slot = (Slot) inventorySlots.get(slotIndex);

        if (slot != null && slot.getHasStack()) {
            ItemStack stackInSlot = slot.getStack();
            result = stackInSlot.copy();

            if (slotIndex < TileAdvancedCharger.TOTAL_SLOTS) {
                if (!mergeItemStack(stackInSlot, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return null;
                }
            } else if (TileAdvancedCharger.isValidAugment(stackInSlot)) {
                if (!mergeItemStack(stackInSlot, TileAdvancedCharger.AUGMENT_START,
                        TileAdvancedCharger.AUGMENT_START + TileAdvancedCharger.AUGMENT_SLOTS, false)) {
                    return null;
                }
            } else if (stackInSlot.getItem() instanceof IEnergyContainerItem
                    || ChargerManager.recipeExists(stackInSlot)) {
                // A real RF-storing item shift-clicked from the player's inventory could in
                // principle also fit the machine's own self-fuel CHARGE_SLOT, but the 9 lines
                // are this machine's whole reason to exist - route it there first, same
                // priority order the other 4 machines already give their own charge slot only
                // once nothing more specific claims the stack.
                if (!mergeIntoLines(stackInSlot)) {
                    return null;
                }
            } else {
                return null;
            }

            if (stackInSlot.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }
        }

        return result;
    }

    /** Shift-click into the lines, honouring line locks - see ContainerAdvancedPulverizer#mergeIntoLines. */
    private boolean mergeIntoLines(ItemStack stack) {
        boolean moved = false;
        for (int pass = 0; pass < 3 && stack.stackSize > 0; pass++) {
            for (int i = 0; i < TileAdvancedCharger.LINE_SLOTS && stack.stackSize > 0; i++) {
                int slot = TileAdvancedCharger.LINE_START + i;
                boolean occupied = tile.getStackInSlot(slot) != null;
                boolean locked = tile.getLineLocks().isLocked(i);
                boolean inThisPass = pass == 0 ? occupied : !occupied && (pass == 1) == locked;
                if (inThisPass && tile.isItemValidForSlot(slot, stack) && mergeItemStack(stack, slot, slot + 1, false)) {
                    moved = true;
                }
            }
        }
        return moved;
    }

    /** Shift + right-click on a line's input slot toggles its lock - see ContainerAdvancedPulverizer#slotClick. */
    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (mode == 1 && button == 1 && player.inventory.getItemStack() == null
                && slotId >= TileAdvancedCharger.LINE_START
                && slotId < TileAdvancedCharger.LINE_START + TileAdvancedCharger.LINE_SLOTS) {
            if (!player.worldObj.isRemote) {
                tile.toggleLineLock(slotId - TileAdvancedCharger.LINE_START);
            }
            return null;
        }
        return super.slotClick(slotId, button, mode, player);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        List<ICrafting> list = (List<ICrafting>) crafters;
        // Sent as exact low/high 16-bit halves - see TileAdvancedPulverizer#applyClientEnergy.
        int energy = tile.getEnergy();
        int maxEnergy = tile.getMaxEnergy();
        int reconfigSides = tile.augmentReconfigSides ? 1 : 0;
        int autoInput = tile.augmentAutoInput ? 1 : 0;
        int autoOutput = tile.augmentAutoOutput ? 1 : 0;
        int redstoneControl = tile.augmentRedstoneControl ? 1 : 0;

        for (int i = 0; i < list.size(); i++) {
            ICrafting crafter = list.get(i);
            if (lastEnergy != energy) {
                crafter.sendProgressBarUpdate(this, 0, energy & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 33, energy >>> 16);
            }
            if (lastMaxEnergy != maxEnergy) {
                crafter.sendProgressBarUpdate(this, 1, maxEnergy & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 34, maxEnergy >>> 16);
            }
            for (int line = 0; line < TileAdvancedCharger.LINE_SLOTS; line++) {
                // A charging line's progress is the charged item's own RF - millions for a real
                // Capacitor. It used to be divided by a scale of 1024 to fit the signed-short
                // window property, which still wrapped negative for any item past
                // 32,767 * 1024 = 33.5M RF. Sent as exact low/high halves instead, the same as
                // the energy readouts (see TileAdvancedPulverizer#applyClientEnergy).
                int p = tile.getProgress(line);
                int pMax = tile.getProgressMax(line);
                if (lastProgress[line] != p) {
                    crafter.sendProgressBarUpdate(this, 2 + line, p & 0xFFFF);
                    crafter.sendProgressBarUpdate(this, PROGRESS_HIGH_ID + line, p >>> 16);
                }
                if (lastProgressMax[line] != pMax) {
                    crafter.sendProgressBarUpdate(this, 11 + line, pMax & 0xFFFF);
                    crafter.sendProgressBarUpdate(this, PROGRESS_MAX_HIGH_ID + line, pMax >>> 16);
                }
            }
            for (int side = 0; side < 6; side++) {
                int mode = tile.getSideMode(side);
                if (lastSideModes[side] != mode) {
                    crafter.sendProgressBarUpdate(this, 20 + side, mode);
                }
            }
            if (lastReconfigSides != reconfigSides) {
                crafter.sendProgressBarUpdate(this, 26, reconfigSides);
            }
            if (lastAutoInput != autoInput) {
                crafter.sendProgressBarUpdate(this, 27, autoInput);
            }
            if (lastAutoOutput != autoOutput) {
                crafter.sendProgressBarUpdate(this, 28, autoOutput);
            }
            if (lastRedstoneControl != redstoneControl) {
                crafter.sendProgressBarUpdate(this, 29, redstoneControl);
            }
            // Unlike the other 3 machines (whose own maxEnergyPerTick tops out in the low
            // thousands, safely under the windowProperty short limit on its own), this tile's
            // own 9 parallel lines at up to BASE_ENERGY_PER_TICK=16,000 RF/t each already exceed
            // 32,767 with ZERO augments installed (9 * 16,000 = 144,000) - needs the same
            // ENERGY_SYNC_SCALE division ids 0/1 already use.
            int energyPerTick = tile.getEnergyPerTick();
            if (lastEnergyPerTick != energyPerTick) {
                crafter.sendProgressBarUpdate(this, 30, energyPerTick & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 35, energyPerTick >>> 16);
            }
            int maxEnergyPerTick = tile.getMaxEnergyPerTick();
            if (lastMaxEnergyPerTick != maxEnergyPerTick) {
                crafter.sendProgressBarUpdate(this, 31, maxEnergyPerTick & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 36, maxEnergyPerTick >>> 16);
            }
            int controlMode = tile.getControl().ordinal();
            if (lastControlMode != controlMode) {
                crafter.sendProgressBarUpdate(this, 32, controlMode);
            }
        }

        lastEnergy = energy;
        lastMaxEnergy = maxEnergy;
        for (int line = 0; line < TileAdvancedCharger.LINE_SLOTS; line++) {
            lastProgress[line] = tile.getProgress(line);
            lastProgressMax[line] = tile.getProgressMax(line);
        }
        for (int side = 0; side < 6; side++) {
            lastSideModes[side] = tile.getSideMode(side);
        }
        lastReconfigSides = reconfigSides;
        lastAutoInput = autoInput;
        lastAutoOutput = autoOutput;
        lastRedstoneControl = redstoneControl;
        lastEnergyPerTick = tile.getEnergyPerTick();
        lastMaxEnergyPerTick = tile.getMaxEnergyPerTick();
        lastControlMode = tile.getControl().ordinal();
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            tile.setEnergyLowClient(value);
        } else if (id == 1) {
            tile.setMaxEnergyLowClient(value);
        } else if (id == 33) {
            tile.setEnergyHighClient(value);
        } else if (id == 34) {
            tile.setMaxEnergyHighClient(value);
        } else if (id == 35) {
            tile.setEnergyPerTickHighClient(value);
        } else if (id == 36) {
            tile.setMaxEnergyPerTickHighClient(value);
        } else if (id >= PROGRESS_HIGH_ID && id < PROGRESS_HIGH_ID + TileAdvancedCharger.LINE_SLOTS) {
            tile.setProgressHighClient(id - PROGRESS_HIGH_ID, value);
        } else if (id >= PROGRESS_MAX_HIGH_ID && id < PROGRESS_MAX_HIGH_ID + TileAdvancedCharger.LINE_SLOTS) {
            tile.setProgressMaxHighClient(id - PROGRESS_MAX_HIGH_ID, value);
        } else if (id >= 2 && id <= 10) {
            tile.setProgressClient(id - 2, value);
        } else if (id >= 11 && id <= 19) {
            tile.setProgressMaxClient(id - 11, value);
        } else if (id >= 20 && id <= 25) {
            tile.setSideModeClient(id - 20, value);
        } else if (id == 26) {
            tile.augmentReconfigSides = value != 0;
        } else if (id == 27) {
            tile.augmentAutoInput = value != 0;
        } else if (id == 28) {
            tile.augmentAutoOutput = value != 0;
        } else if (id == 29) {
            tile.augmentRedstoneControl = value != 0;
        } else if (id == 30) {
            tile.setEnergyPerTickLowClient(value);
        } else if (id == 31) {
            tile.setMaxEnergyPerTickLowClient(value);
        } else if (id == 32) {
            tile.setControlClient(value);
        }
    }
}
