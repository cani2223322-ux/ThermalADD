package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.inventory.Slot;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;
import net.thermaladd.mod.inventory.ContainerSingularityMachine;
import net.thermaladd.mod.tileentity.TileSingularityMachine;

/**
 * GUI shared by the TileSingularityMachine family: the TE-style panel, energy bar and charge slot,
 * every machine slot drawn with its live role ring and line lock, the player inventory, and the
 * same five tabs the other machines have. A subclass only draws what is specific to it - progress
 * arrows, glyphs, tanks, buttons - and names its slots.
 */
public abstract class GuiSingularityMachine extends TabbedMachineGui {

    protected static final int BASE_WIDTH = 176;
    protected static final int BASE_HEIGHT = 178;

    protected static final int ENERGY_X = 8;
    protected static final int ENERGY_Y = 17;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;
    private static final int LEFT_TAB_X = 0;
    private static final int LEFT_TAB_Y = 4;

    /** Real TE's tank size (ElementFluidTank) and gauge overlay. */
    protected static final int TANK_WIDTH = 16;
    protected static final int TANK_HEIGHT = 60;
    private static final ResourceLocation TANK_TEXTURE = new ResourceLocation("cofh", "textures/gui/elements/FluidTank.png");

    protected final TileSingularityMachine tile;
    private final GuiSideTab[] tabs;
    private final TabAugmentsGeneric augmentsTab;
    private final TabConfigGeneric configTab;
    private final TabRedstoneControl redstoneTab;
    private final TabEnergy energyTab;
    private final TabInfo infoTab;

    protected GuiSingularityMachine(ContainerSingularityMachine container, String... infoKeys) {
        super(container);
        this.tile = container.getTile();
        xSize = BASE_WIDTH + GuiSideTab.MAX_WIDTH;
        ySize = BASE_HEIGHT;
        augmentsTab = new TabAugmentsGeneric(this, container);
        configTab = new TabConfigGeneric(this, tile);
        redstoneTab = new TabRedstoneControl(this, tile.xCoord, tile.yCoord, tile.zCoord, tile);
        energyTab = new TabEnergy(this, tile);
        infoTab = new TabInfo(this, infoKeys);
        augmentsTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y);
        configTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP);
        redstoneTab.setStackPosition(TAB_STACK_X, TAB_STACK_Y + TAB_STACK_STEP * 2);
        energyTab.setStackPosition(LEFT_TAB_X, LEFT_TAB_Y);
        infoTab.setStackPosition(LEFT_TAB_X, LEFT_TAB_Y + TAB_STACK_STEP);
        setScrollableTab(infoTab);
        tabs = new GuiSideTab[]{augmentsTab, configTab, redstoneTab, energyTab, infoTab};
        for (int i = 0; i < tabs.length; i++) {
            TabTracker.restore(tabs[i]);
        }
    }

    private boolean isTabShown(GuiSideTab tab) {
        if (tab == configTab) {
            return tile.augmentReconfigSides;
        }
        if (tab == redstoneTab) {
            return tile.augmentRedstoneControl;
        }
        return true;
    }

    // ------------------------------------------------ what a subclass provides

    /** Arrows, glyphs, tanks - everything but the slots, which are drawn here. Absolute coordinates. */
    protected abstract void drawMachine(int left, int top);

    /** Lang key for an empty machine slot's tooltip. */
    protected abstract String machineSlotRoleKey(int slot);

    /** Machine-specific hover tooltips (progress, tank). Returns whether one was drawn. */
    protected boolean drawMachineTooltip(int mouseX, int mouseY) {
        return false;
    }

    /** Machine-specific buttons. Coordinates relative to the panel. Returns whether consumed. */
    protected boolean machineClicked(int relX, int relY, int mouseButton) {
        return false;
    }

    /** Ring colour of an output slot while some side extracts from it. */
    protected int outputHighlight(int slot) {
        return HIGHLIGHT_OUTPUT_PRIMARY;
    }

    // ------------------------------------------------ drawing

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

        for (int i = 0; i < tabs.length; i++) {
            tabs[i].update();
        }

        drawTEPanel(left, top, BASE_WIDTH, BASE_HEIGHT);
        drawEnergyStored(left + ENERGY_X, top + ENERGY_Y, tile.getEnergy(), tile.getMaxEnergy());
        drawTESlot(left + ContainerSingularityMachine.CHARGE_X - 1, top + ContainerSingularityMachine.CHARGE_Y - 1);

        for (int i = 0; i < inventorySlots.inventorySlots.size(); i++) {
            Slot slot = (Slot) inventorySlots.inventorySlots.get(i);
            int index = slot.getSlotIndex();
            if (slot.inventory != tile || index >= tile.getAugmentStart()) {
                continue;
            }
            int x = left + slot.xDisplayPosition;
            int y = top + slot.yDisplayPosition;
            drawTESlot(x - 1, y - 1, slotHighlight(index));
            int lock = tile.lockIndexOf(index);
            if (lock >= 0) {
                drawLineLock(tile.getLineLocks().get(lock), x, y, tile.getStackInSlot(index) == null);
            }
        }

        drawMachine(left, top);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + ContainerSingularityMachine.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + ContainerSingularityMachine.PLAYER_HOTBAR_Y - 1);
        }

        for (int i = 0; i < tabs.length; i++) {
            if (isTabShown(tabs[i])) {
                tabs[i].drawBackground(left, top);
            }
        }
    }

    private int slotHighlight(int slot) {
        if (tile.isOutputSlot(slot)) {
            return tile.isSlotExtractedByAnySide(slot) ? outputHighlight(slot) : HIGHLIGHT_NONE;
        }
        return tile.isSlotInsertedByAnySide(slot) ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
    }

    /** Activity glyph plus progress arrow for one line - see TabbedMachineGui. */
    protected void drawLineProgress(ResourceLocation scaleTexture, int left, int top, int line, int scaleX, int arrowX, int rowY) {
        int progress = tile.getProgress(line);
        int max = tile.getProgressMax(line);
        drawActivityScale(scaleTexture, left + scaleX, top + rowY, progress, max);
        drawProgressArrow(left + arrowX, top + rowY, progress, max);
    }

    /**
     * A TE-style tank: the recessed socket, the fluid's own still texture tiled up from the bottom
     * in its tint (the Assembler draws a flat colour; this is what real TE's ElementFluidTank does),
     * the gauge overlay on top, and the role ring. Panel-relative position.
     */
    protected void drawFluidTank(int left, int top, int relX, int relY, int ringColor) {
        int x = left + relX;
        int y = top + relY;
        drawTESocket(x, y, TANK_WIDTH, TANK_HEIGHT);

        FluidStack fluid = tile.getTankFluid();
        int capacity = tile.getTankCapacity();
        if (fluid != null && fluid.amount > 0 && capacity > 0) {
            int filled = (int) Math.max(1, Math.min(TANK_HEIGHT, (long) fluid.amount * TANK_HEIGHT / capacity));
            IIcon icon = fluid.getFluid().getIcon(fluid);
            if (icon != null) {
                mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
                int color = fluid.getFluid().getColor(fluid);
                GL11.glColor4f((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
                for (int dy = 0; dy < filled; dy += 16) {
                    int segment = Math.min(16, filled - dy);
                    drawIconBottom(icon, x, y + TANK_HEIGHT - dy - segment, TANK_WIDTH, segment);
                }
                GL11.glColor4f(1F, 1F, 1F, 1F);
            }
        }

        mc.getTextureManager().bindTexture(TANK_TEXTURE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawQuad(x, y, TANK_WIDTH, TANK_HEIGHT, 48 / 64F, 1 / 64F, 64 / 64F, 61 / 64F);
        GL11.glDisable(GL11.GL_BLEND);

        drawRect(x - 1, y - 1, x + TANK_WIDTH + 1, y, ringColor);
        drawRect(x - 1, y + TANK_HEIGHT, x + TANK_WIDTH + 1, y + TANK_HEIGHT + 1, ringColor);
        drawRect(x - 1, y - 1, x, y + TANK_HEIGHT + 1, ringColor);
        drawRect(x + TANK_WIDTH, y - 1, x + TANK_WIDTH + 1, y + TANK_HEIGHT + 1, ringColor);
    }

    /** The bottom {@code height} pixels of a 16x16 atlas icon, {@code width} wide. */
    private void drawIconBottom(IIcon icon, int x, int y, int width, int height) {
        float u1 = icon.getMinU();
        float u2 = icon.getMinU() + (icon.getMaxU() - icon.getMinU()) * width / 16F;
        float v2 = icon.getMaxV();
        float v1 = v2 - (icon.getMaxV() - icon.getMinV()) * height / 16F;
        drawQuad(x, y, width, height, u1, v1, u2, v2);
    }

    private void drawQuad(int x, int y, int w, int h, float u1, float v1, float u2, float v2) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, zLevel, u1, v2);
        t.addVertexWithUV(x + w, y + h, zLevel, u2, v2);
        t.addVertexWithUV(x + w, y, zLevel, u2, v1);
        t.addVertexWithUV(x, y, zLevel, u1, v1);
        t.draw();
    }

    /** Real ElementFluidTank's tooltip: the fluid's name when there is some, then amount / capacity. */
    protected boolean drawTankTooltip(int mouseX, int mouseY, int relX, int relY) {
        int x = guiLeft + relX;
        int y = guiTop + relY;
        if (mouseX < x || mouseX >= x + TANK_WIDTH || mouseY < y || mouseY >= y + TANK_HEIGHT) {
            return false;
        }
        List<String> lines = new ArrayList<String>();
        FluidStack fluid = tile.getTankFluid();
        int amount = fluid != null ? fluid.amount : 0;
        if (fluid != null && amount > 0) {
            lines.add(fluid.getFluid().getLocalizedName(fluid));
        }
        lines.add(amount + " / " + tile.getTankCapacity() + " mB");
        drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
        return true;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawMachineTitle(tile, BASE_WIDTH);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"),
                8, ContainerSingularityMachine.PLAYER_INV_Y - 10, 0x404040);

        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setMousePosition(relMouseX, relMouseY);
        }
        for (int i = 0; i < tabs.length; i++) {
            if (isTabShown(tabs[i])) {
                tabs[i].drawForeground(0, 0);
            }
        }
    }

    // ------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        boolean shift = GuiScreen.isShiftKeyDown();

        for (int i = 0; i < tabs.length; i++) {
            if (isTabShown(tabs[i]) && handleTabClick(tabs[i], mouseX, mouseY, left, top, mouseButton, shift)) {
                return;
            }
        }
        if (machineClicked(mouseX - left, mouseY - top, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleTabClick(GuiSideTab tab, int mouseX, int mouseY, int left, int top, int mouseButton, boolean shift) {
        if (mouseButton == 0 && tab.isMouseOverIcon(mouseX, mouseY, left, top)) {
            boolean wasOpen = tab.open;
            for (int i = 0; i < tabs.length; i++) {
                tabs[i].setOpen(false);
            }
            tab.setOpen(!wasOpen);
            TabTracker.record(tabs);
            return true;
        }
        if (tab.isFullyOpen() && tab.isMouseOverFlap(mouseX, mouseY, left, top)) {
            if (tab.onContentClick(mouseX - left - tab.getContentX(), mouseY - top - tab.getTabY(), mouseButton, shift)) {
                return true;
            }
            // An unclaimed click inside an open flap must not reach vanilla as "outside the window".
            return tab != augmentsTab;
        }
        return false;
    }

    // ------------------------------------------------ tooltips

    @Override
    protected String slotRoleKey(int slot) {
        if (slot == tile.getChargeSlot()) {
            return "gui.thermaladd.slot.charge";
        }
        if (tile.isAugmentSlot(slot)) {
            return "gui.thermaladd.slot.augment";
        }
        return machineSlotRoleKey(slot);
    }

    @Override
    protected void addSlotTooltipLines(int slot, List<String> lines) {
        int lock = tile.lockIndexOf(slot);
        if (lock >= 0) {
            addLineLockLines(tile.getLineLocks().get(lock), lines);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (isHoldingItem()) {
            return;
        }
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;

        if (mouseX >= left + ENERGY_X && mouseX < left + ENERGY_X + ENERGY_BAR_WIDTH
                && mouseY >= top + ENERGY_Y && mouseY < top + ENERGY_Y + ENERGY_BAR_HEIGHT) {
            List<String> energyTooltip = new ArrayList<String>();
            energyTooltip.add(tile.getEnergy() + " / " + tile.getMaxEnergy() + " RF");
            drawHoveringText(energyTooltip, mouseX, mouseY, fontRendererObj);
            return;
        }
        if (drawMachineTooltip(mouseX, mouseY)) {
            return;
        }
        if (drawEmptySlotRoleTooltip(tile, mouseX, mouseY)) {
            return;
        }

        List<String> tooltip = new ArrayList<String>();
        for (int i = 0; i < tabs.length; i++) {
            if (isTabShown(tabs[i])) {
                tabs[i].addTooltip(mouseX, mouseY, left, top, tooltip);
            }
        }
        if (!tooltip.isEmpty()) {
            drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }
}
