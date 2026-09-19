package net.thermaladd.mod.client.gui;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

/**
 * Thin common base for every machine GUI in this mod that has CoFH-style side tabs
 * (Augments/Configuration). Exists purely so {@link GuiSideTab} can be shared between
 * GuiAdvancedPulverizer and GuiImprovedAssembler instead of each having its own copy: Java's
 * "protected" access to GuiScreen's inherited fields (mc/fontRendererObj/zLevel) and methods
 * (drawTexturedModalRect) is only visible to other classes in the SAME package through a
 * reference typed as a class declared in that package - a plain GuiContainer-typed reference
 * would not compile here, since GuiContainer itself lives in net.minecraft.client.gui.inventory.
 *
 * Also hosts the shared "Energy" info card (title + red divider + Consumption/Max Power/
 * Stored lines on a solid blue panel) every machine's energy bar shows on hover, matching
 * real Thermal Expansion's own energy tooltip instead of vanilla's plain dark tooltip box.
 */
public abstract class TabbedMachineGui extends GuiContainer {

    private static final int INFO_BG = 0xF0102048;
    private static final int INFO_DIVIDER = 0xFFAA2020;
    private static final int INFO_TITLE = 0xFFE1C92F;
    private static final int INFO_TEXT = 0xFFE0E0E0;

    private static final int PAD_X = 5;
    private static final int TITLE_Y = 4;
    private static final int DIVIDER_Y = 14;
    private static final int LINES_Y = 19;
    private static final int LINE_HEIGHT = 10;

    protected TabbedMachineGui(Container container) {
        super(container);
    }

    public FontRenderer getTabFontRenderer() {
        return fontRendererObj;
    }

    public float getTabZLevel() {
        return zLevel;
    }

    protected void drawEnergyInfoTooltip(int mouseX, int mouseY, String title, List<String> lines) {
        int boxWidth = fontRendererObj.getStringWidth(title);
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, fontRendererObj.getStringWidth(line));
        }
        boxWidth += PAD_X * 2;
        int boxHeight = LINES_Y + lines.size() * LINE_HEIGHT + 3;

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + boxWidth > width) {
            x = mouseX - boxWidth - 12;
        }
        if (y + boxHeight > height) {
            y = height - boxHeight - 4;
        }
        if (y < 4) {
            y = 4;
        }

        drawRect(x, y, x + boxWidth, y + boxHeight, INFO_BG);
        fontRendererObj.drawStringWithShadow(title, x + PAD_X, y + TITLE_Y, INFO_TITLE);
        drawRect(x + PAD_X - 1, y + DIVIDER_Y, x + boxWidth - PAD_X + 1, y + DIVIDER_Y + 1, INFO_DIVIDER);

        int lineY = y + LINES_Y;
        for (String line : lines) {
            fontRendererObj.drawStringWithShadow(line, x + PAD_X, lineY, INFO_TEXT);
            lineY += LINE_HEIGHT;
        }
    }
}
