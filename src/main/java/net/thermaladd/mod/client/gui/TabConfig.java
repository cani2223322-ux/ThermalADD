package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.thermaladd.mod.network.MessageCycleSide;
import net.thermaladd.mod.network.PacketHandler;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;

import cofh.lib.util.helpers.BlockHelper;

/**
 * "Configuration" tab - the same "unfolded cube" 6-button cross layout as Thermal
 * Expansion's real TabConfiguration (cofh.core.gui.element.TabConfiguration): Top/Left/
 * Front/Right/Bottom/Back around a center query button, at the exact same pixel offsets.
 * Left-click cycles a side forward, right-click cycles it backward, shift-click resets it
 * (shift-click the center resets every side) - identical to the real interaction model.
 * Only reachable once the Reconfigurable Sides augment is installed, same as real TE.
 *
 * Which PHYSICAL side each button controls is recomputed from the machine's live facing
 * every time it's needed, not fixed - Top/Bottom always mean Up/Down (this block never
 * tilts), but Left/Right/Front/Back rotate with the block's facing, using the same
 * {@link BlockHelper#getLeftSide}/{@code getRightSide}/{@code getOppositeSide} lookup
 * tables real Thermal Expansion uses internally (see TileAugmentable#readPortableTagInternal
 * in the decompiled TE source). A fixed North/West/East/South mapping - what this class used
 * to do - is only correct when the block happens to be facing North; rotate it with the
 * Crescent Hammer and the buttons would silently control the wrong faces.
 */
public class TabConfig extends GuiSideTab {

    // Top, Left, Front, Right, Bottom, Back - exact offsets from cofh.core.gui.element.TabConfiguration.
    private static final int[] BTN_X = {40, 20, 40, 60, 40, 60};
    private static final int[] BTN_Y = {24, 44, 44, 44, 64, 64};
    // Relative position labels (not compass directions - which physical side is "Left" etc. depends on facing).
    private static final String[] BTN_NAME_KEY = {
            "gui.thermaladd.side.top", "gui.thermaladd.side.left",
            "gui.thermaladd.side.front", "gui.thermaladd.side.right",
            "gui.thermaladd.side.bottom", "gui.thermaladd.side.back"};
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
