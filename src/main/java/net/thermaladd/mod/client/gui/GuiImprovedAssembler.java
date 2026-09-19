package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.inventory.ContainerImprovedAssembler;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/**
 * Same visual language as Thermal Expansion's own augmentable-machine GUIs: the normal
 * crafting box on the left, and two real CoFH-style side tabs on the right edge -
 * "Augments" and "Configuration" (hidden until unlocked) - reusing Thermal Expansion's own
 * tab/icon textures for a pixel-accurate look.
 */
public class GuiImprovedAssembler extends TabbedMachineGui {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ThermalADD.MODID, "textures/gui/improvedAssembler.png");

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 220;

    private static final int ENERGY_X = 150;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_WIDTH = 14;
    private static final int ENERGY_HEIGHT = 58;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;

    private final TileImprovedAssembler tile;
    private final TabAugmentsAssembler augmentsTab;
    private final TabConfigAssembler configTab;
    private final TabRedstoneControl redstoneTab;

    public GuiImprovedAssembler(InventoryPlayer playerInv, TileImprovedAssembler tile) {
        super(new ContainerImprovedAssembler(playerInv, tile));
        this.tile = tile;
        // See GuiAdvancedPulverizer's constructor for why xSize has to cover the tab flap
        // area, not just the main panel: GuiContainer's own mouseClicked() drops/loses
        // whatever item is on the cursor if the click lands outside (guiLeft, guiTop, xSize,
        // ySize), even when a real Slot was found there - regardless of the fact that the
        // panel itself is only ever DRAWN at BASE_WIDTH/BASE_HEIGHT below.
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        this.augmentsTab = new TabAugmentsAssembler(this, (ContainerImprovedAssembler) inventorySlots);
        this.configTab = new TabConfigAssembler(this, tile);
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
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(StatCollector.translateToLocal("tile.improvedAssembler.name"), 8, 6, 0x404040);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, ContainerImprovedAssembler.PLAYER_INV_Y - 10, 0x404040);

        augmentsTab.drawForeground(0, 0);
        if (tile.augmentReconfigSides) {
            configTab.drawForeground(0, 0);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawForeground(0, 0);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        mc.getTextureManager().bindTexture(TEXTURE);
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        drawTexturedModalRect(left, top, 0, 0, BASE_WIDTH, BASE_HEIGHT);

        int filled = (int) (ENERGY_HEIGHT * ((float) tile.getEnergy() / (float) TileImprovedAssembler.ENERGY_CAPACITY));
        if (filled > 0) {
            drawRect(left + ENERGY_X + 2, top + ENERGY_Y + 2 + (ENERGY_HEIGHT - 4 - filled),
                    left + ENERGY_X + ENERGY_WIDTH - 2, top + ENERGY_Y + ENERGY_HEIGHT - 2, 0xFFB01010);
        }

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawBackground(left, top);
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
     * Clicking a tab's own icon always toggles it (open/closed), closing the other tab first.
     * Otherwise, while fully open, a click inside its flap is offered to its content (the
     * Configuration cross-buttons); if nothing there claims it - notably for the Augments
     * tab, which has no custom content, only real vanilla Slots - the click falls through to
     * normal slot handling via super.mouseClicked().
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
