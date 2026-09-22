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
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

import cofh.api.energy.IEnergyContainerItem;
import cofh.lib.gui.slot.SlotEnergy;

public class ContainerImprovedAssembler extends Container {

    // shared layout constants - GuiImprovedAssembler and the placeholder texture
    // generator mirror these numbers, keep them in sync if changed.
    public static final int[] PAIR_X = {8, 68};
    public static final int[] ROW_Y = {17, 37, 57};
    public static final int SLOT_SIZE = 18;
    public static final int OUTPUT_OFFSET = 34;

    public static final int BUFFER_X = 8;
    public static final int BUFFER_Y = 80;

    public static final int PLAYER_INV_Y = 136;
    public static final int PLAYER_HOTBAR_Y = 194;

    /** See ContainerAdvancedPulverizer.CHARGE_X/CHARGE_Y - real TE's own charge-slot offset from its energy bar's origin (GuiImprovedAssembler's own ENERGY_X/ENERGY_Y). */
    public static final int CHARGE_X = 150;
    public static final int CHARGE_Y = 17 + 45;

    /** Parked position for the augment Slots while their tab is closed (off-screen, like TE's own trick). */
    private static final int PARKED = -1000;

    private final TileImprovedAssembler tile;
    private final Slot[] augmentSlots = new Slot[TileImprovedAssembler.AUGMENT_SLOTS];

    private static final int PLAYER_INV_START = TileImprovedAssembler.TOTAL_SLOTS;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_START + 36;

    private int lastEnergy = -1;
    private final int[] lastSideModes = new int[6];
    private int lastReconfigSides = -1;
    private int lastAutoInput = -1;
    private int lastAutoOutput = -1;
    private int lastEnergyPerTick = -1;
    private int lastRedstoneControl = -1;
    private int lastControlMode = -1;
    private int lastFluidId = -1;
    private int lastFluidAmount = -1;

    public ContainerImprovedAssembler(InventoryPlayer playerInv, TileImprovedAssembler tile) {
        this.tile = tile;

        // schematic + output pairs: 2 columns x 3 rows = 6 pairs
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                addSlotToContainer(new SlotSchematic(tile, TileImprovedAssembler.SCHEMATIC_START + index,
                        PAIR_X[col], ROW_Y[row]));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                addSlotToContainer(new SlotOutput(tile, TileImprovedAssembler.OUTPUT_START + index,
                        PAIR_X[col] + OUTPUT_OFFSET, ROW_Y[row]));
            }
        }

        // material buffer: 9 columns x 2 rows
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                addSlotToContainer(new Slot(tile, TileImprovedAssembler.INPUT_START + index,
                        BUFFER_X + col * SLOT_SIZE, BUFFER_Y + row * SLOT_SIZE));
            }
        }

        // augment slots: same design/functionality as Thermal Expansion's TileAugmentable - 3 slots.
        // Parked off-screen; GuiImprovedAssembler's augment tab moves them into view while open.
        for (int i = 0; i < TileImprovedAssembler.AUGMENT_SLOTS; i++) {
            augmentSlots[i] = new SlotAugmentAssembler(tile, TileImprovedAssembler.AUGMENT_START + i, PARKED, PARKED);
            addSlotToContainer(augmentSlots[i]);
        }

        addSlotToContainer(new SlotEnergy(tile, TileImprovedAssembler.CHARGE_SLOT, CHARGE_X, CHARGE_Y));

        // player inventory
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

            if (slotIndex < TileImprovedAssembler.TOTAL_SLOTS) {
                if (!mergeItemStack(stackInSlot, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return null;
                }
            } else if (TileImprovedAssembler.isValidAugment(stackInSlot)) {
                if (!mergeItemStack(stackInSlot, TileImprovedAssembler.AUGMENT_START,
                        TileImprovedAssembler.AUGMENT_START + TileImprovedAssembler.AUGMENT_SLOTS, false)) {
                    return null;
                }
            } else if (stackInSlot.getItem() instanceof IEnergyContainerItem) {
                if (!mergeItemStack(stackInSlot, TileImprovedAssembler.CHARGE_SLOT, TileImprovedAssembler.CHARGE_SLOT + 1, false)) {
                    return null;
                }
            } else {
                if (!mergeItemStack(stackInSlot, TileImprovedAssembler.INPUT_START,
                        TileImprovedAssembler.OUTPUT_START, false)) {
                    return null;
                }
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
        // Sent as exact low/high 16-bit halves - see TileAdvancedPulverizer#applyClientEnergy.
        int energy = tile.getEnergy();
        int reconfigSides = tile.augmentReconfigSides ? 1 : 0;
        int autoInput = tile.augmentAutoInput ? 1 : 0;
        int autoOutput = tile.augmentAutoOutput ? 1 : 0;
        int redstoneControl = tile.augmentRedstoneControl ? 1 : 0;
        FluidStack tankFluid = tile.getTankFluid();
        int fluidId = tankFluid != null ? FluidRegistry.getFluidID(tankFluid.getFluid()) : -1;
        // Same /4 trick getEnergy() already needs above: windowProperty values are transmitted
        // as shorts (max 32767), and the tank's 100,000 mB capacity exceeds that on its own -
        // setTankFluidAmountClient multiplies back by 4 on the way in.
        int fluidAmount = tankFluid != null ? tankFluid.amount / 4 : 0;

        for (int i = 0; i < list.size(); i++) {
            ICrafting crafter = list.get(i);
            if (lastEnergy != energy) {
                crafter.sendProgressBarUpdate(this, 0, energy & 0xFFFF);
                crafter.sendProgressBarUpdate(this, 15, energy >>> 16);
            }
            for (int side = 0; side < 6; side++) {
                int mode = tile.getSideMode(side);
                if (lastSideModes[side] != mode) {
                    crafter.sendProgressBarUpdate(this, 1 + side, mode);
                }
            }
            if (lastReconfigSides != reconfigSides) {
                crafter.sendProgressBarUpdate(this, 7, reconfigSides);
            }
            if (lastAutoInput != autoInput) {
                crafter.sendProgressBarUpdate(this, 8, autoInput);
            }
            if (lastAutoOutput != autoOutput) {
                crafter.sendProgressBarUpdate(this, 9, autoOutput);
            }
            int energyPerTick = tile.getEnergyPerTick();
            if (lastEnergyPerTick != energyPerTick) {
                crafter.sendProgressBarUpdate(this, 10, energyPerTick);
            }
            if (lastRedstoneControl != redstoneControl) {
                crafter.sendProgressBarUpdate(this, 11, redstoneControl);
            }
            int controlMode = tile.getControl().ordinal();
            if (lastControlMode != controlMode) {
                crafter.sendProgressBarUpdate(this, 12, controlMode);
            }
            if (lastFluidId != fluidId) {
                crafter.sendProgressBarUpdate(this, 13, fluidId);
            }
            if (lastFluidAmount != fluidAmount) {
                crafter.sendProgressBarUpdate(this, 14, fluidAmount);
            }
        }

        lastEnergy = energy;
        for (int side = 0; side < 6; side++) {
            lastSideModes[side] = tile.getSideMode(side);
        }
        lastReconfigSides = reconfigSides;
        lastAutoInput = autoInput;
        lastAutoOutput = autoOutput;
        lastEnergyPerTick = tile.getEnergyPerTick();
        lastRedstoneControl = redstoneControl;
        lastControlMode = tile.getControl().ordinal();
        lastFluidId = fluidId;
        lastFluidAmount = fluidAmount;
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            tile.setEnergyLowClient(value);
        } else if (id == 15) {
            tile.setEnergyHighClient(value);
        } else if (id >= 1 && id <= 6) {
            tile.setSideModeClient(id - 1, value);
        } else if (id == 7) {
            tile.augmentReconfigSides = value != 0;
        } else if (id == 8) {
            tile.augmentAutoInput = value != 0;
        } else if (id == 9) {
            tile.augmentAutoOutput = value != 0;
        } else if (id == 10) {
            tile.setEnergyPerTickClient(value);
        } else if (id == 11) {
            tile.augmentRedstoneControl = value != 0;
        } else if (id == 12) {
            tile.setControlClient(value);
        } else if (id == 13) {
            tile.setTankFluidIdClient(value);
        } else if (id == 14) {
            tile.setTankFluidAmountClient(value);
        }
    }
}
