package net.thermaladd.mod.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

/**
 * Thin common base for every machine GUI in this mod that has CoFH-style side tabs
 * (Augments/Configuration/Energy). Exists purely so {@link GuiSideTab} can be shared between
 * GuiAdvancedPulverizer and GuiImprovedAssembler instead of each having its own copy: Java's
 * "protected" access to GuiScreen's inherited fields (mc/fontRendererObj/zLevel) and methods
 * (drawTexturedModalRect) is only visible to other classes in the SAME package through a
 * reference typed as a class declared in that package - a plain GuiContainer-typed reference
 * would not compile here, since GuiContainer itself lives in net.minecraft.client.gui.inventory.
 */
public abstract class TabbedMachineGui extends GuiContainer {

    /**
     * Palette pixel-matched against real Thermal Expansion's own machine GUI textures (verified
     * by scanning cofh.thermalexpansion:textures/gui/machine/Pulverizer.png directly): the panel
     * fill, slot fill, black outline and the two bevel tones are the exact same RGB values TE
     * itself bakes into its GUI PNGs - only the layering below is reimplemented in code, since
     * this mod's machines have extra/taller layouts no stock TE texture covers.
     */
    protected static final int TE_PANEL = 0xFFC6C6C6;
    protected static final int TE_PANEL_DARK = 0xFF8B8B8B;
    protected static final int TE_BLACK = 0xFF000000;
    protected static final int TE_HIGHLIGHT = 0xFFFFFFFF;
    protected static final int TE_SHADOW = 0xFF555555;
    protected static final int TE_SLOT_DARK = 0xFF373737;

    /**
     * Slot role-highlight colors, matching this mod's own side-config badge palette exactly
     * (see BlockAdvancedPulverizer's javadoc for how those were verified against real TE's
     * Config_Blue/Red/Yellow/Orange.png) - the same "read the slot, not just the Configuration
     * tab" idea real Thermal Expansion uses: an input slot's frame is tinted blue, a primary
     * output's red, a secondary output's yellow, and a machine with only one generic output
     * (Furnace/Assembler) uses orange, exactly like their own side badges do.
     */
    protected static final int HIGHLIGHT_INPUT = 0xFF0A76CF;
    protected static final int HIGHLIGHT_OUTPUT_PRIMARY = 0xFFD22C15;
    protected static final int HIGHLIGHT_OUTPUT_SECONDARY = 0xFFD2AA15;
    protected static final int HIGHLIGHT_OUTPUT = 0xFFD26C16;
    /** Fully transparent - draws no ring at all, same as the plain 2-arg drawTESlot. Pass this (rather than branching between the two overloads) when the Reconfigurable Sides augment isn't installed, so slot highlighting turns off along with the rest of the side-config feature. */
    protected static final int HIGHLIGHT_NONE = 0x00000000;

    protected TabbedMachineGui(Container container) {
        super(container);
    }

    /**
     * The main machine panel, styled after real Thermal Expansion's own GUI backgrounds: a flat
     * light-gray fill with a raised 3D bevel border - black outline, a white highlight on the
     * top/left (as if lit from the upper-left, same convention vanilla's own GUIs use) and a
     * gray shadow on the bottom/right - instead of this mod's old single-flat-color frame. The
     * four corners are solid black blocks (matching how TE's own corner pixels read at normal
     * GUI scale) rather than a sharp 90-degree miter.
     */
    protected void drawTEPanel(int left, int top, int w, int h) {
        drawRect(left, top, left + w, top + h, TE_PANEL);

        // top/left highlight (2px), inset 3px short of the far edge so it doesn't collide with
        // the opposite edge's shadow band
        drawRect(left + 3, top + 1, left + w - 3, top + 3, TE_HIGHLIGHT);
        drawRect(left + 1, top + 3, left + 3, top + h - 3, TE_HIGHLIGHT);
        // bottom/right shadow (2px)
        drawRect(left + 3, top + h - 3, left + w - 3, top + h - 1, TE_SHADOW);
        drawRect(left + w - 3, top + 3, left + w - 1, top + h - 3, TE_SHADOW);

        // 1px black outline
        drawRect(left + 3, top, left + w - 3, top + 1, TE_BLACK);
        drawRect(left + 3, top + h - 1, left + w - 3, top + h, TE_BLACK);
        drawRect(left, top + 3, left + 1, top + h - 3, TE_BLACK);
        drawRect(left + w - 1, top + 3, left + w, top + h - 3, TE_BLACK);

        // solid corners
        drawRect(left, top, left + 3, top + 3, TE_BLACK);
        drawRect(left + w - 3, top, left + w, top + 3, TE_BLACK);
        drawRect(left, top + h - 3, left + 3, top + h, TE_BLACK);
        drawRect(left + w - 3, top + h - 3, left + w, top + h, TE_BLACK);
    }

    /**
     * A single 18x18 inventory slot, sunken (dark top/left edge, light bottom/right edge) the
     * opposite way round from {@link #drawTEPanel} - same convention real TE/vanilla slots use
     * to read as "recessed into" the panel rather than "sitting on top of" it.
     */
    protected void drawTESlot(int x, int y) {
        drawRect(x, y, x + 18, y + 18, TE_SLOT_DARK);
        drawRect(x + 1, y + 1, x + 17, y + 17, TE_PANEL_DARK);
        drawRect(x + 17, y + 1, x + 18, y + 18, TE_HIGHLIGHT);
        drawRect(x + 1, y + 17, x + 18, y + 18, TE_HIGHLIGHT);
    }

    /**
     * Same slot, plus a 1px colored ring wrapped directly around it marking its role (input vs.
     * which kind of output) - see the HIGHLIGHT_* constants above.
     */
    protected void drawTESlot(int x, int y, int roleColor) {
        drawTESlot(x, y);
        drawRect(x - 1, y - 1, x + 19, y, roleColor);
        drawRect(x - 1, y + 18, x + 19, y + 19, roleColor);
        drawRect(x - 1, y - 1, x, y + 19, roleColor);
        drawRect(x + 18, y - 1, x + 19, y + 19, roleColor);
    }

    /**
     * Same sunken bevel as {@link #drawTESlot}, but for an arbitrary-size socket (this mod's
     * energy bars and processing-progress bars), which real Thermal Expansion always renders as
     * a recessed slot-styled well rather than a plain single-color frame.
     */
    protected void drawTESocket(int x, int y, int w, int h) {
        drawRect(x - 1, y - 1, x + w + 1, y + h + 1, TE_SLOT_DARK);
        drawRect(x, y, x + w, y + h, TE_PANEL_DARK);
        drawRect(x + w, y - 1, x + w + 1, y + h + 1, TE_HIGHLIGHT);
        drawRect(x - 1, y + h, x + w + 1, y + h + 1, TE_HIGHLIGHT);
    }

    public FontRenderer getTabFontRenderer() {
        return fontRendererObj;
    }

    public float getTabZLevel() {
        return zLevel;
    }
}
