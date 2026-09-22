package net.thermaladd.mod.client.gui;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;

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

    /**
     * Real Thermal Expansion's own RF gauge art, verified via the decompiled
     * {@code cofh.lib.gui.element.ElementEnergyStored}: {@code cofh:textures/gui/elements/
     * Energy.png} is a 32x64 sheet whose left half (0,0)-(16,42) is the always-drawn empty-bar
     * frame/casing, and whose right half (16,0)-(32,42) is the filled-bar art, revealed only for
     * the bottom {@code filled} pixels of it (so the fill rises from the bottom exactly like
     * every other TE gauge). {@code drawTexturedModalRect} assumes a 256x256 sheet unconditionally,
     * so - same as {@link GuiSideTab#drawIcon16} and this mod's own fluid tank frame - this blits
     * both pieces by hand with explicit UV fractions instead. 16x42 is real TE's own fixed size
     * for this element (it's never resized), so every caller draws it at that size.
     */
    protected static final ResourceLocation ENERGY_TEXTURE = new ResourceLocation("cofh", "textures/gui/elements/Energy.png");
    protected static final int ENERGY_BAR_WIDTH = 16;
    protected static final int ENERGY_BAR_HEIGHT = 42;
    private static final int ENERGY_TEX_W = 32;
    private static final int ENERGY_TEX_H = 64;

    protected void drawEnergyStored(int x, int y, int energy, int maxEnergy) {
        int filled = maxEnergy <= 0 ? 0 : (int) ((long) energy * ENERGY_BAR_HEIGHT / maxEnergy);
        if (filled > ENERGY_BAR_HEIGHT) {
            filled = ENERGY_BAR_HEIGHT;
        }
        drawEnergyStoredFilled(x, y, filled);
    }

    /**
     * Same art as {@link #drawEnergyStored(int, int, int, int)}, but takes an already-computed
     * fill height directly - for callers whose own stored/capacity values don't fit an int (the
     * Singularity Energy Cell's capacity runs well past Integer.MAX_VALUE, so it works out its
     * own fill fraction from a pair of longs first).
     */
    protected void drawEnergyStoredFilled(int x, int y, int filled) {
        drawEnergyStoredFilled(x, y, filled, 1);
    }

    /**
     * Same as above, but rendered at {@code scale}x real TE's own 16x42 size (e.g. the
     * Singularity Energy Cell draws this at 2x, per the mod's own "beyond spec" cell needing a
     * bigger, more prominent gauge than a stock TE machine's). {@code filled} stays in the
     * texture's own 0-42 pixel space regardless of scale - only the on-screen quad size and the
     * filled portion's on-screen height are multiplied, the UV mapping into Energy.png itself is
     * untouched.
     */
    protected void drawEnergyStoredFilled(int x, int y, int filled, int scale) {
        if (filled > ENERGY_BAR_HEIGHT) {
            filled = ENERGY_BAR_HEIGHT;
        }
        int screenW = ENERGY_BAR_WIDTH * scale;
        int screenH = ENERGY_BAR_HEIGHT * scale;
        mc.getTextureManager().bindTexture(ENERGY_TEXTURE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        drawEnergyQuad(x, y, 0, 0, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, screenW, screenH);
        if (filled > 0) {
            int filledScreen = filled * scale;
            drawEnergyQuad(x, y + screenH - filledScreen, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT - filled, ENERGY_BAR_WIDTH, filled, screenW, filledScreen);
        }
    }

    /**
     * Real Thermal Expansion's own processing-progress arrow, verified against the decompiled
     * {@code cofh.lib.gui.element.ElementDualScaled} (as used by {@code GuiPulverizer} at 24x16
     * with {@code setMode(1)}, i.e. filling left to right): {@code cofh:textures/gui/elements/
     * Progress_Arrow_Right.png} is a 64x16 sheet holding two 24x16 frames side by side - the
     * empty arrow at u=0, which is always drawn, and the filled arrow at u=24, of which only the
     * leftmost {@code filled} pixels are revealed.
     *
     * This replaces the flat colored rectangle this mod drew before. {@code drawTexturedModalRect}
     * hardcodes a 256x256 sheet, so - exactly like {@link #drawEnergyStoredFilled} and
     * {@link GuiSideTab#drawIcon16} - both frames are blitted by hand with explicit UV fractions.
     * The empty frame already includes its own recessed casing, so callers must NOT draw a
     * {@link #drawTESocket} behind it the way the old rectangle needed.
     */
    protected static final ResourceLocation PROGRESS_ARROW_TEXTURE =
            new ResourceLocation("cofh", "textures/gui/elements/Progress_Arrow_Right.png");
    protected static final int PROGRESS_ARROW_WIDTH = 24;
    protected static final int PROGRESS_ARROW_HEIGHT = 16;
    private static final int PROGRESS_ARROW_TEX_W = 64;
    private static final int PROGRESS_ARROW_TEX_H = 16;

    protected void drawProgressArrow(int x, int y, int progress, int maxProgress) {
        int filled = maxProgress <= 0 ? 0 : (int) ((long) progress * PROGRESS_ARROW_WIDTH / maxProgress);
        if (filled > PROGRESS_ARROW_WIDTH) {
            filled = PROGRESS_ARROW_WIDTH;
        }

        mc.getTextureManager().bindTexture(PROGRESS_ARROW_TEXTURE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        // The arrow art is not a solid rectangle - the pixels around it are transparent, so the
        // panel behind has to show through.
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawProgressQuad(x, y, 0, PROGRESS_ARROW_WIDTH);
        if (filled > 0) {
            drawProgressQuad(x, y, PROGRESS_ARROW_WIDTH, filled);
        }
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawProgressQuad(int x, int y, int u, int width) {
        float u1 = u / (float) PROGRESS_ARROW_TEX_W;
        float u2 = (u + width) / (float) PROGRESS_ARROW_TEX_W;
        float v2 = PROGRESS_ARROW_HEIGHT / (float) PROGRESS_ARROW_TEX_H;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + PROGRESS_ARROW_HEIGHT, zLevel, u1, v2);
        t.addVertexWithUV(x + width, y + PROGRESS_ARROW_HEIGHT, zLevel, u2, v2);
        t.addVertexWithUV(x + width, y, zLevel, u2, 0);
        t.addVertexWithUV(x, y, zLevel, u1, 0);
        t.draw();
    }

    private void drawEnergyQuad(int x, int y, int u, int v, int uvW, int uvH, int screenW, int screenH) {
        float u1 = u / (float) ENERGY_TEX_W;
        float u2 = (u + uvW) / (float) ENERGY_TEX_W;
        float v1 = v / (float) ENERGY_TEX_H;
        float v2 = (v + uvH) / (float) ENERGY_TEX_H;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + screenH, zLevel, u1, v2);
        t.addVertexWithUV(x + screenW, y + screenH, zLevel, u2, v2);
        t.addVertexWithUV(x + screenW, y, zLevel, u2, v1);
        t.addVertexWithUV(x, y, zLevel, u1, v1);
        t.draw();
    }

    public FontRenderer getTabFontRenderer() {
        return fontRendererObj;
    }

    public float getTabZLevel() {
        return zLevel;
    }
}
