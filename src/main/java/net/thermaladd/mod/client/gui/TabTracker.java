package net.thermaladd.mod.client.gui;

/**
 * Remembers which tab the player had open, so opening the next machine shows the same one already
 * expanded instead of collapsed. Straight equivalent of real Thermal Expansion's own
 * {@code cofh.lib.gui.TabTracker}, which is why a TE machine "keeps" your Configuration tab open
 * as you walk down a row of them.
 *
 * Tracked by tab CLASS rather than by instance: every GUI builds its own tab objects, and the
 * point is to carry the choice across machines (and across machine types - the Pulverizer's
 * Configuration tab and the Furnace's are different classes, so each machine type remembers its
 * own, exactly as in TE where the tab classes differ per block too).
 *
 * One slot per side, because only one tab per side can be open at a time. Client-only state; it
 * deliberately does not persist across a game restart, same as TE.
 */
public final class TabTracker {

    private static Class<?> openLeft;
    private static Class<?> openRight;

    private TabTracker() {
    }

    /**
     * Called after the player toggles a tab, with EVERY tab the GUI has. Records whichever tab is
     * actually open on each side afterwards - or none.
     *
     * Recording only the clicked tab was wrong: clicking one tab also closes the others, on both
     * sides, and those side-effect closes went unrecorded. Open Energy, click Configuration, and
     * the next machine came up with both expanded - a state no sequence of clicks can produce.
     */
    public static void record(GuiSideTab... tabs) {
        Class<?> left = null;
        Class<?> right = null;
        for (int i = 0; i < tabs.length; i++) {
            GuiSideTab tab = tabs[i];
            if (!tab.open) {
                continue;
            }
            if (tab.isLeftSide()) {
                left = tab.getClass();
            } else {
                right = tab.getClass();
            }
        }
        openLeft = left;
        openRight = right;
    }

    /**
     * Opens {@code tab} immediately if it is the one remembered for its side. Callers pass every
     * tab they build; at most one per side can match.
     */
    public static void restore(GuiSideTab tab) {
        Class<?> remembered = tab.isLeftSide() ? openLeft : openRight;
        if (remembered == tab.getClass()) {
            tab.setFullyOpenImmediately();
        }
    }
}
