package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.inventory.ContainerAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;

/** Same self-drawn TE-styled panel as {@link GuiAdvancedPulverizer}, laid out for 3 inputs -> 3 stacked outputs, one per line (no secondary product). */
public class GuiAdvancedFurnace extends TabbedMachineGui {

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
    /** Energy is docked on the LEFT edge (tabX=0, the panel's own left edge), not stacked with the other 3 right-side tabs - see TabEnergy's own javadoc. */
    private static final int LEFT_TAB_X = 0;
    private static final int LEFT_TAB_Y = 4;

    private final TileAdvancedFurnace tile;
    private final TabAugmentsFurnace augmentsTab;
    private final TabConfigFurnace configTab;
    private final TabRedstoneControl redstoneTab;
    private final TabEnergy energyTab;

    public GuiAdvancedFurnace(InventoryPlayer playerInv, TileAdvancedFurnace tile) {
        super(new ContainerAdvancedFurnace(playerInv, tile));
        this.tile = tile;
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugmentsFurnace(this, (ContainerAdvancedFurnace) inventorySlots);
        this.configTab = new TabConfigFurnace(this, tile);
        this.redstoneTab = new TabRedstoneControl(this, tile.xCoord, tile.yCoord, tile.zCoord, tile);
        this.energyTab = new TabEnergy(this, tile);
        augmentsTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP);
        redstoneTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP * 2);
        energyTab.setStackPosition(LEFT_TAB_X, LEFT_TAB_Y);
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
        energyTab.update();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        drawTEPanel(left, top, BASE_WIDTH, BASE_HEIGHT);

        drawEnergyBar(left, top);

        // See GuiAdvancedPulverizer's own copy of this comment: the colored role ring is part
        // of the side-config feature, gated behind the same augment as the Configuration tab.
        int inputHighlight = tile.augmentReconfigSides ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int outputHighlight = tile.augmentReconfigSides ? HIGHLIGHT_OUTPUT : HIGHLIGHT_NONE;
        for (int i = 0; i < TileAdvancedFurnace.INPUT_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedFurnace.INPUT_X - 1,
                    top + ContainerAdvancedFurnace.INPUT_Y + i * ContainerAdvancedFurnace.SLOT_SIZE - 1, inputHighlight);
            drawProgressBar(left, top, i);
        }
        for (int i = 0; i < TileAdvancedFurnace.OUTPUT_SLOTS; i++) {
            drawTESlot(left + ContainerAdvancedFurnace.OUTPUT_X - 1,
                    top + ContainerAdvancedFurnace.OUTPUT_Y + i * ContainerAdvancedFurnace.SLOT_SIZE - 1, outputHighlight);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedFurnace.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + ContainerAdvancedFurnace.PLAYER_HOTBAR_Y - 1);
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
        drawTESocket(left + ENERGY_X, top + ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT);

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

        drawTESocket(x, y, PROGRESS_WIDTH, PROGRESS_HEIGHT);

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
            return true;
        }
        if (tab.isFullyOpen() && tab.isMouseOverFlap(mouseX, mouseY, left, top)) {
            return tab.onContentClick(mouseX - left - tab.getContentX(), mouseY - top - tab.getTabY(), mouseButton, shift);
        }
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

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
}
