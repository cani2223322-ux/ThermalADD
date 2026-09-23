package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;

/**
 * Entirely self-drawn main panel, same TE-pixel-matched style as {@link GuiAdvancedPulverizer}
 * (see that class's own javadoc) - this one is a near-identical layout since the Sawmill shares
 * the exact same 3-input/3-primary-output/2-secondary-output/9-augment/1-charge slot shape.
 */
public class GuiAdvancedSawmill extends TabbedMachineGui {


    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 178;

    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_WIDTH = ENERGY_BAR_WIDTH;
    private static final int ENERGY_HEIGHT = ENERGY_BAR_HEIGHT;
    private static final int CHARGE_SLOT_X = ENERGY_X;
    private static final int CHARGE_SLOT_Y = ENERGY_Y + 45;

    /** Activity glyph (16px) then progress arrow (24px), in the gap between the slot columns. */
    private static final int SCALE_X = 67;
    private static final int ARROW_X = 85;
    /** Real TE's own Sawmill glyph. */
    private static final ResourceLocation SCALE_TEXTURE =
            new ResourceLocation("cofh", "textures/gui/elements/Scale_Saw.png");

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;
    private static final int LEFT_TAB_X = 0;
    private static final int LEFT_TAB_Y = 4;

    private final TileAdvancedSawmill tile;
    private final TabAugmentsSawmill augmentsTab;
    private final TabConfigSawmill configTab;
    private final TabRedstoneControl redstoneTab;
    private final TabEnergy energyTab;
    private final TabInfo infoTab;

    public GuiAdvancedSawmill(InventoryPlayer playerInv, TileAdvancedSawmill tile) {
        super(new ContainerAdvancedSawmill(playerInv, tile));
        this.tile = tile;
        // See GuiAdvancedPulverizer's constructor for why xSize has to cover the tab flap area.
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugmentsSawmill(this, (ContainerAdvancedSawmill) inventorySlots);
        this.configTab = new TabConfigSawmill(this, tile);
        this.redstoneTab = new TabRedstoneControl(this, tile.xCoord, tile.yCoord, tile.zCoord, tile);
        this.energyTab = new TabEnergy(this, tile);
        augmentsTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP);
        redstoneTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP * 2);
        energyTab.setStackPosition(LEFT_TAB_X, LEFT_TAB_Y);
        this.infoTab = new TabInfo(this, "info.thermaladd.advancedSawmill", "info.thermaladd.tip.lock", "info.thermaladd.tip.wrench", "info.thermaladd.tip.redprint", "info.thermaladd.tip.comparatorLines");
        infoTab.setStackPosition(LEFT_TAB_X, LEFT_TAB_Y + TAB_STACK_STEP);
        setScrollableTab(infoTab);
        TabTracker.restore(augmentsTab);
        TabTracker.restore(configTab);
        TabTracker.restore(redstoneTab);
        TabTracker.restore(energyTab);
        TabTracker.restore(infoTab);
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
        infoTab.update();

        drawTEPanel(left, top, BASE_WIDTH, BASE_HEIGHT);

        drawEnergyBar(left, top);
        drawTESlot(left + CHARGE_SLOT_X - 1, top + CHARGE_SLOT_Y - 1);

        // See GuiAdvancedPulverizer's own copy of this comment: the colored role ring tracks
        // live side configuration, not merely whether the Reconfigurable Sides augment is
        // installed.
        int inputHighlight = tile.isAnyInputSide() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int primaryHighlight = tile.isAnyOutputPrimarySide() ? HIGHLIGHT_OUTPUT_PRIMARY : HIGHLIGHT_NONE;
        int secondaryHighlight = tile.isAnyOutputSecondarySide() ? HIGHLIGHT_OUTPUT_SECONDARY : HIGHLIGHT_NONE;
        for (int i = 0; i < TileAdvancedSawmill.INPUT_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedSawmill.INPUT_X - 1,
                    top + ContainerAdvancedSawmill.INPUT_Y + i * ContainerAdvancedSawmill.SLOT_SIZE - 1, inputHighlight);
            drawProgressBar(left, top, i);
            drawLineLock(tile.getLineLocks().get(i), left + ContainerAdvancedSawmill.INPUT_X,
                    top + ContainerAdvancedSawmill.INPUT_Y + i * ContainerAdvancedSawmill.SLOT_SIZE,
                    tile.getStackInSlot(TileAdvancedSawmill.INPUT_START + i) == null);
        }
        for (int i = 0; i < TileAdvancedSawmill.OUTPUT_PRIMARY_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedSawmill.OUTPUT_PRIMARY_X - 1,
                    top + ContainerAdvancedSawmill.OUTPUT_PRIMARY_Y + i * ContainerAdvancedSawmill.SLOT_SIZE - 1, primaryHighlight);
        }
        for (int i = 0; i < TileAdvancedSawmill.OUTPUT_SECONDARY_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedSawmill.OUTPUT_SECONDARY_X - 1,
                    top + ContainerAdvancedSawmill.OUTPUT_SECONDARY_Y + i * ContainerAdvancedSawmill.SLOT_SIZE - 1, secondaryHighlight);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedSawmill.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedSawmill.PLAYER_HOTBAR_Y - 1);
        }

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawBackground(left, top);
        }
        energyTab.drawBackground(left, top);
        infoTab.drawBackground(left, top);
    }

    private void drawEnergyBar(int left, int top) {
        drawEnergyStored(left + ENERGY_X, top + ENERGY_Y, tile.getEnergy(), tile.getMaxEnergy());
    }

    @Override
    protected String slotRoleKey(int slot) {
        if (slot < TileAdvancedSawmill.OUTPUT_PRIMARY_START) {
            return "gui.thermaladd.mode.input";
        }
        if (slot < TileAdvancedSawmill.OUTPUT_SECONDARY_START) {
            return "gui.thermaladd.mode.outputPrimary";
        }
        if (slot < TileAdvancedSawmill.AUGMENT_START) {
            return "gui.thermaladd.mode.outputSecondary";
        }
        return slot < TileAdvancedSawmill.CHARGE_SLOT ? "gui.thermaladd.slot.augment" : "gui.thermaladd.slot.charge";
    }

    @Override
    protected void addSlotTooltipLines(int slot, List<String> lines) {
        int line = slot - TileAdvancedSawmill.INPUT_START;
        if (line >= 0 && line < TileAdvancedSawmill.INPUT_SLOTS) {
            addLineLockLines(tile.getLineLocks().get(line), lines);
        }
    }

    /** Real TE's own activity glyph plus progress arrow, per line - see TabbedMachineGui. */
    private void drawProgressBar(int left, int top, int line) {
        int progress = tile.getProgress(line);
        int max = tile.getProgressMax(line);
        int y = top + ContainerAdvancedSawmill.INPUT_Y + line * ContainerAdvancedSawmill.SLOT_SIZE
                + (ContainerAdvancedSawmill.SLOT_SIZE - PROGRESS_ARROW_HEIGHT) / 2;
        drawActivityScale(SCALE_TEXTURE, left + SCALE_X, y, progress, max);
        drawProgressArrow(left + ARROW_X, y, progress, max);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawMachineTitle(tile, BASE_WIDTH);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"),
                8, ContainerAdvancedSawmill.PLAYER_INV_Y - 10, 0x404040);

        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        augmentsTab.setMousePosition(relMouseX, relMouseY);
        configTab.setMousePosition(relMouseX, relMouseY);
        redstoneTab.setMousePosition(relMouseX, relMouseY);
        energyTab.setMousePosition(relMouseX, relMouseY);
        infoTab.setMousePosition(relMouseX, relMouseY);

        augmentsTab.drawForeground(0, 0);
        if (tile.augmentReconfigSides) {
            configTab.drawForeground(0, 0);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawForeground(0, 0);
        }
        energyTab.drawForeground(0, 0);
        infoTab.drawForeground(0, 0);
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
        if (handleTabClick(infoTab, mouseX, mouseY, left, top, mouseButton, shift)) {
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
            infoTab.setOpen(false);
            tab.setOpen(!wasOpen);
            TabTracker.record(augmentsTab, configTab, redstoneTab, energyTab, infoTab);
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

        for (int line = 0; line < TileAdvancedSawmill.INPUT_SLOTS; line++) {
            int rowY = ContainerAdvancedSawmill.INPUT_Y + line * ContainerAdvancedSawmill.SLOT_SIZE
                    + (ContainerAdvancedSawmill.SLOT_SIZE - PROGRESS_ARROW_HEIGHT) / 2;
            if (drawProgressTooltip(mouseX, mouseY, SCALE_X, rowY, ARROW_X + PROGRESS_ARROW_WIDTH - SCALE_X,
                    PROGRESS_ARROW_HEIGHT, tile.getProgress(line), tile.getProgressMax(line))) {
                return;
            }
        }
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
        infoTab.addTooltip(mouseX, mouseY, left, top, tooltip);
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }
}
