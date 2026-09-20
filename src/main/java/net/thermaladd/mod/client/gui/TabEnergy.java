package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import cofh.api.tileentity.IEnergyInfo;

/**
 * "Energy" tab - same design as real Thermal Expansion's own cofh.core.gui.element.TabEnergy:
 * a dedicated tab (not a hover tooltip on the RF bar) showing Consumption/Max Energy per
 * tick/Stored, replacing the hover-only info box every machine GUI in this mod used to show.
 * Colors match TabEnergy's own decompiled defaults exactly - header 0xE1C92F, subheader
 * 0xAAAFB8, text black, tint 0x0A76D0 (TabEnergy.defaultBackgroundColorIn, the "consumer"
 * color real TE uses for machines that draw power rather than produce it, which is every
 * machine in this mod). The real Icon_Energy.png icon (cofh:textures/items/icons/
 * Icon_Energy.png) is used directly, same "reference the real PNG" trick the Configuration/
 * Redstone Control tabs already use.
 *
 * Docked on the LEFT edge (the {@code leftSide=true} GuiSideTab constructor) rather than
 * stacked with the other 3 tabs on the right - real TE's own TabEnergy defaults to the left
 * side too ({@code TabEnergy.defaultSide == LEFT}), keeping it visually separate from the
 * augment/configuration/redstone cluster.
 */
public class TabEnergy extends GuiSideTab {

    private static final int SUBHEADER = 0xAAAFB8;
    private static final int TEXT = 0x000000;
    private static final int TINT = 0x0A76D0;
    private static final int HEADER = 0xE1C92F;

    private static final ResourceLocation ICON_ENERGY = new ResourceLocation("cofh", "textures/items/icons/Icon_Energy.png");

    private final TabbedMachineGui gui;
    private final IEnergyInfo tile;

    public TabEnergy(TabbedMachineGui gui, IEnergyInfo tile) {
        super(gui, ICON_ENERGY, TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.energy.title"), true);
        this.gui = gui;
        this.tile = tile;
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        gui.getTabFontRenderer().drawString(StatCollector.translateToLocal("gui.thermaladd.energy.tab.consumption"),
                x + 6, y + 18, SUBHEADER);
        gui.getTabFontRenderer().drawString(tile.getInfoEnergyPerTick() + " RF/t", x + 14, y + 30, TEXT);

        gui.getTabFontRenderer().drawString(StatCollector.translateToLocal("gui.thermaladd.energy.tab.maxpower"),
                x + 6, y + 42, SUBHEADER);
        gui.getTabFontRenderer().drawString(tile.getInfoMaxEnergyPerTick() + " RF/t", x + 14, y + 54, TEXT);

        gui.getTabFontRenderer().drawString(StatCollector.translateToLocal("gui.thermaladd.energy.tab.stored"),
                x + 6, y + 66, SUBHEADER);
        gui.getTabFontRenderer().drawString(tile.getInfoEnergyStored() + " RF", x + 14, y + 78, TEXT);
    }

    /**
     * Collapsed hover shows the live RF/t draw instead of the generic tab-title tooltip
     * GuiSideTab shows by default, matching real TabEnergy#addTooltip exactly - which also
     * shows nothing extra while fully open, since the tab's own text already covers everything.
     */
    @Override
    public void addTooltip(int mouseX, int mouseY, int left, int top, List<String> tooltip) {
        if (!isFullyOpen()) {
            if (isMouseOverIcon(mouseX, mouseY, left, top)) {
                tooltip.add(tile.getInfoEnergyPerTick() + " RF/t");
            }
        }
    }
}
