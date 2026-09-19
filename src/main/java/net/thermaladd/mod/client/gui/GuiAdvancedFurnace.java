package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;

/** Same self-drawn panel style as {@link GuiAdvancedPulverizer}, laid out for 3 inputs -> 2 stacked outputs (no secondary product). */
public class GuiAdvancedFurnace extends TabbedMachineGui {

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

    private final TileAdvancedFurnace tile;
    private final TabAugmentsFurnace augmentsTab;
    private final TabConfigFurnace configTab;

    public GuiAdvancedFurnace(InventoryPlayer playerInv, TileAdvancedFurnace tile) {
        super(new ContainerAdvancedFurnace(playerInv, tile));
        this.tile = tile;
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugmentsFurnace(this, (ContainerAdvancedFurnace) inventorySlots);
        this.configTab = new TabConfigFurnace(this, tile);
        augmentsTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        augmentsTab.update();
        if (!tile.augmentReconfigSides) {
            configTab.setOpen(false);
        }
        configTab.update();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        drawRect(left, top, left + BASE_WIDTH, top + BASE_HEIGHT, PANEL);
        drawPanelBorder(left, top, BASE_WIDTH, BASE_HEIGHT);

        drawEnergyBar(left, top);

        for (int i = 0; i < TileAdvancedFurnace.INPUT_SLOTS; i++) {
            drawSlotFrame(left + ContainerAdvancedFurnace.INPUT_X - 1,
                    top + ContainerAdvancedFurnace.INPUT_Y + i * ContainerAdvancedFurnace.SLOT_SIZE - 1);
            drawProgressBar(left, top, i);
        }
        for (int i = 0; i < TileAdvancedFurnace.OUTPUT_SLOTS; i++) {
            drawSlotFrame(left + ContainerAdvancedFurnace.OUTPUT_X - 1,
                    top + ContainerAdvancedFurnace.OUTPUT_Y + i * ContainerAdvancedFurnace.SLOT_SIZE - 1);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(left + 8 + col * 18 - 1, top + ContainerAdvancedFurnace.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(left + 8 + col * 18 - 1, top + ContainerAdvancedFurnace.PLAYER_HOTBAR_Y - 1);
        }

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
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
        int y = top + ContainerAdvancedFurnace.INPUT_Y + line * ContainerAdvancedFurnace.SLOT_SIZE + 4;

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
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.advancedFurnace.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"),
                8, ContainerAdvancedFurnace.PLAYER_INV_Y - 10, 0x404040);

        augmentsTab.drawForeground(0, 0);
        if (tile.augmentReconfigSides) {
            configTab.drawForeground(0, 0);
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

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleTabClick(GuiSideTab tab, int mouseX, int mouseY, int left, int top, int mouseButton, boolean shift) {
        if (mouseButton == 0 && tab.isMouseOverIcon(mouseX, mouseY, left, top)) {
            boolean wasOpen = tab.open;
            augmentsTab.setOpen(false);
            configTab.setOpen(false);
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
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }
}
