package net.thermaladd.mod.client.gui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.thermaladd.mod.inventory.ContainerSingularTransposer;
import net.thermaladd.mod.inventory.ContainerSingularityMachine;
import net.thermaladd.mod.network.MessageMachineMode;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileSingularTransposer;

import cofh.thermalexpansion.util.crafting.TransposerManager.RecipeTransposer;

/**
 * Input column, bubble glyph and fluid arrow per line, output column, then the mode button and
 * the tank. The button uses real TE's own Transposer sprites (fill at u=176, extract at u=192,
 * hover one row down).
 */
public class GuiSingularTransposer extends GuiSingularityMachine {

    private static final int SCALE_X = 67;
    private static final int ARROW_X = 85;
    private static final int BUTTON_X = 133;
    private static final int BUTTON_Y = 35;
    private static final int TANK_X = 153;
    private static final int TANK_Y = 17;
    private static final ResourceLocation SCALE_TEXTURE =
            new ResourceLocation("cofh", "textures/gui/elements/Scale_Bubble.png");
    private static final ResourceLocation TE_TRANSPOSER_TEXTURE =
            new ResourceLocation("thermalexpansion", "textures/gui/machine/Transposer.png");

    private final TileSingularTransposer transposer;

    public GuiSingularTransposer(InventoryPlayer playerInv, TileSingularTransposer tile) {
        super(new ContainerSingularTransposer(playerInv, tile), "info.thermaladd.singularTransposer",
                "info.thermaladd.tip.transposerMode", "info.thermaladd.tip.transposerContainers",
                "info.thermaladd.tip.lock", "info.thermaladd.tip.wrenchFluid", "info.thermaladd.tip.redprint",
                "info.thermaladd.tip.comparatorLines");
        this.transposer = tile;
    }

    private static int rowY(int line) {
        return ContainerSingularTransposer.INPUT_Y + line * ContainerSingularityMachine.SLOT_SIZE
                + (ContainerSingularityMachine.SLOT_SIZE - PROGRESS_ARROW_HEIGHT) / 2;
    }

    /** Each line's arrow, panel-relative - NEI opens TE's Fluid Transposer recipes from it. */
    public static Rectangle[] recipeAreas() {
        Rectangle[] areas = new Rectangle[TileSingularTransposer.LINES];
        for (int line = 0; line < areas.length; line++) {
            areas[line] = new Rectangle(ARROW_X, rowY(line), PROGRESS_ARROW_WIDTH, PROGRESS_ARROW_HEIGHT);
        }
        return areas;
    }

    @Override
    protected void drawMachine(int left, int top) {
        for (int line = 0; line < TileSingularTransposer.LINES; line++) {
            int progress = tile.getProgress(line);
            int max = tile.getProgressMax(line);
            drawActivityScale(SCALE_TEXTURE, left + SCALE_X, top + rowY(line), progress, max);
            drawFluidArrow(FLUID_ARROW_RIGHT, left + ARROW_X, top + rowY(line), progress, max, lineFluid(line), false);
        }
        drawFluidTank(left, top, TANK_X, TANK_Y, tankHighlight());

        mc.getTextureManager().bindTexture(TE_TRANSPOSER_TEXTURE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        int u = transposer.isExtracting() ? 192 : 176;
        int v = isOverButton(mouseXCache, mouseYCache) ? 16 : 0;
        drawTexturedModalRect(left + BUTTON_X, top + BUTTON_Y, u, v, 16, 16);
    }

    private int tankHighlight() {
        if (transposer.isExtracting()) {
            return tile.isFluidDrainedByAnySide() ? HIGHLIGHT_OUTPUT_SECONDARY : HIGHLIGHT_NONE;
        }
        for (int side = 0; side < 6; side++) {
            int mode = tile.getSideMode(side);
            if (mode == TileSingularTransposer.SIDE_MODE_INPUT || mode == TileSingularTransposer.SIDE_MODE_ALL) {
                return HIGHLIGHT_INPUT;
            }
        }
        return HIGHLIGHT_NONE;
    }

    /** What flows through a line's arrow: the tank's fluid when filling, the item's fluid when extracting. */
    private FluidStack lineFluid(int line) {
        if (!transposer.isExtracting()) {
            return tile.getTankFluid();
        }
        RecipeTransposer recipe = transposer.getLineRecipe(line);
        if (recipe != null) {
            return recipe.getFluid();
        }
        ItemStack input = tile.getStackInSlot(line);
        if (input != null && input.getItem() instanceof IFluidContainerItem) {
            return ((IFluidContainerItem) input.getItem()).getFluid(input);
        }
        return null;
    }

    private int mouseXCache = Integer.MIN_VALUE;
    private int mouseYCache = Integer.MIN_VALUE;

    private boolean isOverButton(int mouseX, int mouseY) {
        int x = guiLeft + BUTTON_X;
        int y = guiTop + BUTTON_Y;
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        mouseXCache = mouseX;
        mouseYCache = mouseY;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected boolean machineClicked(int relX, int relY, int mouseButton) {
        if (relX < BUTTON_X || relX >= BUTTON_X + 16 || relY < BUTTON_Y || relY >= BUTTON_Y + 16) {
            return false;
        }
        int next = transposer.isExtracting() ? TileSingularTransposer.MODE_FILL : TileSingularTransposer.MODE_EXTRACT;
        // TE's own pitches: 0.8 switching to fill, 0.6 switching to extract.
        playClick(next == TileSingularTransposer.MODE_FILL ? PITCH_CYCLE_FORWARD : PITCH_CYCLE_BACKWARD);
        PacketHandler.INSTANCE.sendToServer(new MessageMachineMode(tile.xCoord, tile.yCoord, tile.zCoord, next));
        return true;
    }

    @Override
    protected String machineSlotRoleKey(int slot) {
        return slot < TileSingularTransposer.OUTPUT_START ? "gui.thermaladd.mode.input" : "gui.thermaladd.mode.outputItems";
    }

    @Override
    protected boolean drawMachineTooltip(int mouseX, int mouseY) {
        if (isOverButton(mouseX, mouseY)) {
            List<String> lines = new ArrayList<String>();
            lines.add(StatCollector.translateToLocal(transposer.isExtracting()
                    ? "gui.thermaladd.transposer.modeExtract" : "gui.thermaladd.transposer.modeFill"));
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(transposer.isExtracting()
                    ? "gui.thermaladd.transposer.toFill" : "gui.thermaladd.transposer.toExtract"));
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
            return true;
        }
        for (int line = 0; line < TileSingularTransposer.LINES; line++) {
            if (drawProgressTooltip(mouseX, mouseY, SCALE_X, rowY(line), ACTIVITY_SCALE_SIZE,
                    PROGRESS_ARROW_HEIGHT, tile.getProgress(line), tile.getProgressMax(line))) {
                return true;
            }
        }
        return drawTankTooltip(mouseX, mouseY, TANK_X, TANK_Y);
    }
}
