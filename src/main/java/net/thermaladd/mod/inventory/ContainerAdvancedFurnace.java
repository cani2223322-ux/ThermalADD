package net.thermaladd.mod.inventory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;

import cofh.api.energy.IEnergyContainerItem;
import cofh.lib.gui.slot.SlotEnergy;
import cofh.thermalexpansion.util.crafting.FurnaceManager;

/** Same layout family as {@link ContainerAdvancedPulverizer}, minus the secondary-output slot this machine has no use for. */
public class ContainerAdvancedFurnace extends Container {

    public static final int SLOT_SIZE = 18;

    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 17;

    /** One output slot per input line (3), stacked in its own column exactly like the input column. */
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 17;

    public static final int PLAYER_INV_Y = 92;
    public static final int PLAYER_HOTBAR_Y = 150;

    /** See ContainerAdvancedPulverizer.CHARGE_X/CHARGE_Y - real TE's own charge-slot offset from its energy bar's origin. */
    public static final int CHARGE_X = 8;
    public static final int CHARGE_Y = 17 + 45;

    private static final int PARKED = -1000;

    private static final int PLAYER_INV_START = TileAdvancedFurnace.TOTAL_SLOTS;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_START + 36;

    private final TileAdvancedFurnace tile;
    private final Slot[] augmentSlots = new Slot[TileAdvancedFurnace.AUGMENT_SLOTS];

    private int lastEnergy = -1;
    private int lastMaxEnergy = -1;
    private final int[] lastProgress = new int[TileAdvancedFurnace.INPUT_SLOTS];
    private final int[] lastProgressMax = new int[TileAdvancedFurnace.INPUT_SLOTS];
    private final int[] lastSideModes = new int[6];
    private int lastReconfigSides = -1;
    private int lastAutoInput = -1;
    private int lastAutoOutput = -1;
    private int lastRedstoneControl = -1;
    private int lastEnergyPerTick = -1;
    private int lastMaxEnergyPerTick = -1;
    private int lastControlMode = -1;

    public ContainerAdvancedFurnace(InventoryPlayer playerInv, TileAdvancedFurnace tile) {
        this.tile = tile;

        for (int i = 0; i < TileAdvancedFurnace.INPUT_SLOTS; i++) {
            addSlotToContainer(new SlotFurnaceInput(tile, TileAdvancedFurnace.INPUT_START + i,
                    INPUT_X, INPUT_Y + i * SLOT_SIZE));
        }
        for (int i = 0; i < TileAdvancedFurnace.OUTPUT_SLOTS; i++) {
            addSlotToContainer(new Slot(tile, TileAdvancedFurnace.OUTPUT_START + i,
                    OUTPUT_X, OUTPUT_Y + i * SLOT_SIZE));
        }

        for (int i = 0; i < TileAdvancedFurnace.AUGMENT_SLOTS; i++) {
            augmentSlots[i] = new SlotAugmentFurnace(tile, TileAdvancedFurnace.AUGMENT_START + i, PARKED, PARKED);
            addSlotToContainer(augmentSlots[i]);
        }

        addSlotToContainer(new SlotEnergy(tile, TileAdvancedFurnace.CHARGE_SLOT, CHARGE_X, CHARGE_Y));

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

            if (slotIndex < TileAdvancedFurnace.TOTAL_SLOTS) {
                if (!mergeItemStack(stackInSlot, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return null;
                }
            } else if (TileAdvancedFurnace.isValidAugment(stackInSlot)) {
                if (!mergeItemStack(stackInSlot, TileAdvancedFurnace.AUGMENT_START,
                        TileAdvancedFurnace.AUGMENT_START + TileAdvancedFurnace.AUGMENT_SLOTS, false)) {
                    return null;
                }
            } else if (stackInSlot.getItem() instanceof IEnergyContainerItem) {
                if (!mergeItemStack(stackInSlot, TileAdvancedFurnace.CHARGE_SLOT, TileAdvancedFurnace.CHARGE_SLOT + 1, false)) {
                    return null;
                }
            } else if (FurnaceManager.recipeExists(stackInSlot)) {
                if (!mergeItemStack(stackInSlot, TileAdvancedFurnace.INPUT_START,
                        TileAdvancedFurnace.OUTPUT_START, false)) {
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
        int energyScaled = tile.getEnergy() / TileAdvancedFurnace.ENERGY_SYNC_SCALE;
        int maxEnergyScaled = tile.getMaxEnergy() / TileAdvancedFurnace.ENERGY_SYNC_SCALE;
        int energyPerTickScaled = tile.getEnergyPerTick() / TileAdvancedFurnace.RATE_SYNC_SCALE;
        int maxEnergyPerTickScaled = tile.getMaxEnergyPerTick() / TileAdvancedFurnace.RATE_SYNC_SCALE;
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
            for (int line = 0; line < TileAdvancedFurnace.INPUT_SLOTS; line++) {
                int p = tile.getProgress(line);
                int pMax = tile.getProgressMax(line);
                if (lastProgress[line] != p) {
                    crafter.sendProgressBarUpdate(this, 2 + line, p);
                }
                if (lastProgressMax[line] != pMax) {
                    crafter.sendProgressBarUpdate(this, 5 + line, pMax);
                }
            }
            for (int side = 0; side < 6; side++) {
                int mode = tile.getSideMode(side);
                if (lastSideModes[side] != mode) {
                    crafter.sendProgressBarUpdate(this, 8 + side, mode);
                }
            }
            if (lastReconfigSides != reconfigSides) {
                crafter.sendProgressBarUpdate(this, 14, reconfigSides);
            }
            if (lastAutoInput != autoInput) {
                crafter.sendProgressBarUpdate(this, 15, autoInput);
            }
            if (lastAutoOutput != autoOutput) {
                crafter.sendProgressBarUpdate(this, 16, autoOutput);
            }
            if (lastRedstoneControl != redstoneControl) {
                crafter.sendProgressBarUpdate(this, 17, redstoneControl);
            }
            if (lastEnergyPerTick != energyPerTickScaled) {
                crafter.sendProgressBarUpdate(this, 18, energyPerTickScaled);
            }
            if (lastMaxEnergyPerTick != maxEnergyPerTickScaled) {
                crafter.sendProgressBarUpdate(this, 19, maxEnergyPerTickScaled);
            }
            int controlMode = tile.getControl().ordinal();
            if (lastControlMode != controlMode) {
                crafter.sendProgressBarUpdate(this, 20, controlMode);
            }
        }

        lastEnergy = energyScaled;
        lastMaxEnergy = maxEnergyScaled;
        for (int line = 0; line < TileAdvancedFurnace.INPUT_SLOTS; line++) {
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
        lastEnergyPerTick = energyPerTickScaled;
        lastMaxEnergyPerTick = maxEnergyPerTickScaled;
        lastControlMode = tile.getControl().ordinal();
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            tile.setEnergyStoredClient(value);
        } else if (id == 1) {
            tile.setMaxEnergyClient(value * TileAdvancedFurnace.ENERGY_SYNC_SCALE);
        } else if (id >= 2 && id <= 4) {
            tile.setProgressClient(id - 2, value);
        } else if (id >= 5 && id <= 7) {
            tile.setProgressMaxClient(id - 5, value);
        } else if (id >= 8 && id <= 13) {
            tile.setSideModeClient(id - 8, value);
        } else if (id == 14) {
            tile.augmentReconfigSides = value != 0;
        } else if (id == 15) {
            tile.augmentAutoInput = value != 0;
        } else if (id == 16) {
            tile.augmentAutoOutput = value != 0;
        } else if (id == 17) {
            tile.augmentRedstoneControl = value != 0;
        } else if (id == 18) {
            tile.setEnergyPerTickClient(value);
        } else if (id == 19) {
            tile.setMaxEnergyPerTickClient(value);
        } else if (id == 20) {
            tile.setControlClient(value);
        }
    }
}
