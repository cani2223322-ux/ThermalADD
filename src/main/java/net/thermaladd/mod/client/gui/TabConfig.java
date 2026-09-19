package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.network.MessageCycleSide;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

/**
 * "Configuration" tab - the same "unfolded cube" 6-button cross layout as Thermal
 * Expansion's real TabConfiguration (cofh.core.gui.element.TabConfiguration): Top/Left/
 * Front/Right/Bottom/Back around a center query button, at the exact same pixel offsets.
 * Left-click cycles a side forward, right-click cycles it backward, shift-click resets it
 * (shift-click the center resets every side) - identical to the real interaction model.
 * Only reachable once the Reconfigurable Sides augment is installed, same as real TE.
 */
public class TabConfig extends GuiSideTab {

    // Top, Left, Front, Right, Bottom, Back - exact offsets from cofh.core.gui.element.TabConfiguration.
    private static final int[] BTN_X = {40, 20, 40, 60, 40, 60};
    private static final int[] BTN_Y = {24, 44, 44, 44, 64, 64};
    // Fixed absolute side per button (this block has no facing/rotation): Up, West, North, East, Down, South.
    private static final int[] BTN_SIDE = {1, 4, 2, 5, 0, 3};
    private static final String[] BTN_NAME_KEY = {
            "gui.thermaladd.side.up", "gui.thermaladd.side.west",
            "gui.thermaladd.side.north", "gui.thermaladd.side.east",
            "gui.thermaladd.side.down", "gui.thermaladd.side.south"};
    private static final int FRONT_INDEX = 2;

    private static final int TINT = 0x226688;
    private static final int HEADER = 0xE1C92F;

    private static final ResourceLocation ICON_UP =
            new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowUp.png");
    private static final ResourceLocation ICON_DOWN =
            new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowDown.png");
    private static final ResourceLocation ICON_NOPE =
            new ResourceLocation("cofh", "textures/items/icons/Icon_Nope.png");

    private final TileAdvancedPulverizer tile;

    public TabConfig(GuiAdvancedPulverizer gui, TileAdvancedPulverizer tile) {
        super(gui, new ResourceLocation("cofh", "textures/items/icons/Icon_Config.png"),
                TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.tab.configuration"));
        this.tile = tile;
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        for (int i = 0; i < 6; i++) {
            int mode = tile.getSideMode(BTN_SIDE[i]);
            int bx = x + BTN_X[i];
            int by = y + BTN_Y[i];
            Gui.drawRect(bx - 1, by - 1, bx + 17, by + 17, 0xFF8B8B8B);
            Gui.drawRect(bx, by, bx + 16, by + 16, modeColor(mode));
        }
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        for (int i = 0; i < 6; i++) {
            int mode = tile.getSideMode(BTN_SIDE[i]);
            ResourceLocation icon = modeIcon(mode);
            if (icon != null) {
                drawIcon16(icon, x + BTN_X[i], y + BTN_Y[i]);
            }
        }
    }

    @Override
    public boolean onContentClick(int relX, int relY, int mouseButton, boolean shift) {
        for (int i = 0; i < 6; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y[i] || relY >= BTN_Y[i] + 16) {
                continue;
            }
            int action;
            int side = BTN_SIDE[i];
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
        for (int i = 0; i < 6; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y[i] || relY >= BTN_Y[i] + 16) {
                continue;
            }
            tooltip.add(StatCollector.translateToLocal(BTN_NAME_KEY[i]) + ": " + modeName(tile.getSideMode(BTN_SIDE[i])));
            tooltip.add("§7" + StatCollector.translateToLocal("gui.thermaladd.side.hint"));
            return;
        }
    }

    private static String modeName(int mode) {
        switch (mode) {
            case TileAdvancedPulverizer.SIDE_MODE_INPUT:
                return StatCollector.translateToLocal("gui.thermaladd.mode.input");
            case TileAdvancedPulverizer.SIDE_MODE_OUTPUT:
                return StatCollector.translateToLocal("gui.thermaladd.mode.output");
            case TileAdvancedPulverizer.SIDE_MODE_DISABLED:
                return StatCollector.translateToLocal("gui.thermaladd.mode.disabled");
            default:
                return StatCollector.translateToLocal("gui.thermaladd.mode.auto");
        }
    }

    private static int modeColor(int mode) {
        switch (mode) {
            case TileAdvancedPulverizer.SIDE_MODE_INPUT:
                return 0xFF3070C0;
            case TileAdvancedPulverizer.SIDE_MODE_OUTPUT:
                return 0xFFC08020;
            case TileAdvancedPulverizer.SIDE_MODE_DISABLED:
                return 0xFF802020;
            default:
                return 0xFF308030;
        }
    }

    private static ResourceLocation modeIcon(int mode) {
        switch (mode) {
            case TileAdvancedPulverizer.SIDE_MODE_INPUT:
                return ICON_UP;
            case TileAdvancedPulverizer.SIDE_MODE_OUTPUT:
                return ICON_DOWN;
            case TileAdvancedPulverizer.SIDE_MODE_DISABLED:
                return ICON_NOPE;
            default:
                return null;
        }
    }
}
