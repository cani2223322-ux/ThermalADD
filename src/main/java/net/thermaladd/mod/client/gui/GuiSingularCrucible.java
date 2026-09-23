package net.thermaladd.mod.client.gui;

import java.awt.Rectangle;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.thermaladd.mod.inventory.ContainerSingularCrucible;
import net.thermaladd.mod.inventory.ContainerSingularityMachine;
import net.thermaladd.mod.tileentity.TileSingularCrucible;

import cofh.thermalexpansion.util.crafting.CrucibleManager;
import cofh.thermalexpansion.util.crafting.CrucibleManager.RecipeCrucible;

/** Input column, flame glyph and fluid arrow per line, all feeding the shared tank. */
public class GuiSingularCrucible extends GuiSingularityMachine {

    private static final int SCALE_X = 67;
    private static final int ARROW_X = 85;
    private static final int TANK_X = 120;
    private static final int TANK_Y = 17;
    /** Real TE's own Crucible glyph (GuiCrucible's speed element). */
    private static final ResourceLocation SCALE_TEXTURE =
            new ResourceLocation("cofh", "textures/gui/elements/Scale_Flame.png");

    public GuiSingularCrucible(InventoryPlayer playerInv, TileSingularCrucible tile) {
        super(new ContainerSingularCrucible(playerInv, tile), "info.thermaladd.singularCrucible",
                "info.thermaladd.tip.fluidOutput", "info.thermaladd.tip.lock", "info.thermaladd.tip.wrenchFluid",
                "info.thermaladd.tip.redprint", "info.thermaladd.tip.comparatorLines");
    }

    private static int rowY(int line) {
        return ContainerSingularCrucible.INPUT_Y + line * ContainerSingularityMachine.SLOT_SIZE
                + (ContainerSingularityMachine.SLOT_SIZE - PROGRESS_ARROW_HEIGHT) / 2;
    }

    /** Each line's arrow, panel-relative - NEI opens TE's Magma Crucible recipes from it. */
    public static Rectangle[] recipeAreas() {
        Rectangle[] areas = new Rectangle[TileSingularCrucible.LINES];
        for (int line = 0; line < areas.length; line++) {
            areas[line] = new Rectangle(ARROW_X, rowY(line), PROGRESS_ARROW_WIDTH, PROGRESS_ARROW_HEIGHT);
        }
        return areas;
    }

    @Override
    protected void drawMachine(int left, int top) {
        for (int line = 0; line < TileSingularCrucible.LINES; line++) {
            int progress = tile.getProgress(line);
            int max = tile.getProgressMax(line);
            drawActivityScale(SCALE_TEXTURE, left + SCALE_X, top + rowY(line), progress, max);
            drawFluidArrow(FLUID_ARROW_RIGHT, left + ARROW_X, top + rowY(line), progress, max, lineFluid(line), false);
        }
        drawFluidTank(left, top, TANK_X, TANK_Y,
                tile.isFluidDrainedByAnySide() ? HIGHLIGHT_OUTPUT : HIGHLIGHT_NONE);
    }

    /** The fluid a line is melting its input into - worked out client-side from the synced slot. */
    private FluidStack lineFluid(int line) {
        ItemStack input = tile.getStackInSlot(line);
        RecipeCrucible recipe = input == null ? null : CrucibleManager.getRecipe(input);
        return recipe == null ? null : recipe.getOutput();
    }

    @Override
    protected String machineSlotRoleKey(int slot) {
        return "gui.thermaladd.mode.input";
    }

    @Override
    protected boolean drawMachineTooltip(int mouseX, int mouseY) {
        for (int line = 0; line < TileSingularCrucible.LINES; line++) {
            if (drawProgressTooltip(mouseX, mouseY, SCALE_X, rowY(line), ACTIVITY_SCALE_SIZE,
                    PROGRESS_ARROW_HEIGHT, tile.getProgress(line), tile.getProgressMax(line))) {
                return true;
            }
        }
        return drawTankTooltip(mouseX, mouseY, TANK_X, TANK_Y);
    }
}
