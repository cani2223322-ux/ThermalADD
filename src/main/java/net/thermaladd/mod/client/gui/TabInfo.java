package net.thermaladd.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

/**
 * "Information" tab - real Thermal Expansion's own cofh.core.gui.element.TabInfo: docked on the
 * LEFT under the Energy tab, grey background (0x555555), gold header, white text, scrolled with
 * the mouse wheel or the arrow buttons when the text is longer than the flap. Colours and icons
 * are TE's own decompiled defaults and textures.
 *
 * TE splits this across two tabs (Information for what the machine is, Tutorial for how to use
 * its features). With only a 100px flap, one tab that starts with the description and continues
 * into the tips reads better here, and keeps the left side down to two tabs.
 *
 * Paragraphs are lang keys; they are wrapped to the flap width at construction, with a blank line
 * between paragraphs.
 */
public class TabInfo extends GuiSideTab {

    private static final int TINT = 0x555555;
    private static final int HEADER = 0xE1C92F;
    private static final int TEXT = 0xFFFFFF;

    private static final ResourceLocation ICON = new ResourceLocation("cofh", "textures/items/icons/Icon_Information.png");
    private static final ResourceLocation ARROW_UP = new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowUp.png");
    private static final ResourceLocation ARROW_UP_OFF = new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowUp_Inactive.png");
    private static final ResourceLocation ARROW_DOWN = new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowDown.png");
    private static final ResourceLocation ARROW_DOWN_OFF = new ResourceLocation("cofh", "textures/items/icons/Icon_ArrowDown_Inactive.png");

    private static final int TEXT_X = 6;
    private static final int TEXT_Y = 20;
    private static final int TEXT_WIDTH = 74;
    private static final int LINE_HEIGHT = 10;
    private static final int VISIBLE_LINES = 7;
    /** The arrow column on the flap's right edge, same place TE's TabScrolledText puts them. */
    private static final int ARROW_X = 82;
    private static final int ARROW_UP_Y = 18;
    private static final int ARROW_DOWN_Y = 74;

    private final TabbedMachineGui gui;
    private final String[] paragraphKeys;
    private List<String> lines;
    private int firstLine = 0;

    public TabInfo(TabbedMachineGui gui, String... paragraphKeys) {
        super(gui, ICON, TINT, HEADER, StatCollector.translateToLocal("gui.thermaladd.tab.info"), true);
        this.gui = gui;
        this.paragraphKeys = paragraphKeys;
    }

    /**
     * Wrapped on first use rather than in the constructor: the tabs are built in the GUI's own
     * constructor, and GuiScreen only gets its font renderer later, in setWorldAndResolution.
     */
    private List<String> lines() {
        if (lines == null) {
            lines = new ArrayList<String>();
            for (int i = 0; i < paragraphKeys.length; i++) {
                if (i > 0) {
                    lines.add("");
                }
                @SuppressWarnings("unchecked")
                List<String> wrapped = gui.getTabFontRenderer().listFormattedStringToWidth(
                        StatCollector.translateToLocal(paragraphKeys[i]), TEXT_WIDTH);
                lines.addAll(wrapped);
            }
        }
        return lines;
    }

    private int maxFirstLine() {
        return Math.max(0, lines().size() - VISIBLE_LINES);
    }

    /** Positive = scroll down. */
    public void scroll(int amount) {
        firstLine = Math.max(0, Math.min(maxFirstLine(), firstLine + amount));
    }

    @Override
    protected void drawContentForeground(int x, int y) {
        List<String> text = lines();
        int end = Math.min(text.size(), firstLine + VISIBLE_LINES);
        for (int i = firstLine; i < end; i++) {
            gui.getTabFontRenderer().drawStringWithShadow(text.get(i), x + TEXT_X, y + TEXT_Y + (i - firstLine) * LINE_HEIGHT, TEXT);
        }
        if (maxFirstLine() > 0) {
            drawIcon16(firstLine > 0 ? ARROW_UP : ARROW_UP_OFF, x + ARROW_X, y + ARROW_UP_Y);
            drawIcon16(firstLine < maxFirstLine() ? ARROW_DOWN : ARROW_DOWN_OFF, x + ARROW_X, y + ARROW_DOWN_Y);
        }
    }

    @Override
    public boolean onContentClick(int relX, int relY, int mouseButton, boolean shift) {
        if (maxFirstLine() == 0 || relX < ARROW_X || relX >= ARROW_X + 16) {
            return false;
        }
        if (relY >= ARROW_UP_Y && relY < ARROW_UP_Y + 16 && firstLine > 0) {
            scroll(-1);
            playClick(TabbedMachineGui.PITCH_CYCLE_FORWARD);
            return true;
        }
        if (relY >= ARROW_DOWN_Y && relY < ARROW_DOWN_Y + 16 && firstLine < maxFirstLine()) {
            scroll(1);
            playClick(TabbedMachineGui.PITCH_CYCLE_BACKWARD);
            return true;
        }
        return false;
    }
}
