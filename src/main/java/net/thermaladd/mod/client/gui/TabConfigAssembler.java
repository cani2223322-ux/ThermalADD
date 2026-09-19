package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.network.MessageCycleSide;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

import cofh.lib.util.helpers.BlockHelper;

/**
 * "Configuration" tab for the Improved Cyclic Assembler - same "unfolded cube" 6-button
 * cross layout as Thermal Expansion's real TabConfiguration: Top/Left/Front/Right/Bottom/
 * Back around a center query button. Left-click cycles a side forward, right-click cycles it
 * backward, shift-click resets it (shift-click the center resets every side). Only reachable
 * once the Reconfigurable Sides augment is installed, same as real TE.
 *
 * Button-to-side mapping is recomputed from the live facing every time - see TabConfig's
 * class comment for why a fixed North/West/East/South mapping is wrong the moment the block
 * is rotated with the Crescent Hammer.
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

    private static final int TINT = 0x226688;
    private static final int HEADER = 0xE1C92F;

    private static final ResourceLocation ICON_UP =
            new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowUp.png");
    private static final ResourceLocation ICON_DOWN =
            new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowDown.png");
    private static final ResourceLocation ICON_NOPE =
            new ResourceLocation("cofh", "textures/items/icons/Icon_Nope.png");

    private final TileImprovedAssembler tile;

    public TabConfigAssembler(TabbedMachineGui gui, TileImprovedAssembler tile) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Config.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.improvedassembler.tab.configuration"));
        this.tile = tile;
    }

    private int[] currentButtonSides() {
        int facing = tile.getFacing();
        return new int[]{
                1,
                BlockHelper.getLeftSide(facing),
                facing,
                BlockHelper.getRightSide(facing),
                0,
                BlockHelper.getOppositeSide(facing)
        };
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        int[] btnSide = currentButtonSides();
        for (int i = 0; i < 6; i++) {
            int mode = tile.getSideMode(btnSide[i]);
            int bx = x + BTN_X[i];
            int by = y + BTN_Y[i];
            Gui.drawRect(bx - 1, by - 1, bx + 17, by + 17, 0xFF8B8B8B);
            Gui.drawRect(bx, by, bx + 16, by + 16, modeColor(mode));
        }
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        int[] btnSide = currentButtonSides();
        for (int i = 0; i < 6; i++) {
            int mode = tile.getSideMode(btnSide[i]);
            ResourceLocation icon = modeIcon(mode);
            if (icon != null) {
                drawIcon16(icon, x + BTN_X[i], y + BTN_Y[i]);
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

    private static int modeColor(int mode) {
        switch (mode) {
            case TileImprovedAssembler.SIDE_MODE_INPUT:
                return 0xFF3070C0;
            case TileImprovedAssembler.SIDE_MODE_OUTPUT:
                return 0xFFC08020;
            case TileImprovedAssembler.SIDE_MODE_DISABLED:
                return 0xFF802020;
            default:
                return 0xFF308030;
        }
    }

    private static ResourceLocation modeIcon(int mode) {
        switch (mode) {
            case TileImprovedAssembler.SIDE_MODE_INPUT:
                return ICON_UP;
            case TileImprovedAssembler.SIDE_MODE_OUTPUT:
                return ICON_DOWN;
            case TileImprovedAssembler.SIDE_MODE_DISABLED:
                return ICON_NOPE;
            default:
                return null;
        }
    }
}
