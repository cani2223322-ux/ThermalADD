package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.network.MessageCycleSide;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;

import cofh.lib.util.helpers.BlockHelper;

/**
 * Same design as {@link TabConfig} (the Pulverizer's) - real machine-face textures (with
 * the same connection badges shown in-world) on a shared darkened panel, button-to-side
 * mapping recomputed from the live facing - wired to the Furnace's own tile.
 */
public class TabConfigFurnace extends GuiSideTab {

    private static final int[] BTN_X = {40, 20, 40, 60, 40, 60};
    private static final int[] BTN_Y = {24, 44, 44, 44, 64, 64};
    private static final String[] BTN_NAME_KEY = {
            "gui.thermaladd.side.top", "gui.thermaladd.side.left",
            "gui.thermaladd.side.front", "gui.thermaladd.side.right",
            "gui.thermaladd.side.bottom", "gui.thermaladd.side.back"};
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
    private static final ResourceLocation TEX_FACE_IDLE =
            new ResourceLocation("thermalexpansion", "textures/blocks/machine/Machine_Face_Furnace.png");
    private static final ResourceLocation TEX_FACE_ACTIVE =
            new ResourceLocation("thermalexpansion", "textures/blocks/machine/Machine_Active_Furnace.png");

    private static final ResourceLocation TEX_TOP_INPUT = badge("TopInput");
    private static final ResourceLocation TEX_TOP_OUTPUT = badge("TopOutput");
    private static final ResourceLocation TEX_TOP_ALL = badge("TopAll");
    private static final ResourceLocation TEX_BOTTOM_INPUT = badge("BottomInput");
    private static final ResourceLocation TEX_BOTTOM_OUTPUT = badge("BottomOutput");
    private static final ResourceLocation TEX_BOTTOM_ALL = badge("BottomAll");
    private static final ResourceLocation TEX_SIDE_INPUT = badge("SideInput");
    private static final ResourceLocation TEX_SIDE_OUTPUT = badge("SideOutput");
    private static final ResourceLocation TEX_SIDE_ALL = badge("SideAll");

    private static ResourceLocation badge(String name) {
        return new ResourceLocation(ThermalADD.MODID, "textures/blocks/" + name + ".png");
    }

    private static int darken(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private final TileAdvancedFurnace tile;

    public TabConfigFurnace(GuiAdvancedFurnace gui, TileAdvancedFurnace tile) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Config.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.tab.configuration"));
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

    /**
     * The front face itself always shows the live machine face/casing regardless of mode - it
     * can never actually be reconfigured (permanently forced to Disabled, see
     * {@code TileAdvancedFurnace#setDefaultSides}) - exactly like real Thermal Expansion's own
     * TabConfiguration.
     */
    private ResourceLocation iconForButton(int index, int mode) {
        if (index == FRONT_INDEX) {
            return tile.isActive() ? TEX_FACE_ACTIVE : TEX_FACE_IDLE;
        }
        if (index == TOP_INDEX) {
            switch (mode) {
                case TileAdvancedFurnace.SIDE_MODE_INPUT:
                    return TEX_TOP_INPUT;
                case TileAdvancedFurnace.SIDE_MODE_OUTPUT:
                    return TEX_TOP_OUTPUT;
                case TileAdvancedFurnace.SIDE_MODE_ALL:
                    return TEX_TOP_ALL;
                default:
                    return TEX_TOP;
            }
        }
        if (index == BOTTOM_INDEX) {
            switch (mode) {
                case TileAdvancedFurnace.SIDE_MODE_INPUT:
                    return TEX_BOTTOM_INPUT;
                case TileAdvancedFurnace.SIDE_MODE_OUTPUT:
                    return TEX_BOTTOM_OUTPUT;
                case TileAdvancedFurnace.SIDE_MODE_ALL:
                    return TEX_BOTTOM_ALL;
                default:
                    return TEX_BOTTOM;
            }
        }
        switch (mode) {
            case TileAdvancedFurnace.SIDE_MODE_INPUT:
                return TEX_SIDE_INPUT;
            case TileAdvancedFurnace.SIDE_MODE_OUTPUT:
                return TEX_SIDE_OUTPUT;
            case TileAdvancedFurnace.SIDE_MODE_ALL:
                return TEX_SIDE_ALL;
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
            tooltip.add("§7" + StatCollector.translateToLocal("gui.thermaladd.side.hint"));
            return;
        }
    }

    private static String modeName(int mode) {
        switch (mode) {
            case TileAdvancedFurnace.SIDE_MODE_INPUT:
                return StatCollector.translateToLocal("gui.thermaladd.mode.input");
            case TileAdvancedFurnace.SIDE_MODE_OUTPUT:
                return StatCollector.translateToLocal("gui.thermaladd.mode.output");
            case TileAdvancedFurnace.SIDE_MODE_ALL:
                return StatCollector.translateToLocal("gui.thermaladd.mode.all");
            default:
                return StatCollector.translateToLocal("gui.thermaladd.mode.disabled");
        }
    }
}
