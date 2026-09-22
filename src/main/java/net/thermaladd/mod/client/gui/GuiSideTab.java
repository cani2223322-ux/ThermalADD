package net.thermaladd.mod.client.gui;

import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

/**
 * The same tab widget every real Thermal Expansion augmentable-machine GUI uses
 * (cofh.lib.gui.element.TabBase / cofh.core.gui.element.TabAugment / TabConfiguration): a
 * 22x22 icon button stacked against an edge of the main box that expands into a 100x92
 * colored flap when clicked, one tab open at a time. Since CoFHCore is a hard dependency of
 * this addon, the real {@code cofh:textures/gui/elements/Tab_Right.png}/{@code Tab_Left.png}
 * (256x256, corner-sliced) are used directly - no bundled copy needed.
 *
 * Right-side tabs (the default, and every tab in this mod except {@link TabEnergy}) anchor
 * their box's LEFT edge at a fixed {@code tabX} and grow the box rightward, away from the
 * panel, as they open - the icon (drawn near that fixed edge) never moves. A left-side tab
 * mirrors this: {@code tabX} instead anchors the box's RIGHT edge (flush against the panel's
 * own left edge) and the box grows further left as it opens, so the icon - fixed at
 * {@code tabX - MIN_SIZE}, independent of the animating width - stays put right where it
 * meets the panel, exactly like a right tab's icon does.
 */
public abstract class GuiSideTab {

    private static final ResourceLocation TAB_TEXTURE_RIGHT =
            new ResourceLocation("cofh", "textures/gui/elements/Tab_Right.png");
    private static final ResourceLocation TAB_TEXTURE_LEFT =
            new ResourceLocation("cofh", "textures/gui/elements/Tab_Left.png");

    static final int MIN_SIZE = 22;
    /** Package-visible: GuiAdvancedPulverizer needs this to size xSize to cover the tab flap. */
    static final int MAX_WIDTH = 100;
    static final int MAX_HEIGHT = 92;
    private static final int EXPAND_SPEED = 8;

    private final TabbedMachineGui gui;
    private final ResourceLocation icon;
    private final int tintColor;
    private final int headerColor;
    private final String title;
    private final boolean leftSide;

    private int tabX;
    private int tabY;
    public boolean open;
    private int currentWidth = MIN_SIZE;
    private int currentHeight = MIN_SIZE;

    protected GuiSideTab(TabbedMachineGui gui, ResourceLocation icon, int tintColor, int headerColor, String title) {
        this(gui, icon, tintColor, headerColor, title, false);
    }

    protected GuiSideTab(TabbedMachineGui gui, ResourceLocation icon, int tintColor, int headerColor, String title, boolean leftSide) {
        this.gui = gui;
        this.icon = icon;
        this.tintColor = tintColor;
        this.headerColor = headerColor;
        this.title = title;
        this.leftSide = leftSide;
    }

    /**
     * The raw stack anchor as passed to {@link #setStackPosition} - the box's own left edge
     * for a right tab, but for a left tab this is NOT where the box visually starts on
     * screen (see {@link #getContentX()} for that). Kept for the existing right-tab-only
     * callers (e.g. TabAugments moving real Slots) that already assume tabX == the box's
     * left edge.
     */
    public void setStackPosition(int x, int y) {
        this.tabX = x;
        this.tabY = y;
    }

    public int getTabX() {
        return tabX;
    }

    public int getTabY() {
        return tabY;
    }

    /** The box's actual on-screen left edge - identical to {@link #getTabX()} for a right tab, but shifted left by the current (animating) width for a left tab. Use this, not getTabX(), for anything meant to line up with what drawBackground/drawContentForeground actually draw. */
    public int getContentX() {
        return boxX();
    }

    private int boxX() {
        return leftSide ? tabX - currentWidth : tabX;
    }

    /** Where the always-visible 22x22 icon button sits - fixed at the edge that meets the panel, regardless of side or animation state. */
    private int iconX() {
        return leftSide ? tabX - MIN_SIZE : tabX;
    }

    public boolean isFullyOpen() {
        return open && currentWidth >= MAX_WIDTH && currentHeight >= MAX_HEIGHT;
    }

    public boolean isLeftSide() {
        return leftSide;
    }

    /**
     * Opens the tab with no animation - used when {@link TabTracker} restores whichever tab the
     * player had open in the previous machine, which should already be open the moment the GUI
     * appears rather than visibly sliding out.
     */
    public void setFullyOpenImmediately() {
        open = true;
        currentWidth = MAX_WIDTH;
        currentHeight = MAX_HEIGHT;
    }

    /**
     * Where the mouse is, relative to the GUI panel's own origin. Set once per frame by the GUI
     * before it draws the tabs, so tab content can highlight whichever button is under the
     * cursor - drawContentForeground has no mouse arguments of its own.
     */
    private int mousePanelX = Integer.MIN_VALUE;
    private int mousePanelY = Integer.MIN_VALUE;

    public void setMousePosition(int panelRelX, int panelRelY) {
        mousePanelX = panelRelX;
        mousePanelY = panelRelY;
    }

    /** Real TE's own click feedback - see TabbedMachineGui#playClick for the pitch convention. */
    protected void playClick(float pitch) {
        gui.playClick(pitch);
    }

    /** {@code relX}/{@code relY} are in the same tab-relative space the BTN_X/BTN_Y tables use. */
    protected boolean isContentHovered(int relX, int relY, int width, int height) {
        if (!isFullyOpen() || mousePanelX == Integer.MIN_VALUE) {
            return false;
        }
        int tx = mousePanelX - boxX();
        int ty = mousePanelY - tabY;
        return tx >= relX && tx < relX + width && ty >= relY && ty < relY + height;
    }

    /**
     * A 1px white frame just outside a 16x16 button, drawn while the cursor is over it. Real
     * Thermal Expansion's own buttons only distinguish idle/active/disabled and never react to
     * hover at all - this is one place it is worth going past TE, since without it there is
     * nothing telling the player these glyphs are clickable.
     */
    protected void drawHoverFrame(int x, int y) {
        final int color = 0x80FFFFFF;
        net.minecraft.client.gui.Gui.drawRect(x - 1, y - 1, x + 17, y, color);
        net.minecraft.client.gui.Gui.drawRect(x - 1, y + 16, x + 17, y + 17, color);
        net.minecraft.client.gui.Gui.drawRect(x - 1, y, x, y + 16, color);
        net.minecraft.client.gui.Gui.drawRect(x + 16, y, x + 17, y + 16, color);
    }

    public void update() {
        if (open && currentWidth < MAX_WIDTH) {
            currentWidth = Math.min(MAX_WIDTH, currentWidth + EXPAND_SPEED);
        } else if (!open && currentWidth > MIN_SIZE) {
            currentWidth = Math.max(MIN_SIZE, currentWidth - EXPAND_SPEED);
        }
        if (open && currentHeight < MAX_HEIGHT) {
            currentHeight = Math.min(MAX_HEIGHT, currentHeight + EXPAND_SPEED);
        } else if (!open && currentHeight > MIN_SIZE) {
            currentHeight = Math.max(MIN_SIZE, currentHeight - EXPAND_SPEED);
        }
        onUpdate(isFullyOpen());
    }

    public void setOpen(boolean value) {
        this.open = value;
    }

    /** True while the mouse is over the always-visible 22x22 icon button. */
    public boolean isMouseOverIcon(int mouseX, int mouseY, int left, int top) {
        int x = left + iconX();
        int y = top + tabY;
        return mouseX >= x && mouseX < x + MIN_SIZE && mouseY >= y && mouseY < y + MIN_SIZE;
    }

    public boolean isMouseOverFlap(int mouseX, int mouseY, int left, int top) {
        int x = left + boxX();
        int y = top + tabY;
        return mouseX >= x && mouseX < x + currentWidth && mouseY >= y && mouseY < y + currentHeight;
    }

    public void drawBackground(int left, int top) {
        int x = left + boxX();
        int y = top + tabY;

        gui.mc.getTextureManager().bindTexture(leftSide ? TAB_TEXTURE_LEFT : TAB_TEXTURE_RIGHT);
        float r = (tintColor >> 16 & 0xFF) / 255F;
        float g = (tintColor >> 8 & 0xFF) / 255F;
        float b = (tintColor & 0xFF) / 255F;
        GL11.glColor4f(r, g, b, 1F);

        gui.drawTexturedModalRect(x, y + 4, 0, 256 - currentHeight + 4, 4, currentHeight - 4);
        gui.drawTexturedModalRect(x + 4, y, 256 - currentWidth + 4, 0, currentWidth - 4, 4);
        gui.drawTexturedModalRect(x, y, 0, 0, 4, 4);
        gui.drawTexturedModalRect(x + 4, y + 4, 256 - currentWidth + 4, 256 - currentHeight + 4, currentWidth - 4, currentHeight - 4);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        if (isFullyOpen()) {
            drawContentBackground(x, y);
        }
    }

    public void drawForeground(int left, int top) {
        int iconScreenX = left + iconX();
        int y = top + tabY;
        drawIcon16(icon, iconScreenX + 3, y + 3);
        if (isFullyOpen()) {
            int x = left + boxX();
            gui.getTabFontRenderer().drawString(title, x + 18, y + 6, headerColor);
            drawContentForeground(x, y);
        }
    }

    public void addTooltip(int mouseX, int mouseY, int left, int top, List<String> tooltip) {
        if (!isFullyOpen()) {
            if (isMouseOverIcon(mouseX, mouseY, left, top)) {
                tooltip.add(title);
            }
            return;
        }
        addContentTooltip(mouseX - (left + boxX()), mouseY - (top + tabY), tooltip);
    }

    protected void addContentTooltip(int relX, int relY, List<String> tooltip) {
    }

    /** Draws a standalone (non-256-sheet) 16x16 icon texture without the vanilla /256 UV assumption. */
    protected void drawIcon16(ResourceLocation texture, int x, int y) {
        gui.mc.getTextureManager().bindTexture(texture);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        float zLevel = gui.getTabZLevel();
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + 16, zLevel, 0, 1);
        t.addVertexWithUV(x + 16, y + 16, zLevel, 1, 1);
        t.addVertexWithUV(x + 16, y, zLevel, 1, 0);
        t.addVertexWithUV(x, y, zLevel, 0, 0);
        t.draw();
    }

    /** Called every tick; {@code fullyOpen} mirrors {@link #isFullyOpen()} for subclasses (e.g. to move real Slots). */
    protected void onUpdate(boolean fullyOpen) {
    }

    protected void drawContentBackground(int x, int y) {
    }

    protected void drawContentForeground(int x, int y) {
    }

    /** mouseX/mouseY relative to the tab's own (x, y) origin. */
    public boolean onContentClick(int relX, int relY, int mouseButton, boolean shift) {
        return false;
    }
}
