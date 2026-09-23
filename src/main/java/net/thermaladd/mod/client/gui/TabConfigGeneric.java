package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.network.MessageCycleSide;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileSingularityMachine;

import cofh.lib.util.helpers.BlockHelper;

/**
 * "Configuration" tab for the TileSingularityMachine family - the same layout and interaction as
 * TabConfigSawmill, with the face textures, badges and mode names read from the tile instead of
 * being hardcoded per machine.
 */
public class TabConfigGeneric extends GuiSideTab {

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

    private final TileSingularityMachine tile;
    private final ResourceLocation faceIdle;
    private final ResourceLocation faceActive;
    private final ResourceLocation[] top;
    private final ResourceLocation[] bottom;
    private final ResourceLocation[] side;

    public TabConfigGeneric(TabbedMachineGui gui, TileSingularityMachine tile) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Config.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.tab.configuration"));
        this.tile = tile;
        faceIdle = new ResourceLocation("thermalexpansion",
                "textures/blocks/machine/Machine_Face_" + tile.getFaceTextureName() + ".png");
        faceActive = new ResourceLocation("thermalexpansion",
                "textures/blocks/machine/Machine_Active_" + tile.getFaceTextureName() + ".png");
        String[] badges = tile.getSideModeBadges();
        top = new ResourceLocation[badges.length];
        bottom = new ResourceLocation[badges.length];
        side = new ResourceLocation[badges.length];
        for (int mode = 0; mode < badges.length; mode++) {
            top[mode] = badges[mode] == null ? TEX_TOP : badge("Top" + badges[mode]);
            bottom[mode] = badges[mode] == null ? TEX_BOTTOM : badge("Bottom" + badges[mode]);
            side[mode] = badges[mode] == null ? TEX_SIDE : badge("Side" + badges[mode]);
        }
    }

    private static ResourceLocation badge(String name) {
        return new ResourceLocation(ThermalADD.MODID, "textures/blocks/" + name + ".png");
    }

    private static int darken(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
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
            return tile.isActive() ? faceActive : faceIdle;
        }
        if (mode < 0 || mode >= top.length) {
            mode = 0;
        }
        if (index == TOP_INDEX) {
            return top[mode];
        }
        if (index == BOTTOM_INDEX) {
            return bottom[mode];
        }
        return side[mode];
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        Gui.drawRect(x + PANEL_X, y + PANEL_Y, x + PANEL_X + PANEL_SIZE, y + PANEL_Y + PANEL_SIZE, PANEL_COLOR);
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        int[] btnSide = currentButtonSides();
        for (int i = 0; i < 6; i++) {
            drawIcon16(iconForButton(i, tile.getSideMode(btnSide[i])), x + BTN_X[i], y + BTN_Y[i]);
            if (isContentHovered(BTN_X[i], BTN_Y[i], 16, 16)) {
                drawHoverFrame(x + BTN_X[i], y + BTN_Y[i]);
            }
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
            float pitch;
            if (shift) {
                action = i == FRONT_INDEX ? MessageCycleSide.ACTION_RESET_ALL : MessageCycleSide.ACTION_RESET_ONE;
                pitch = i == FRONT_INDEX ? TabbedMachineGui.PITCH_RESET_ALL : TabbedMachineGui.PITCH_SET_DISABLED;
            } else {
                action = mouseButton == 1 ? MessageCycleSide.ACTION_BACKWARD : MessageCycleSide.ACTION_FORWARD;
                pitch = mouseButton == 1 ? TabbedMachineGui.PITCH_CYCLE_BACKWARD : TabbedMachineGui.PITCH_CYCLE_FORWARD;
            }
            playClick(pitch);
            PacketHandler.INSTANCE.sendToServer(new MessageCycleSide(tile.xCoord, tile.yCoord, tile.zCoord, btnSide[i], action));
            return true;
        }
        return false;
    }

    @Override
    protected void addContentTooltip(int relX, int relY, List<String> tooltip) {
        int[] btnSide = currentButtonSides();
        String[] names = tile.getSideModeNameKeys();
        for (int i = 0; i < 6; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y[i] || relY >= BTN_Y[i] + 16) {
                continue;
            }
            int mode = tile.getSideMode(btnSide[i]);
            String name = StatCollector.translateToLocal(names[mode >= 0 && mode < names.length ? mode : 0]);
            tooltip.add(StatCollector.translateToLocal(BTN_NAME_KEY[i]) + ": " + name);
            tooltip.add("§7" + StatCollector.translateToLocal("gui.thermaladd.side.hint"));
            return;
        }
    }
}
