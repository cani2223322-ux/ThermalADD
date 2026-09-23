package net.thermaladd.mod.client.gui;

import java.awt.Rectangle;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.thermaladd.mod.inventory.ContainerSingularSmelter;
import net.thermaladd.mod.inventory.ContainerSingularityMachine;
import net.thermaladd.mod.tileentity.TileSingularSmelter;

/** Two input columns, then the flame glyph and arrow per line, then the Sawmill's output layout. */
public class GuiSingularSmelter extends GuiSingularityMachine {

    private static final int SCALE_X = 67;
    private static final int ARROW_X = 85;
    /** Real TE's own Smelter glyph (GuiSmelter's speed element). */
    private static final ResourceLocation SCALE_TEXTURE =
            new ResourceLocation("cofh", "textures/gui/elements/Scale_Flame.png");

    public GuiSingularSmelter(InventoryPlayer playerInv, TileSingularSmelter tile) {
        super(new ContainerSingularSmelter(playerInv, tile), "info.thermaladd.singularSmelter",
                "info.thermaladd.tip.smelterPairs", "info.thermaladd.tip.lockSlot", "info.thermaladd.tip.wrench",
                "info.thermaladd.tip.redprint", "info.thermaladd.tip.comparatorLines");
    }

    private static int rowY(int line) {
        return ContainerSingularSmelter.INPUT_Y + line * ContainerSingularityMachine.SLOT_SIZE
                + (ContainerSingularityMachine.SLOT_SIZE - PROGRESS_ARROW_HEIGHT) / 2;
    }

    /** Each line's arrow, panel-relative - NEI opens TE's Induction Smelter recipes from it. */
    public static Rectangle[] recipeAreas() {
        Rectangle[] areas = new Rectangle[TileSingularSmelter.LINES];
        for (int line = 0; line < areas.length; line++) {
            areas[line] = new Rectangle(ARROW_X, rowY(line), PROGRESS_ARROW_WIDTH, PROGRESS_ARROW_HEIGHT);
        }
        return areas;
    }

    @Override
    protected void drawMachine(int left, int top) {
        for (int line = 0; line < TileSingularSmelter.LINES; line++) {
            drawLineProgress(SCALE_TEXTURE, left, top, line, SCALE_X, ARROW_X, rowY(line));
        }
    }

    @Override
    protected int outputHighlight(int slot) {
        return slot >= TileSingularSmelter.OUTPUT_SECONDARY_START ? HIGHLIGHT_OUTPUT_SECONDARY : HIGHLIGHT_OUTPUT_PRIMARY;
    }

    @Override
    protected String machineSlotRoleKey(int slot) {
        if (TileSingularSmelter.isInputA(slot)) {
            return "gui.thermaladd.slot.smelterInputA";
        }
        if (TileSingularSmelter.isInputB(slot)) {
            return "gui.thermaladd.slot.smelterInputB";
        }
        if (slot < TileSingularSmelter.OUTPUT_SECONDARY_START) {
            return "gui.thermaladd.mode.outputPrimary";
        }
        return "gui.thermaladd.mode.outputSecondary";
    }

    @Override
    protected boolean drawMachineTooltip(int mouseX, int mouseY) {
        for (int line = 0; line < TileSingularSmelter.LINES; line++) {
            if (drawProgressTooltip(mouseX, mouseY, SCALE_X, rowY(line), ACTIVITY_SCALE_SIZE,
                    PROGRESS_ARROW_HEIGHT, tile.getProgress(line), tile.getProgressMax(line))) {
                return true;
            }
        }
        return false;
    }
}
