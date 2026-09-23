package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedCharger;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;

/**
 * Entirely self-drawn main panel, same TE-pixel-matched style as {@link GuiAdvancedPulverizer}
 * (see that class's own javadoc). Laid out as a 3x3 grid of (line -> output) pairs instead of
 * the Pulverizer/Furnace/Sawmill's own column layout - 9 parallel lines need more horizontal
 * room than 3 does. Each line's own charge/recipe progress (RF stored/max for a battery, or RF
 * invested/needed for a real ChargerManager conversion - see TileAdvancedCharger#tryProcess) is
 * shown as a hover tooltip on its own line slot rather than a dedicated bar widget - 9 of the
 * Pulverizer's own 46px-wide bars wouldn't fit this grid's tighter per-line footprint.
 */
public class GuiAdvancedCharger extends TabbedMachineGui {

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 178;

    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_WIDTH = ENERGY_BAR_WIDTH;
    private static final int ENERGY_HEIGHT = ENERGY_BAR_HEIGHT;
    private static final int CHARGE_SLOT_X = ENERGY_X;
    private static final int CHARGE_SLOT_Y = ENERGY_Y + 45;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;
    private static final int LEFT_TAB_X = 0;
    private static final int LEFT_TAB_Y = 4;

    private final TileAdvancedCharger tile;
    private final TabAugmentsCharger augmentsTab;
    private final TabConfigCharger configTab;
    private final TabRedstoneControl redstoneTab;
    private final TabEnergy energyTab;

    public GuiAdvancedCharger(InventoryPlayer playerInv, TileAdvancedCharger tile) {
        super(new ContainerAdvancedCharger(playerInv, tile));
        this.tile = tile;
        // See GuiAdvancedPulverizer's constructor for why xSize has to cover the tab flap area.
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugmentsCharger(this, (ContainerAdvancedCharger) inventorySlots);
        this.configTab = new TabConfigCharger(this, tile);
        this.redstoneTab = new TabRedstoneControl(this, tile.xCoord, tile.yCoord, tile.zCoord, tile);
        this.energyTab = new TabEnergy(this, tile);
        augmentsTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP);
        redstoneTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP * 2);
        energyTab.setStackPosition(LEFT_TAB_X, LEFT_TAB_Y);
        TabTracker.restore(augmentsTab);
        TabTracker.restore(configTab);
        TabTracker.restore(redstoneTab);
        TabTracker.restore(energyTab);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!tile.augmentReconfigSides) {
            configTab.setOpen(false);
        }
        if (!tile.augmentRedstoneControl) {
            redstoneTab.setOpen(false);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        // See GuiAdvancedPulverizer's own copy of this comment: stepped once per rendered frame
        // (not once per game tick) to match real Thermal Expansion's own GuiBase#drawTabs.
        augmentsTab.update();
        configTab.update();
        redstoneTab.update();
        energyTab.update();

        drawTEPanel(left, top, BASE_WIDTH, BASE_HEIGHT);

        drawEnergyBar(left, top);
        drawTESlot(left + CHARGE_SLOT_X - 1, top + CHARGE_SLOT_Y - 1);

        // Matches real Thermal Expansion: the colored role ring only appears once the player
        // has actually configured a side to grant that role - see GuiAdvancedPulverizer's own
        // copy of this comment.
        int inputHighlight = tile.isAnyInputSide() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int outputHighlight = tile.isAnyOutputSide() ? HIGHLIGHT_OUTPUT : HIGHLIGHT_NONE;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < ContainerAdvancedCharger.COLS; col++) {
                int x = left + ContainerAdvancedCharger.LINE_X[col];
                int y = top + ContainerAdvancedCharger.ROW_Y[row];
                drawTESlot(x - 1, y - 1, inputHighlight);
                drawTESlot(x + ContainerAdvancedCharger.OUTPUT_OFFSET - 1, y - 1, outputHighlight);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedCharger.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedCharger.PLAYER_HOTBAR_Y - 1);
        }

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawBackground(left, top);
        }
        energyTab.drawBackground(left, top);
    }

    private void drawEnergyBar(int left, int top) {
        drawEnergyStored(left + ENERGY_X, top + ENERGY_Y, tile.getEnergy(), tile.getMaxEnergy());
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawMachineTitle(tile, BASE_WIDTH);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"),
                8, ContainerAdvancedCharger.PLAYER_INV_Y - 10, 0x404040);

        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        augmentsTab.setMousePosition(relMouseX, relMouseY);
        configTab.setMousePosition(relMouseX, relMouseY);
        redstoneTab.setMousePosition(relMouseX, relMouseY);
        energyTab.setMousePosition(relMouseX, relMouseY);

        augmentsTab.drawForeground(0, 0);
        if (tile.augmentReconfigSides) {
            configTab.drawForeground(0, 0);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawForeground(0, 0);
        }
        energyTab.drawForeground(0, 0);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        boolean shift = GuiScreen.isShiftKeyDown();

        if (handleTabClick(augmentsTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }
        if (tile.augmentReconfigSides && handleTabClick(configTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }
        if (tile.augmentRedstoneControl && handleTabClick(redstoneTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }
        if (handleTabClick(energyTab, mouseX, mouseY, left, top, mouseButton, shift)) {
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleTabClick(GuiSideTab tab, int mouseX, int mouseY, int left, int top, int mouseButton, boolean shift) {
        if (mouseButton == 0 && tab.isMouseOverIcon(mouseX, mouseY, left, top)) {
            boolean wasOpen = tab.open;
            augmentsTab.setOpen(false);
            configTab.setOpen(false);
            redstoneTab.setOpen(false);
            energyTab.setOpen(false);
            tab.setOpen(!wasOpen);
            TabTracker.record(augmentsTab, configTab, redstoneTab, energyTab);
            return true;
        }
        if (tab.isFullyOpen() && tab.isMouseOverFlap(mouseX, mouseY, left, top)) {
            if (tab.onContentClick(mouseX - left - tab.getContentX(), mouseY - top - tab.getTabY(), mouseButton, shift)) {
                return true;
            }
            // See GuiAdvancedPulverizer#handleTabClick - an unclaimed click inside an open flap
            // used to reach vanilla as "clicked outside the window" and drop the held stack.
            return tab != augmentsTab;
        }
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (isHoldingItem()) {
            return;
        }
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        if (mouseX >= left + ENERGY_X && mouseX < left + ENERGY_X + ENERGY_WIDTH
                && mouseY >= top + ENERGY_Y && mouseY < top + ENERGY_Y + ENERGY_HEIGHT) {
            List<String> energyTooltip = new ArrayList<String>();
            energyTooltip.add(tile.getEnergy() + " / " + tile.getMaxEnergy() + " RF");
            drawHoveringText(energyTooltip, mouseX, mouseY, fontRendererObj);
            return;
        }

        // A line's progress is NOT drawn here any more - see renderToolTip below.
        if (drawEmptySlotRoleTooltip(tile, mouseX, mouseY)) {
            return;
        }

        List<String> tooltip = new ArrayList<String>();
        augmentsTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        if (tile.augmentReconfigSides) {
            configTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        }
        energyTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }

    /**
     * A line's progress is appended to the item's OWN tooltip rather than drawn as a second box.
     * It used to be drawn separately from drawScreen, and since a line only has progress while an
     * item sits in it, that box always landed on top of vanilla's item tooltip for the same slot.
     * Line colouring matches vanilla's own renderToolTip: rarity colour on the name, grey below.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void renderToolTip(ItemStack stack, int x, int y) {
        int line = lineAt(x, y);
        int max = line < 0 ? 0 : tile.getProgressMax(line);
        if (max <= 0) {
            super.renderToolTip(stack, x, y);
            return;
        }
        List<String> lines = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, (i == 0 ? stack.getRarity().rarityColor : EnumChatFormatting.GRAY) + lines.get(i));
        }
        lines.add(EnumChatFormatting.GRAY + String.valueOf(tile.getProgress(line)) + " / " + max + " RF");
        FontRenderer font = stack.getItem().getFontRenderer(stack);
        drawHoveringText(lines, x, y, font == null ? fontRendererObj : font);
    }

    @Override
    protected String slotRoleKey(int slot) {
        if (slot < TileAdvancedCharger.OUTPUT_START) {
            return "gui.thermaladd.slot.chargerLine";
        }
        if (slot < TileAdvancedCharger.AUGMENT_START) {
            return "gui.thermaladd.mode.output";
        }
        return slot < TileAdvancedCharger.CHARGE_SLOT ? "gui.thermaladd.slot.augment" : "gui.thermaladd.slot.charge";
    }

    /** Which charging line's input slot is under the given screen position, or -1. */
    private int lineAt(int mouseX, int mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < ContainerAdvancedCharger.COLS; col++) {
                int x = guiLeft + ContainerAdvancedCharger.LINE_X[col];
                int y = guiTop + ContainerAdvancedCharger.ROW_Y[row];
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    return row * ContainerAdvancedCharger.COLS + col;
                }
            }
        }
        return -1;
    }
}
