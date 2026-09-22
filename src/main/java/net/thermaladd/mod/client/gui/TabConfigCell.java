package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.network.MessageCycleSide;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileSingularityCell;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Same "unfolded cube" cross layout as this mod's machine Configuration tabs (see TabConfig),
 * but copied from real Thermal Expansion's own Energy Cell side config instead of its machines':
 * only 3 modes per side (Disabled/Output/Input, cofh.thermalexpansion.block.cell.TileCell's own
 * numbering - no Auto), and every side is independently configurable (the cell has no facing to
 * protect, unlike a machine's front face). Since the block itself has no facing either, button
 * positions map straight to absolute ForgeDirection sides (Up/Down/North/South/East/West)
 * rather than being recomputed relative to a facing.
 */
public class TabConfigCell extends GuiSideTab {

    private static final int[] BTN_X = {40, 20, 40, 60, 40, 60};
    private static final int[] BTN_Y = {24, 44, 44, 44, 64, 64};
    /** Index -> absolute ForgeDirection ordinal: Top=Up, Left=West, Front=North, Right=East, Bottom=Down, Back=South. */
    private static final int[] BTN_SIDE = {
            ForgeDirection.UP.ordinal(), ForgeDirection.WEST.ordinal(), ForgeDirection.NORTH.ordinal(),
            ForgeDirection.EAST.ordinal(), ForgeDirection.DOWN.ordinal(), ForgeDirection.SOUTH.ordinal()
    };
    private static final String[] BTN_NAME_KEY = {
            "gui.thermaladd.cell.side.up", "gui.thermaladd.cell.side.west", "gui.thermaladd.cell.side.north",
            "gui.thermaladd.cell.side.east", "gui.thermaladd.cell.side.down", "gui.thermaladd.cell.side.south"};

    private static final int TINT = 0x226688;
    private static final int HEADER = 0xE1C92F;
    private static final int PANEL_X = 16;
    private static final int PANEL_Y = 20;
    private static final int PANEL_SIZE = 64;
    private static final int PANEL_COLOR = darken(TINT, 0.6f);

    private static final ResourceLocation TEX_DISABLED = tex("CellDisabled");
    private static final ResourceLocation TEX_OUTPUT = tex("CellOutput");
    private static final ResourceLocation TEX_INPUT = tex("CellInput");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(ThermalADD.MODID, "textures/blocks/" + name + ".png");
    }

    private static int darken(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private final TileSingularityCell tile;

    public TabConfigCell(TabbedMachineGui gui, TileSingularityCell tile) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Config.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.cell.tab.configuration"));
        this.tile = tile;
    }

    private static ResourceLocation iconForMode(int mode) {
        switch (mode) {
            case TileSingularityCell.MODE_OUTPUT:
                return TEX_OUTPUT;
            case TileSingularityCell.MODE_INPUT:
                return TEX_INPUT;
            default:
                return TEX_DISABLED;
        }
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        Gui.drawRect(x + PANEL_X, y + PANEL_Y, x + PANEL_X + PANEL_SIZE, y + PANEL_Y + PANEL_SIZE, PANEL_COLOR);
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        for (int i = 0; i < 6; i++) {
            int mode = tile.getSideMode(BTN_SIDE[i]);
            drawIcon16(iconForMode(mode), x + BTN_X[i], y + BTN_Y[i]);
            if (isContentHovered(BTN_X[i], BTN_Y[i], 16, 16)) {
                drawHoverFrame(x + BTN_X[i], y + BTN_Y[i]);
            }
        }
    }

    @Override
    public boolean onContentClick(int relX, int relY, int mouseButton, boolean shift) {
        for (int i = 0; i < 6; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y[i] || relY >= BTN_Y[i] + 16) {
                continue;
            }
            // Pitches match real Thermal Expansion's own TabConfigCell exactly.
            int action;
            float pitch;
            if (shift) {
                action = MessageCycleSide.ACTION_RESET_ONE;
                pitch = TabbedMachineGui.PITCH_SET_DISABLED;
            } else {
                action = mouseButton == 1 ? MessageCycleSide.ACTION_BACKWARD : MessageCycleSide.ACTION_FORWARD;
                pitch = mouseButton == 1 ? TabbedMachineGui.PITCH_CYCLE_BACKWARD : TabbedMachineGui.PITCH_CYCLE_FORWARD;
            }
            playClick(pitch);
            PacketHandler.INSTANCE.sendToServer(new MessageCycleSide(tile.xCoord, tile.yCoord, tile.zCoord, BTN_SIDE[i], action));
            return true;
        }
        return false;
    }

    @Override
    protected void addContentTooltip(int relX, int relY, List<String> tooltip) {
        for (int i = 0; i < 6; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y[i] || relY >= BTN_Y[i] + 16) {
                continue;
            }
            tooltip.add(StatCollector.translateToLocal(BTN_NAME_KEY[i]) + ": " + modeName(tile.getSideMode(BTN_SIDE[i])));
            tooltip.add("§7" + StatCollector.translateToLocal("gui.thermaladd.cell.side.hint"));
            return;
        }
    }

    private static String modeName(int mode) {
        switch (mode) {
            case TileSingularityCell.MODE_OUTPUT:
                return StatCollector.translateToLocal("gui.thermaladd.mode.output");
            case TileSingularityCell.MODE_INPUT:
                return StatCollector.translateToLocal("gui.thermaladd.mode.input");
            default:
                return StatCollector.translateToLocal("gui.thermaladd.mode.disabled");
        }
    }
}
