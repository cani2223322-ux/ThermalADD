package net.thermaladd.mod.inventory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

import cofh.api.energy.IEnergyContainerItem;
import cofh.lib.gui.slot.SlotEnergy;
import cofh.thermalexpansion.util.crafting.PulverizerManager;

/**
 * Wires up the 3 input slots, the 2+1 output slots, the 9 augment slots and the player's
 * own inventory, and keeps the client in sync using vanilla's own windowProperty channel
 * (Container#sendProgressBarUpdate / #updateProgressBar) - the same lightweight approach the
 * sibling ImprovedAssembler mod uses, no custom network packets required for state sync (a
 * packet IS used, separately, for the Configuration tab's own clicks - see MessageCycleSide).
 * windowProperty values are transmitted as shorts, which is why energy values are divided by
 * TileAdvancedPulverizer.ENERGY_SYNC_SCALE before sending (matches TileAdvancedPulverizer's
 * setEnergyStoredClient/setMaxEnergyClient, which multiply back by the same scale on the way in).
 */
public class ContainerAdvancedPulverizer extends Container {

    public static final int SLOT_SIZE = 18;

    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 17;

    /** One primary-output slot per input line (3), stacked in its own column exactly like the input column. */
    public static final int OUTPUT_PRIMARY_X = 116;
    public static final int OUTPUT_PRIMARY_Y = 17;

    /** Bottom-right corner of the panel: its own column right next to primary output, only 2 rows tall. */
    public static final int OUTPUT_SECONDARY_X = 134;
    public static final int OUTPUT_SECONDARY_Y = 35;

    public static final int PLAYER_INV_Y = 92;
    public static final int PLAYER_HOTBAR_Y = 150;

    /** Real TE's own charge-slot offset from its energy bar's origin (bar at (8,8), slot at (8,53)) - see GuiAdvancedPulverizer's ENERGY_X/ENERGY_Y for the bar itself. */
    public static final int CHARGE_X = 8;
    public static final int CHARGE_Y = 17 + 45;

    /** Parked position for the augment Slots while their tab is closed (off-screen, like TE's own trick). */
    private static final int PARKED = -1000;

    private static final int PLAYER_INV_START = TileAdvancedPulverizer.TOTAL_SLOTS;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_START + 36;

    private final TileAdvancedPulverizer tile;
    private final Slot[] augmentSlots = new Slot[TileAdvancedPulverizer.AUGMENT_SLOTS];

    private int lastEnergy = -1;
    private int lastMaxEnergy = -1;
    private final int[] lastProgress = new int[TileAdvancedPulverizer.INPUT_SLOTS];
    private final int[] lastProgressMax = new int[TileAdvancedPulverizer.INPUT_SLOTS];
    {
        // -1 = "never sent", so a freshly opened GUI always receives every line. Starting at 0
        // left a line that had gone idle since the GUI was last open showing its old half-full
        // arrow: the server's 0 equalled the cached 0, so it was never sent.
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

    public ContainerAdvancedPulverizer(InventoryPlayer playerInv, TileAdvancedPulverizer tile) {
        this.tile = tile;

        for (int i = 0; i < TileAdvancedPulverizer.INPUT_SLOTS; i++) {
            addSlotToContainer(new SlotPulverizerInput(tile, TileAdvancedPulverizer.INPUT_START + i,
                    INPUT_X, INPUT_Y + i * SLOT_SIZE));
        }
        for (int i = 0; i < TileAdvancedPulverizer.OUTPUT_PRIMARY_SLOTS; i++) {
            addSlotToContainer(new Slot(tile, TileAdvancedPulverizer.OUTPUT_PRIMARY_START + i,
                    OUTPUT_PRIMARY_X, OUTPUT_PRIMARY_Y + i * SLOT_SIZE));
        }
        for (int i = 0; i < TileAdvancedPulverizer.OUTPUT_SECONDARY_SLOTS; i++) {
            addSlotToContainer(new Slot(tile, TileAdvancedPulverizer.OUTPUT_SECONDARY_START + i,
                    OUTPUT_SECONDARY_X, OUTPUT_SECONDARY_Y + i * SLOT_SIZE));
        }

        // 9 augment slots - same design/functionality as Thermal Expansion's TileAugmentable,
        // just more of them since this block has no separate tier/upgrade item of its own.
        // Parked off-screen; GuiAdvancedPulverizer's augment tab moves them into view while open.
        for (int i = 0; i < TileAdvancedPulverizer.AUGMENT_SLOTS; i++) {
            augmentSlots[i] = new SlotAugment(tile, TileAdvancedPulverizer.AUGMENT_START + i, PARKED, PARKED);
            addSlotToContainer(augmentSlots[i]);
        }

        addSlotToContainer(new SlotEnergy(tile, TileAdvancedPulverizer.CHARGE_SLOT, CHARGE_X, CHARGE_Y));

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

            if (slotIndex < TileAdvancedPulverizer.TOTAL_SLOTS) {
                if (!mergeItemStack(stackInSlot, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return null;
                }
            } else if (TileAdvancedPulverizer.isValidAugment(stackInSlot)) {
                if (!mergeItemStack(stackInSlot, TileAdvancedPulverizer.AUGMENT_START,
                        TileAdvancedPulverizer.AUGMENT_START + TileAdvancedPulverizer.AUGMENT_SLOTS, false)) {
                    return null;
                }
            } else if (stackInSlot.getItem() instanceof IEnergyContainerItem) {
                if (!mergeItemStack(stackInSlot, TileAdvancedPulverizer.CHARGE_SLOT, TileAdvancedPulverizer.CHARGE_SLOT + 1, false)) {
                    return null;
                }
            } else if (PulverizerManager.recipeExists(stackInSlot)) {
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

    /**
     * Shift-click into the processing lines, honouring line locks. Vanilla mergeItemStack never
     * asks a slot whether an item is valid, so merging over the whole input range would drop
     * anything into a line locked to something else. Three passes, in order: top up a line that
     * already holds the item, then fill an empty line locked to it, then any empty free line.
     */
    private boolean mergeIntoLines(ItemStack stack) {
        boolean moved = false;
        for (int pass = 0; pass < 3 && stack.stackSize > 0; pass++) {
            for (int i = 0; i < TileAdvancedPulverizer.INPUT_SLOTS && stack.stackSize > 0; i++) {
                int slot = TileAdvancedPulverizer.INPUT_START + i;
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

    /**
     * Shift + right-click on a line's input slot with nothing on the cursor toggles that line's
     * lock instead of quick-moving the stack out (shift + left-click still quick-moves). Runs on
     * both sides, like every slot click; only the server changes anything, and both return null,
     * so the server does not see a mismatch and resend the inventory.
     */
    @Override
    public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
        if (mode == 1 && button == 1 && player.inventory.getItemStack() == null
                && slotId >= TileAdvancedPulverizer.INPUT_START
                && slotId < TileAdvancedPulverizer.INPUT_START + TileAdvancedPulverizer.INPUT_SLOTS) {
            if (!player.worldObj.isRemote) {
                tile.toggleLineLock(slotId - TileAdvancedPulverizer.INPUT_START);
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
        // Sent as exact low/high 16-bit halves rather than divided by a scale - see
        // TileAdvancedPulverizer#applyClientEnergy for why.
        int energy = tile.getEnergy();
        int maxEnergy = tile.getMaxEnergy();
        int energyPerTick = tile.getEnergyPerTick();
        int maxEnergyPerTick = tile.getMaxEnergyPerTick();
        int reconfigSides = tile.augmentReconfigSides ? 1 : 0;
        int autoInput = tile.augmentAutoInput ? 1 : 0;
        int autoOutput = tile.augmentAutoOutput ? 1 : 0;
        int redstoneControl = tile.augmentRedstoneControl ? 1 : 0;

        for (int i = 0; i < list.size(); i++) {
            ICrafting crafter = list.get(i);
            if (lastEnergy != energy) {
                crafter.sendProgressBarUpdate(this, 0, energy & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 21, energy >>> 16);
            }
            if (lastMaxEnergy != maxEnergy) {
                crafter.sendProgressBarUpdate(this, 1, maxEnergy & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 22, maxEnergy >>> 16);
            }
            for (int line = 0; line < TileAdvancedPulverizer.INPUT_SLOTS; line++) {
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
            if (lastEnergyPerTick != energyPerTick) {
                crafter.sendProgressBarUpdate(this, 18, energyPerTick & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 23, energyPerTick >>> 16);
            }
            if (lastMaxEnergyPerTick != maxEnergyPerTick) {
                crafter.sendProgressBarUpdate(this, 19, maxEnergyPerTick & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 24, maxEnergyPerTick >>> 16);
            }
            int controlMode = tile.getControl().ordinal();
            if (lastControlMode != controlMode) {
                crafter.sendProgressBarUpdate(this, 20, controlMode);
            }
        }

        lastEnergy = energy;
        lastMaxEnergy = maxEnergy;
        for (int line = 0; line < TileAdvancedPulverizer.INPUT_SLOTS; line++) {
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
        lastEnergyPerTick = energyPerTick;
        lastMaxEnergyPerTick = maxEnergyPerTick;
        lastControlMode = tile.getControl().ordinal();
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            tile.setEnergyLowClient(value);
        } else if (id == 1) {
            tile.setMaxEnergyLowClient(value);
        } else if (id == 21) {
            tile.setEnergyHighClient(value);
        } else if (id == 22) {
            tile.setMaxEnergyHighClient(value);
        } else if (id == 23) {
            tile.setEnergyPerTickHighClient(value);
        } else if (id == 24) {
            tile.setMaxEnergyPerTickHighClient(value);
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
            tile.setEnergyPerTickLowClient(value);
        } else if (id == 19) {
            tile.setMaxEnergyPerTickLowClient(value);
        } else if (id == 20) {
            tile.setControlClient(value);
        }
    }
}
