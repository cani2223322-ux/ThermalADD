package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;
import net.thermaladd.mod.inventory.ContainerImprovedAssembler;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/**
 * Same self-drawn Thermal Expansion-styled panel as the other 3 machine GUIs in this mod (see
 * {@link TabbedMachineGui#drawTEPanel}), replacing the old one-off "improvedAssembler.png"
 * texture (a flat, non-TE-styled placeholder from before this mod adopted pixel-matched TE
 * styling everywhere else). 6 schematic/output slot pairs with a small static direction arrow
 * between each pair (the real Assembler crafts instantly, no progress timer to animate), the
 * 9x2 material buffer below, and the vertical RF socket on the right edge.
 */
public class GuiImprovedAssembler extends TabbedMachineGui {

    private static final int ARROW_COLOR = 0xFF8B8B8B;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 220;

    private static final int ENERGY_X = 150;
    private static final int ENERGY_Y = 17;
    /** Real TE's own {@code ElementEnergyStored} size - see TabbedMachineGui#drawEnergyStored, never resized. */
    private static final int ENERGY_WIDTH = ENERGY_BAR_WIDTH;
    private static final int ENERGY_HEIGHT = ENERGY_BAR_HEIGHT;
    /** See ContainerImprovedAssembler.CHARGE_X/CHARGE_Y, which this must stay in sync with. */
    private static final int CHARGE_SLOT_X = ENERGY_X;
    private static final int CHARGE_SLOT_Y = ENERGY_Y + 45;

    /**
     * Same fluid tank real TE's own Assembler shows (GuiAssembler's ElementFluidTank), same
     * 16x60 size - positioned to fit the gap between the 2nd output slot column and the RF
     * socket. The frame/gauge overlay (the blue border + red tick marks) is real TE's own
     * {@code cofh:textures/gui/elements/FluidTank.png} (64x64 sheet), used directly like every
     * other real CoFH/TE texture this mod already references - real
     * ElementFluidTank#drawBackground blits it from {@code (32 + gaugeType*16, 1)} sized
     * 16x60; {@code setGauge(1)} (what the real Assembler's own tank uses) puts that at UV
     * (48, 1).
     */
    private static final ResourceLocation TANK_TEXTURE = new ResourceLocation("cofh", "textures/gui/elements/FluidTank.png");
    private static final int TANK_X = 129;
    private static final int TANK_Y = 17;
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = 60;
    /** Used whenever a fluid reports a plain white tint (most vanilla-style fluids bake their actual color into the texture, not this multiplier) so the bar isn't just a blank white sliver. */
    private static final int TANK_FALLBACK_COLOR = 0xFF3060C0;

    private static final int TAB_STACK_X = BASE_WIDTH;
    private static final int TAB_STACK_Y = 4;
    private static final int TAB_STACK_STEP = 22;
    /** Energy is docked on the LEFT edge (tabX=0, the panel's own left edge), not stacked with the other 3 right-side tabs - see TabEnergy's own javadoc. */
    private static final int LEFT_TAB_X = 0;
    private static final int LEFT_TAB_Y = 4;

    private final TileImprovedAssembler tile;
    private final TabAugmentsAssembler augmentsTab;
    private final TabConfigAssembler configTab;
    private final TabRedstoneControl redstoneTab;
    private final TabEnergy energyTab;

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
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawMachineTitle(tile, BASE_WIDTH);
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, ContainerImprovedAssembler.PLAYER_INV_Y - 10, 0x404040);

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

        // See GuiAdvancedPulverizer's own copy of this comment: the colored role ring tracks
        // live side configuration - a fresh machine starts with every side Disabled, so
        // nothing is highlighted until the Configuration tab is actually used. The material
        // buffer's 2 rows can be configured independently (Row 1/Row 2), so each gets its own
        // live check; the schematic slots just use "is ANY input-flavored mode set anywhere".
        int schematicHighlight = tile.isAnyInputSide() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int outputHighlight = tile.isAnyOutputSide() ? HIGHLIGHT_OUTPUT : HIGHLIGHT_NONE;
        int row1Highlight = tile.isAnyInputRow1Side() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        int row2Highlight = tile.isAnyInputRow2Side() ? HIGHLIGHT_INPUT : HIGHLIGHT_NONE;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int x = left + ContainerImprovedAssembler.PAIR_X[col];
                int y = top + ContainerImprovedAssembler.ROW_Y[row];
                drawTESlot(x - 1, y - 1, schematicHighlight);
                drawTESlot(x + ContainerImprovedAssembler.OUTPUT_OFFSET - 1, y - 1, outputHighlight);
                drawArrow(x + ContainerImprovedAssembler.SLOT_SIZE + 4, y + 5);
            }
        }

        for (int row = 0; row < 2; row++) {
            int rowHighlight = row == 0 ? row1Highlight : row2Highlight;
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + ContainerImprovedAssembler.BUFFER_X + col * ContainerImprovedAssembler.SLOT_SIZE - 1,
                        top + ContainerImprovedAssembler.BUFFER_Y + row * ContainerImprovedAssembler.SLOT_SIZE - 1, rowHighlight);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawTESlot(left + 8 + col * 18 - 1, top + ContainerImprovedAssembler.PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawTESlot(left + 8 + col * 18 - 1, top + ContainerImprovedAssembler.PLAYER_HOTBAR_Y - 1);
        }

        drawEnergyBar(left, top);
        drawTESlot(left + CHARGE_SLOT_X - 1, top + CHARGE_SLOT_Y - 1);
        // Real TE doesn't bake this border into FluidTank.png at all - GuiAssembler layers a
        // separate ElementSlotOverlay on top of the tank, shown only while some side is set to
        // an input-flavored mode (hasSide(1)||hasSide(3)||hasSide(4)). We already compute that
        // exact condition as schematicHighlight above for the schematic slots, so reuse it here
        // instead of re-deriving it; output is included too since our own tank (unlike real
        // TE's) can also be drained via an Output-configured side.
        drawFluidTank(left, top, schematicHighlight != HIGHLIGHT_NONE ? schematicHighlight : outputHighlight);

        augmentsTab.drawBackground(left, top);
        if (tile.augmentReconfigSides) {
            configTab.drawBackground(left, top);
        }
        if (tile.augmentRedstoneControl) {
            redstoneTab.drawBackground(left, top);
        }
        energyTab.drawBackground(left, top);
    }

    /** Small static right-pointing triangle (7x7) marking schematic -> output direction; the real Assembler crafts instantly, so there's no progress fraction to animate here. */
    private void drawArrow(int x, int y) {
        int[] widths = {1, 2, 3, 4, 3, 2, 1};
        for (int row = 0; row < widths.length; row++) {
            drawRect(x, y + row, x + widths[row], y + row + 1, ARROW_COLOR);
        }
    }

    private void drawEnergyBar(int left, int top) {
        drawEnergyStored(left + ENERGY_X, top + ENERGY_Y, tile.getEnergy(), TileImprovedAssembler.ENERGY_CAPACITY);
    }

    /**
     * Real TE's own tank border isn't part of FluidTank.png either (confirmed by decompiling it
     * - that sheet is only the fill-color-underlay math plus the thin red tick marks); the
     * plain socket border real TE always shows, config or not, comes from the ordinary
     * panel-recessed bevel every TE gauge/socket widget sits in (this mod's own energy bar uses
     * the exact same {@link #drawTESocket}). So that always-on bevel is drawn first here, then
     * the fluid fill, then the real tick-mark texture on top, then - only while a side is
     * actually configured - the role-colored ring around the outside. Disabling every side
     * should only drop that outer ring, never the socket itself.
     */
    private void drawFluidTank(int left, int top, int roleColor) {
        int x = left + TANK_X;
        int y = top + TANK_Y;

        drawTESocket(x, y, TANK_WIDTH, TANK_HEIGHT);

        FluidStack fluid = tile.getTankFluid();
        if (fluid != null && fluid.amount > 0) {
            int filled = (int) (TANK_HEIGHT * ((float) fluid.amount / (float) tile.getTankCapacity()));
            if (filled > 0) {
                int rawColor = fluid.getFluid().getColor();
                int color = (rawColor & 0xFFFFFF) == 0xFFFFFF ? TANK_FALLBACK_COLOR : (0xFF000000 | (rawColor & 0xFFFFFF));
                drawRect(x, y + (TANK_HEIGHT - filled), x + TANK_WIDTH, y + TANK_HEIGHT, color);
            }
        }

        drawTankFrame(x, y);
        drawTankRing(x, y, roleColor);
    }

    /** Same 1px ring convention as {@link TabbedMachineGui#drawTESlot(int, int, int)}, just sized for the tank's own 16x60 footprint instead of an 18x18 item slot. */
    private void drawTankRing(int x, int y, int color) {
        drawRect(x - 1, y - 1, x + TANK_WIDTH + 1, y, color);
        drawRect(x - 1, y + TANK_HEIGHT, x + TANK_WIDTH + 1, y + TANK_HEIGHT + 1, color);
        drawRect(x - 1, y - 1, x, y + TANK_HEIGHT + 1, color);
        drawRect(x + TANK_WIDTH, y - 1, x + TANK_WIDTH + 1, y + TANK_HEIGHT + 1, color);
    }

    /**
     * {@code drawTexturedModalRect} always assumes a 256x256 sheet (its UV math divides by
     * 256 unconditionally) - wrong for this 64x64 texture, so this blits the (48,1)-(64,61)
     * region by hand with explicit UV fractions instead, the same "standalone non-256-sheet
     * texture" workaround {@link GuiSideTab#drawIcon16} already uses.
     */
    private void drawTankFrame(int x, int y) {
        mc.getTextureManager().bindTexture(TANK_TEXTURE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        float u1 = 48 / 64F;
        float u2 = 64 / 64F;
        float v1 = 1 / 64F;
        float v2 = 61 / 64F;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + TANK_HEIGHT, zLevel, u1, v2);
        t.addVertexWithUV(x + TANK_WIDTH, y + TANK_HEIGHT, zLevel, u2, v2);
        t.addVertexWithUV(x + TANK_WIDTH, y, zLevel, u2, v1);
        t.addVertexWithUV(x, y, zLevel, u1, v1);
        t.draw();
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
            energyTooltip.add(tile.getEnergy() + " / " + TileImprovedAssembler.ENERGY_CAPACITY + " RF");
            drawHoveringText(energyTooltip, mouseX, mouseY, fontRendererObj);
            return;
        }

        if (mouseX >= left + TANK_X && mouseX < left + TANK_X + TANK_WIDTH
                && mouseY >= top + TANK_Y && mouseY < top + TANK_Y + TANK_HEIGHT) {
            // Matches real ElementFluidTank#addTooltip exactly: the fluid's own name only shows
            // up as a line when there's actually something in the tank, but the "amount /
            // capacity" line is always shown, even at 0.
            List<String> tankTooltip = new ArrayList<String>();
            FluidStack fluid = tile.getTankFluid();
            int amount = fluid != null ? fluid.amount : 0;
            if (fluid != null && amount > 0) {
                tankTooltip.add(fluid.getFluid().getLocalizedName(fluid));
            }
            tankTooltip.add(amount + " / " + tile.getTankCapacity() + " mB");
            drawHoveringText(tankTooltip, mouseX, mouseY, fontRendererObj);
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
}
