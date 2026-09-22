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
                if (!mergeItemStack(stackInSlot, TileAdvancedCharger.LINE_START,
                        TileAdvancedCharger.OUTPUT_START, false)) {
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

    @SuppressWarnings("unchecked")
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        List<ICrafting> list = (List<ICrafting>) crafters;
        int energyScaled = tile.getEnergy() / TileAdvancedCharger.ENERGY_SYNC_SCALE;
        int maxEnergyScaled = tile.getMaxEnergy() / TileAdvancedCharger.ENERGY_SYNC_SCALE;
        int reconfigSides = tile.augmentReconfigSides ? 1 : 0;
        int autoInput = tile.augmentAutoInput ? 1 : 0;
        int autoOutput = tile.augmentAutoOutput ? 1 : 0;
        int redstoneControl = tile.augmentRedstoneControl ? 1 : 0;

        for (int i = 0; i < list.size(); i++) {
            ICrafting crafter = list.get(i);
            if (lastEnergy != energyScaled) {
                crafter.sendProgressBarUpdate(this, 0, energyScaled);
            }
            if (lastMaxEnergy != maxEnergyScaled) {
                crafter.sendProgressBarUpdate(this, 1, maxEnergyScaled);
            }
            for (int line = 0; line < TileAdvancedCharger.LINE_SLOTS; line++) {
                // See TileAdvancedCharger#setProgressClient's own doc for why this needs the
                // same ENERGY_SYNC_SCALE division the main energy bar already needs - a
                // charging line's progress/progressMax can be as large as a real Capacitor's
                // own RF capacity (millions), which overflows the windowProperty short on its
                // own otherwise.
                int p = tile.getProgress(line) / TileAdvancedCharger.ENERGY_SYNC_SCALE;
                int pMax = tile.getProgressMax(line) / TileAdvancedCharger.ENERGY_SYNC_SCALE;
                if (lastProgress[line] != p) {
                    crafter.sendProgressBarUpdate(this, 2 + line, p);
                }
                if (lastProgressMax[line] != pMax) {
                    crafter.sendProgressBarUpdate(this, 11 + line, pMax);
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
            int energyPerTick = tile.getEnergyPerTick() / TileAdvancedCharger.ENERGY_SYNC_SCALE;
            if (lastEnergyPerTick != energyPerTick) {
                crafter.sendProgressBarUpdate(this, 30, energyPerTick);
            }
            int maxEnergyPerTick = tile.getMaxEnergyPerTick() / TileAdvancedCharger.ENERGY_SYNC_SCALE;
            if (lastMaxEnergyPerTick != maxEnergyPerTick) {
                crafter.sendProgressBarUpdate(this, 31, maxEnergyPerTick);
            }
            int controlMode = tile.getControl().ordinal();
            if (lastControlMode != controlMode) {
                crafter.sendProgressBarUpdate(this, 32, controlMode);
            }
        }

        lastEnergy = energyScaled;
        lastMaxEnergy = maxEnergyScaled;
        for (int line = 0; line < TileAdvancedCharger.LINE_SLOTS; line++) {
            // Must store the same SCALED value the comparison above uses (see the /
            // ENERGY_SYNC_SCALE division a few lines up) - storing the raw unscaled value here
            // would make the two almost never match, forcing a redundant resend every tick even
            // when nothing actually changed.
            lastProgress[line] = tile.getProgress(line) / TileAdvancedCharger.ENERGY_SYNC_SCALE;
            lastProgressMax[line] = tile.getProgressMax(line) / TileAdvancedCharger.ENERGY_SYNC_SCALE;
        }
        for (int side = 0; side < 6; side++) {
            lastSideModes[side] = tile.getSideMode(side);
        }
        lastReconfigSides = reconfigSides;
        lastAutoInput = autoInput;
        lastAutoOutput = autoOutput;
        lastRedstoneControl = redstoneControl;
        lastEnergyPerTick = tile.getEnergyPerTick() / TileAdvancedCharger.ENERGY_SYNC_SCALE;
        lastMaxEnergyPerTick = tile.getMaxEnergyPerTick() / TileAdvancedCharger.ENERGY_SYNC_SCALE;
        lastControlMode = tile.getControl().ordinal();
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            tile.setEnergyStoredClient(value);
        } else if (id == 1) {
            tile.setMaxEnergyClient(value * TileAdvancedCharger.ENERGY_SYNC_SCALE);
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
            tile.setEnergyPerTickClient(value);
        } else if (id == 31) {
            tile.setMaxEnergyPerTickClient(value);
        } else if (id == 32) {
            tile.setControlClient(value);
        }
    }
}
