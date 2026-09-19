package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import cofh.api.tileentity.IRedstoneControl;
import net.thermaladd.mod.network.MessageSetRedstoneControl;
import net.thermaladd.mod.network.PacketHandler;

/**
 * Same tab as real Thermal Expansion's cofh.core.gui.element.TabRedstone: 3 buttons (Disabled/
 * Low/High), each a button-frame icon with an overlaid status icon on top - reusing CoFHCore's
 * own real texture files directly (the same "reference the real PNG as a standalone texture"
 * trick {@link TabConfig} already uses for Icon_Config.png and the machine face/casing
 * textures), rather than going through CoFHCore's runtime IconRegistry/item-atlas system:
 * IconGunpowder/IconRedstone are literally the vanilla Gunpowder/Redstone Dust item textures,
 * IconRSTorchOff/On and the 3 button-frame icons are cofh's own icons/ textures - all
 * confirmed from CoFHCore's decompiled ProxyClient (where it registers them into IconRegistry
 * under those exact "cofh:icons/Name" paths, which is the same "cofh:icons/Icon_Config" shape
 * this mod's Configuration tab already references successfully).
 *
 * Colors match TabRedstone's own defaults exactly (decompiled from CoFHCore 3.1.4-329):
 * header 0xE1C92F, subheader 0xAAAFB8, text black, background/tint 0xD0230A (red). Button
 * x-positions are compacted from TE's 28/48/68 (needs a 112-wide tab) down to 20/40/60 to fit
 * this mod's shared 100-wide tab budget (see GuiSideTab.MAX_WIDTH) - the same spacing/size this
 * mod's own Configuration tab already uses.
 */
public class TabRedstoneControl extends GuiSideTab {

    private static final int[] BTN_X = {20, 40, 60};
    private static final int BTN_Y = 20;

    private static final int TINT = 0xD0230A;
    private static final int HEADER = 0xE1C92F;
    private static final int SUBHEADER = 0xAAAFB8;
    private static final int TEXT = 0x000000;

    private static final int PANEL_X = 16;
    private static final int PANEL_Y = 16;
    private static final int PANEL_WIDTH = 64;
    private static final int PANEL_HEIGHT = 24;
    private static final int PANEL_COLOR = darken(TINT, 0.6f);

    private static final ResourceLocation TEX_GUNPOWDER = new ResourceLocation("minecraft", "textures/items/gunpowder.png");
    private static final ResourceLocation TEX_REDSTONE = new ResourceLocation("minecraft", "textures/items/redstone_dust.png");
    private static final ResourceLocation TEX_TORCH_OFF = new ResourceLocation("cofh", "textures/items/icons/Icon_RSTorchOff.png");
    private static final ResourceLocation TEX_TORCH_ON = new ResourceLocation("cofh", "textures/items/icons/Icon_RSTorchOn.png");
    private static final ResourceLocation TEX_BUTTON = new ResourceLocation("cofh", "textures/items/icons/Icon_Button.png");
    private static final ResourceLocation TEX_BUTTON_HIGHLIGHT = new ResourceLocation("cofh", "textures/items/icons/Icon_Button_Highlight.png");

    private static int darken(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private final TabbedMachineGui gui;
    private final int x, y, z;
    private final IRedstoneControl tile;

    public TabRedstoneControl(TabbedMachineGui gui, int x, int y, int z, IRedstoneControl tile) {
        super(gui, TEX_REDSTONE, TINT, HEADER, StatCollector.translateToLocal("info.cofh.redstoneControl"));
        this.gui = gui;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tile = tile;
    }

    @Override
    protected void drawContentBackground(int x, int y) {
        Gui.drawRect(x + PANEL_X, y + PANEL_Y, x + PANEL_X + PANEL_WIDTH, y + PANEL_Y + PANEL_HEIGHT, PANEL_COLOR);
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        IRedstoneControl.ControlMode mode = tile.getControl();

        ResourceLocation btn0Icon;
        String statusLine;
        String signalLine;
        boolean pressed0 = false;
        boolean pressed1 = false;
        boolean pressed2 = false;

        if (mode.isDisabled()) {
            btn0Icon = TEX_GUNPOWDER;
            pressed0 = true;
            statusLine = StatCollector.translateToLocal("info.cofh.disabled");
            signalLine = StatCollector.translateToLocal("info.cofh.ignored");
        } else {
            btn0Icon = TEX_REDSTONE;
            statusLine = StatCollector.translateToLocal("info.cofh.enabled");
            if (mode.isLow()) {
                pressed1 = true;
                signalLine = StatCollector.translateToLocal("info.cofh.low");
            } else {
                pressed2 = true;
                signalLine = StatCollector.translateToLocal("info.cofh.high");
            }
        }

        drawButtonIcon(btn0Icon, x + BTN_X[0], y + BTN_Y, pressed0);
        drawButtonIcon(TEX_TORCH_OFF, x + BTN_X[1], y + BTN_Y, pressed1);
        drawButtonIcon(TEX_TORCH_ON, x + BTN_X[2], y + BTN_Y, pressed2);

        gui.getTabFontRenderer().drawString(StatCollector.translateToLocal("info.cofh.controlStatus") + ":",
                x + 6, y + 42, SUBHEADER);
        gui.getTabFontRenderer().drawString(statusLine, x + 14, y + 54, TEXT);
        gui.getTabFontRenderer().drawString(StatCollector.translateToLocal("info.cofh.signalRequired") + ":",
                x + 6, y + 66, SUBHEADER);
        gui.getTabFontRenderer().drawString(signalLine, x + 14, y + 78, TEXT);
    }

    /** Button-frame icon (idle or highlighted, matching cofh.core.gui.GuiBaseAdv#drawButton) with the status icon layered on top. */
    private void drawButtonIcon(ResourceLocation icon, int px, int py, boolean pressed) {
        drawIcon16(pressed ? TEX_BUTTON_HIGHLIGHT : TEX_BUTTON, px, py);
        drawIcon16(icon, px, py);
    }

    @Override
    public boolean onContentClick(int relX, int relY, int mouseButton, boolean shift) {
        for (int i = 0; i < BTN_X.length; i++) {
            if (relX < BTN_X[i] || relX >= BTN_X[i] + 16 || relY < BTN_Y || relY >= BTN_Y + 16) {
                continue;
            }
            IRedstoneControl.ControlMode target = i == 0 ? IRedstoneControl.ControlMode.DISABLED
                    : i == 1 ? IRedstoneControl.ControlMode.LOW : IRedstoneControl.ControlMode.HIGH;
            if (tile.getControl() != target) {
                PacketHandler.INSTANCE.sendToServer(new MessageSetRedstoneControl(x, y, z, target.ordinal()));
            }
            return true;
        }
        return false;
    }

    /** Collapsed hover shows the current status, matching real TabRedstone - not the generic tab-title tooltip GuiSideTab shows by default. */
    @Override
    public void addTooltip(int mouseX, int mouseY, int left, int top, List<String> tooltip) {
        if (!isFullyOpen()) {
            if (isMouseOverIcon(mouseX, mouseY, left, top)) {
                IRedstoneControl.ControlMode mode = tile.getControl();
                if (mode.isDisabled()) {
                    tooltip.add(StatCollector.translateToLocal("info.cofh.disabled"));
                } else if (mode.isLow()) {
                    tooltip.add(StatCollector.translateToLocal("info.cofh.enabled") + ", " + StatCollector.translateToLocal("info.cofh.low"));
                } else {
                    tooltip.add(StatCollector.translateToLocal("info.cofh.enabled") + ", " + StatCollector.translateToLocal("info.cofh.high"));
                }
            }
            return;
        }
        int relX = mouseX - (left + getTabX());
        int relY = mouseY - (top + getTabY());
        List<String> names = new ArrayList<String>();
        names.add(StatCollector.translateToLocal("info.cofh.disabled"));
        names.add(StatCollector.translateToLocal("info.cofh.low"));
        names.add(StatCollector.translateToLocal("info.cofh.high"));
        for (int i = 0; i < BTN_X.length; i++) {
            if (relX >= BTN_X[i] && relX < BTN_X[i] + 16 && relY >= BTN_Y && relY < BTN_Y + 16) {
                tooltip.add(names.get(i));
                return;
            }
        }
    }
}
