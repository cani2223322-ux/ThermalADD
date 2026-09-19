package net.thermaladd.mod.client.gui;

import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

/**
 * The same tab widget every real Thermal Expansion augmentable-machine GUI uses
 * (cofh.lib.gui.element.TabBase / cofh.core.gui.element.TabAugment / TabConfiguration): a
 * 22x22 icon button stacked against the right edge of the main box that expands into a
 * 100x92 colored flap when clicked, one tab open at a time. Since CoFHCore is a hard
 * dependency of this addon, the real {@code cofh:textures/gui/elements/Tab_Right.png}
 * (256x256, corner-sliced) is used directly - no bundled copy needed.
 */
public abstract class GuiSideTab {

    private static final ResourceLocation TAB_TEXTURE =
            new ResourceLocation("cofh", "textures/gui/elements/Tab_Right.png");

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

    private int tabX;
    private int tabY;
    public boolean open;
    private int currentWidth = MIN_SIZE;
    private int currentHeight = MIN_SIZE;

    protected GuiSideTab(TabbedMachineGui gui, ResourceLocation icon, int tintColor, int headerColor, String title) {
        this.gui = gui;
        this.icon = icon;
        this.tintColor = tintColor;
        this.headerColor = headerColor;
        this.title = title;
    }

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

    public boolean isFullyOpen() {
        return open && currentWidth >= MAX_WIDTH && currentHeight >= MAX_HEIGHT;
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
        int x = left + tabX;
        int y = top + tabY;
        return mouseX >= x && mouseX < x + MIN_SIZE && mouseY >= y && mouseY < y + MIN_SIZE;
    }

    public boolean isMouseOverFlap(int mouseX, int mouseY, int left, int top) {
        int x = left + tabX;
        int y = top + tabY;
        return mouseX >= x && mouseX < x + currentWidth && mouseY >= y && mouseY < y + currentHeight;
    }

    public void drawBackground(int left, int top) {
        int x = left + tabX;
        int y = top + tabY;

        gui.mc.getTextureManager().bindTexture(TAB_TEXTURE);
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
        int x = left + tabX;
        int y = top + tabY;
        drawIcon16(icon, x + 3, y + 3);
        if (isFullyOpen()) {
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
        addContentTooltip(mouseX - (left + tabX), mouseY - (top + tabY), tooltip);
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
