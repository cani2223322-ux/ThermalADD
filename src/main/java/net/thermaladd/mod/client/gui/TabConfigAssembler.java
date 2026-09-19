package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.network.MessageCycleSide;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

import cofh.lib.util.helpers.BlockHelper;

/**
 * Same design as {@link TabConfig} (the Pulverizer's) - real machine-face textures (with the
 * same connection badges shown in-world) on a shared darkened panel, button-to-side mapping
 * recomputed from the live facing - wired to the Improved Cyclic Assembler's own tile. Unlike
 * the other two machines, the Assembler's face icon has no separate Active variant, so the
 * Front button always shows the one idle face texture.
 */
public class TabConfigAssembler extends GuiSideTab {

    // Top, Left, Front, Right, Bottom, Back - exact offsets from cofh.core.gui.element.TabConfiguration.
    private static final int[] BTN_X = {40, 20, 40, 60, 40, 60};
    private static final int[] BTN_Y = {24, 44, 44, 44, 64, 64};
    private static final String[] BTN_NAME_KEY = {
            "gui.improvedassembler.side.top", "gui.improvedassembler.side.left",
            "gui.improvedassembler.side.front", "gui.improvedassembler.side.right",
            "gui.improvedassembler.side.bottom", "gui.improvedassembler.side.back"};
    private static final int FRONT_INDEX = 2;
    private static final int TOP_INDEX = 0;
    private static final int BOTTOM_INDEX = 4;

    private static final int TINT = 0x226688;
    private static final int HEADER = 0xE1C92F;
    private static final int PANEL_X = 16;
    private static final int PANEL_Y = 20;
    private static final int PANEL_SIZE = 64;
    private static final int PANEL_COLOR = darken(TINT, 0.6f);

    private static final ResourceLocation TEX_TOP =
            new ResourceLocation("thermalexpansion", "textures/blocks/machine/Machine_Top.png");
    private static final ResourceLocation TEX_BOTTOM =
            new ResourceLocation("thermalexpansion", "textures/blocks/machine/Machine_Bottom.png");
    private static final ResourceLocation TEX_SIDE =
            new ResourceLocation("thermalexpansion", "textures/blocks/machine/Machine_Side.png");
    private static final ResourceLocation TEX_FACE =
            new ResourceLocation("thermalexpansion", "textures/blocks/machine/Machine_Face_Assembler.png");

    private static final ResourceLocation TEX_TOP_INPUT = badge("TopInput");
    private static final ResourceLocation TEX_TOP_OUTPUT = badge("TopOutput");
    private static final ResourceLocation TEX_TOP_DISABLED = badge("TopDisabled");
    private static final ResourceLocation TEX_BOTTOM_INPUT = badge("BottomInput");
    private static final ResourceLocation TEX_BOTTOM_OUTPUT = badge("BottomOutput");
    private static final ResourceLocation TEX_BOTTOM_DISABLED = badge("BottomDisabled");
    private static final ResourceLocation TEX_SIDE_INPUT = badge("SideInput");
    private static final ResourceLocation TEX_SIDE_OUTPUT = badge("SideOutput");
    private static final ResourceLocation TEX_SIDE_DISABLED = badge("SideDisabled");

    private static ResourceLocation badge(String name) {
        return new ResourceLocation(ThermalADD.MODID, "textures/blocks/" + name + ".png");
    }

    private static int darken(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private final TileImprovedAssembler tile;

    public TabConfigAssembler(TabbedMachineGui gui, TileImprovedAssembler tile) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Config.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.improvedassembler.tab.configuration"));
        this.tile = tile;
    }

    private int[] currentButtonSides() {
        int facing = tile.getFacing();
        return new int[]{
                BlockHelper.getAboveSide(facing),
                BlockHelper.getLeftSide(facing),
                facing,
                BlockHelper.getRightSide(facing),
                BlockHelper.getBelowSide(facing),
                BlockHelper.getOppositeSide(facing)
        };
    }

    private ResourceLocation iconForButton(int index, int mode) {
        if (index == FRONT_INDEX) {
            switch (mode) {
                case TileImprovedAssembler.SIDE_MODE_INPUT:
                    return TEX_SIDE_INPUT;
                case TileImprovedAssembler.SIDE_MODE_OUTPUT:
                    return TEX_SIDE_OUTPUT;
                case TileImprovedAssembler.SIDE_MODE_DISABLED:
                    return TEX_SIDE_DISABLED;
                default:
                    return TEX_FACE;
            }
        }
        if (index == TOP_INDEX) {
            switch (mode) {
                case TileImprovedAssembler.SIDE_MODE_INPUT:
                    return TEX_TOP_INPUT;
                case TileImprovedAssembler.SIDE_MODE_OUTPUT:
                    return TEX_TOP_OUTPUT;
                case TileImprovedAssembler.SIDE_MODE_DISABLED:
                    return TEX_TOP_DISABLED;
                default:
                    return TEX_TOP;
            }
        }
        if (index == BOTTOM_INDEX) {
            switch (mode) {
                case TileImprovedAssembler.SIDE_MODE_INPUT:
                    return TEX_BOTTOM_INPUT;
                case TileImprovedAssembler.SIDE_MODE_OUTPUT:
                    return TEX_BOTTOM_OUTPUT;
                case TileImprovedAssembler.SIDE_MODE_DISABLED:
                    return TEX_BOTTOM_DISABLED;
                default:
                    return TEX_BOTTOM;
            }
        }
        switch (mode) {
            case TileImprovedAssembler.SIDE_MODE_INPUT:
                return TEX_SIDE_INPUT;
            case TileImprovedAssembler.SIDE_MODE_OUTPUT:
                return TEX_SIDE_OUTPUT;
            case TileImprovedAssembler.SIDE_MODE_DISABLED:
                return TEX_SIDE_DISABLED;
            default:
                return TEX_SIDE;
        }
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        Gui.drawRect(x + PANEL_X, y + PANEL_Y, x + PANEL_X + PANEL_SIZE, y + PANEL_Y + PANEL_SIZE, PANEL_COLOR);
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        int[] btnSide = currentButtonSides();
        for (int i = 0; i < 6; i++) {
            int mode = tile.getSideMode(btnSide[i]);
            drawIcon16(iconForButton(i, mode), x + BTN_X[i], y + BTN_Y[i]);
        }
    }

    @Override
    public boolean onContentClick(int relX, int relY, int mouseButton, boolean shift) {
        int[] btnSide = currentButtonSides();
        for (int i = 0; i < 6; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y[i] || relY >= BTN_Y[i] + 16) {
                continue;
            }
            if (!shift && i == FRONT_INDEX) {
                return true;
            }
            int action;
            int side = btnSide[i];
            if (shift) {
                action = i == FRONT_INDEX ? MessageCycleSide.ACTION_RESET_ALL : MessageCycleSide.ACTION_RESET_ONE;
            } else {
                action = mouseButton == 1 ? MessageCycleSide.ACTION_BACKWARD : MessageCycleSide.ACTION_FORWARD;
            }
            PacketHandler.INSTANCE.sendToServer(new MessageCycleSide(tile.xCoord, tile.yCoord, tile.zCoord, side, action));
            return true;
        }
        return false;
    }

    @Override
    protected void addContentTooltip(int relX, int relY, List<String> tooltip) {
        int[] btnSide = currentButtonSides();
        for (int i = 0; i < 6; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y[i] || relY >= BTN_Y[i] + 16) {
                continue;
            }
            tooltip.add(StatCollector.translateToLocal(BTN_NAME_KEY[i]) + ": " + modeName(tile.getSideMode(btnSide[i])));
            tooltip.add("§7" + StatCollector.translateToLocal("gui.improvedassembler.side.hint"));
            return;
        }
    }

    private static String modeName(int mode) {
        switch (mode) {
            case TileImprovedAssembler.SIDE_MODE_INPUT:
                return StatCollector.translateToLocal("gui.improvedassembler.mode.input");
            case TileImprovedAssembler.SIDE_MODE_OUTPUT:
                return StatCollector.translateToLocal("gui.improvedassembler.mode.output");
            case TileImprovedAssembler.SIDE_MODE_DISABLED:
                return StatCollector.translateToLocal("gui.improvedassembler.mode.disabled");
            default:
                return StatCollector.translateToLocal("gui.improvedassembler.mode.auto");
        }
    }
}
