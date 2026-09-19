package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/**
 * Entirely self-drawn main panel (no background texture file): a flat CoFH/TE-styled panel
 * built out of plain rectangles, in the same spirit as the sibling ImprovedAssembler GUI's
 * hand-drawn energy bar, just extended to the whole panel. Draws the RF bar, 3 independent
 * progress bars (one per input line) and frames for all machine slots. The Augments and
 * Configuration side tabs, on the other hand, reuse Thermal Expansion's own real tab/icon
 * textures (see GuiSideTab) for a pixel-accurate look, since CoFHCore is a hard dependency.
 */
public class GuiAdvancedPulverizer extends TabbedMachineGui {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_DARK = 0xFF8B8B8B;
    private static final int BORDER = 0xFF373737;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int ENERGY_FILL = 0xFFB01010;
    private static final int PROGRESS_FILL = 0xFF3CA0DC;
    private static final int PROGRESS_DONE = 0xFF3CDC6E;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 178;

    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_HEIGHT = 54;

    private static final int PROGRESS_X = 66;
    private static final int PROGRESS_WIDTH = 46;
    private static final int PROGRESS_HEIGHT = 10;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;

    private final TileAdvancedPulverizer tile;
    private final TabAugments augmentsTab;
    private final TabConfig configTab;
    private final TabRedstoneControl redstoneTab;

    public GuiAdvancedPulverizer(InventoryPlayer playerInv, TileAdvancedPulverizer tile) {
        super(new ContainerAdvancedPulverizer(playerInv, tile));
        this.tile = tile;
        // xSize/ySize drive more than just the background texture size - GuiContainer's own
        // mouseClicked() separately checks the click against (guiLeft, guiTop, xSize, ySize)
        // and forces the slot to -999 ("clicked outside the window", which drops whatever is
        // on the cursor) whenever that check fails, REGARDLESS of whether getSlotAtPosition()
        // already found a real Slot there. The augment/config tabs render past x=176 (right of
        // the main panel), so xSize has to cover that whole flap area or every click on a tab
        // slot gets treated as "outside" and the item is lost. The panel itself is still only
        // drawn at BASE_WIDTH/BASE_HEIGHT (see drawGuiContainerBackgroundLayer) - only the
        // click-bounds grow, which is exactly how real Thermal Expansion's own tabbed machine
        // GUIs end up visibly off-center-left instead of centered.
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugments(this, (ContainerAdvancedPulverizer) inventorySlots);
        this.configTab = new TabConfig(this, tile);
        this.redstoneTab = new TabRedstoneControl(this, tile.xCoord, tile.yCoord, tile.zCoord, tile);
        augmentsTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP);
        redstoneTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP * 2);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        augmentsTab.update();
        if (!tile.augmentReconfigSides) {
            configTab.setOpen(false);
        }
        configTab.update();
        if (!tile.augmentRedstoneControl) {
            redstoneTab.setOpen(false);
        }
        redstoneTab.update();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        // Deliberately BASE_WIDTH/BASE_HEIGHT here, NOT xSize/ySize: xSize is padded to cover
        // the tab flap area for click-detection (see the constructor), but the visible panel
        // itself is still only the original 176x178 box.
        drawRect(left, top, left + BASE_WIDTH, top + BASE_HEIGHT, PANEL);
        drawPanelBorder(left, top, BASE_WIDTH, BASE_HEIGHT);

        drawEnergyBar(left, top);

        for (int i = 0; i < TileAdvancedPulverizer.INPUT_SLOTS; i++) {
            drawSlotFrame(left + ContainerAdvancedPulverizer.INPUT_X - 1,
                    top + ContainerAdvancedPulverizer.INPUT_Y + i * ContainerAdvancedPulverizer.SLOT_SIZE - 1);
            drawProgressBar(left, top, i);
        }
        for (int i = 0; i < TileAdvancedPulverizer.OUTPUT_PRIMARY_SLOTS; i++) {
            drawSlotFrame(left + ContainerAdvancedPulverizer.OUTPUT_PRIMARY_X - 1,
                    top + ContainerAdvancedPulverizer.OUTPUT_PRIMARY_Y + i * ContainerAdvancedPulverizer.SLOT_SIZE - 1);
        }
        drawSlotFrame(left + ContainerAdvancedPulverizer.OUTPUT_SECONDARY_X - 1,
                top + ContainerAdvancedPulverizer.OUTPUT_SECONDARY_Y - 1);

        // player inventory slot frames
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(left + 8 + col * 18 - 1, top + ContainerAdvancedPulverizer.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(left + 8 + col * 18 - 1, top + ContainerAdvancedPulverizer.PLAYER_HOTBAR_Y - 1);
        }

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawBackground(left, top);
        }
    }

    private void drawPanelBorder(int left, int top, int w, int h) {
        drawRect(left, top, left + w, top + 1, BORDER);
        drawRect(left, top + h - 1, left + w, top + h, BORDER);
        drawRect(left, top, left + 1, top + h, BORDER);
        drawRect(left + w - 1, top, left + w, top + h, BORDER);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, BORDER);
        drawRect(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
    }

    private void drawEnergyBar(int left, int top) {
        drawRect(left + ENERGY_X - 1, top + ENERGY_Y - 1,
                left + ENERGY_X + ENERGY_WIDTH + 1, top + ENERGY_Y + ENERGY_HEIGHT + 1, BORDER);
        drawRect(left + ENERGY_X, top + ENERGY_Y,
                left + ENERGY_X + ENERGY_WIDTH, top + ENERGY_Y + ENERGY_HEIGHT, PANEL_DARK);

        int maxEnergy = tile.getMaxEnergy();
        int filled = maxEnergy <= 0 ? 0 : (int) (ENERGY_HEIGHT * ((float) tile.getEnergy() / (float) maxEnergy));
        if (filled > 0) {
            drawRect(left + ENERGY_X, top + ENERGY_Y + (ENERGY_HEIGHT - filled),
                    left + ENERGY_X + ENERGY_WIDTH, top + ENERGY_Y + ENERGY_HEIGHT, ENERGY_FILL);
        }
    }

    private void drawProgressBar(int left, int top, int line) {
        int x = left + PROGRESS_X;
        int y = top + ContainerAdvancedPulverizer.INPUT_Y + line * ContainerAdvancedPulverizer.SLOT_SIZE + 4;

        drawRect(x - 1, y - 1, x + PROGRESS_WIDTH + 1, y + PROGRESS_HEIGHT + 1, BORDER);
        drawRect(x, y, x + PROGRESS_WIDTH, y + PROGRESS_HEIGHT, PANEL_DARK);

        int max = tile.getProgressMax(line);
        if (max <= 0) {
            return;
        }
        int p = tile.getProgress(line);
        int filled = Math.min(PROGRESS_WIDTH, PROGRESS_WIDTH * p / max);
        if (filled > 0) {
            drawRect(x, y, x + filled, y + PROGRESS_HEIGHT, p >= max ? PROGRESS_DONE : PROGRESS_FILL);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.advancedPulverizer.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"),
                8, ContainerAdvancedPulverizer.PLAYER_INV_Y - 10, 0x404040);

        augmentsTab.drawForeground(0, 0);
        if (tile.augmentReconfigSides) {
            configTab.drawForeground(0, 0);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawForeground(0, 0);
        }
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

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Clicking a tab's own icon always toggles it (open/closed), closing the other tabs first.
     * Otherwise, while fully open, a click inside its flap is offered to its content (the
     * Configuration cross-buttons, the Redstone Control buttons); if nothing there claims it -
     * notably for the Augments tab, which has no custom content, only real vanilla Slots - the
     * click falls through to normal slot handling via super.mouseClicked().
     */
    private boolean handleTabClick(GuiSideTab tab, int mouseX, int mouseY, int left, int top, int mouseButton, boolean shift) {
        if (mouseButton == 0 && tab.isMouseOverIcon(mouseX, mouseY, left, top)) {
            boolean wasOpen = tab.open;
            augmentsTab.setOpen(false);
            configTab.setOpen(false);
            redstoneTab.setOpen(false);
            tab.setOpen(!wasOpen);
            return true;
        }
        if (tab.isFullyOpen() && tab.isMouseOverFlap(mouseX, mouseY, left, top)) {
            return tab.onContentClick(mouseX - left - tab.getTabX(), mouseY - top - tab.getTabY(), mouseButton, shift);
        }
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        if (mouseX >= left + ENERGY_X && mouseX <= left + ENERGY_X + ENERGY_WIDTH
                && mouseY >= top + ENERGY_Y && mouseY <= top + ENERGY_Y + ENERGY_HEIGHT) {
            List<String> energyLines = new ArrayList<String>();
            energyLines.add(StatCollector.translateToLocalFormatted("gui.thermaladd.energy.consumption", tile.getEnergyPerTick()));
            energyLines.add(StatCollector.translateToLocalFormatted("gui.thermaladd.energy.maxpower", tile.getMaxEnergyPerTick()));
            energyLines.add(StatCollector.translateToLocalFormatted("gui.thermaladd.energy.stored", tile.getEnergy()));
            drawEnergyInfoTooltip(mouseX, mouseY, StatCollector.translateToLocal("gui.thermaladd.energy.title"), energyLines);
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
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }
}
