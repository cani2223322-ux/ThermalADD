package net.thermaladd.mod.util;

/**
 * Carries a machine's per-side configuration along when its facing changes, so the configuration
 * stays attached to the MACHINE rather than to fixed world directions.
 *
 * Real Thermal Expansion does exactly this: TileReconfigurable#rotateBlock remaps sideCache
 * through BlockHelper.ROTATE_CLOCK_Y in the same step that turns the facing. Before this helper
 * existed, a wrench rotation here only changed the facing, so a face configured as Output could
 * end up being the new FRONT: the block rendered the machine's face texture over it, the GUI
 * refused to touch it (the front is never configurable), yet pipes kept pulling items out of it.
 * The same thing happened when a dismantled machine was placed facing another way, and when a
 * Redprint was pasted onto a machine facing a different direction than the one it was copied
 * from.
 *
 * Only the four horizontal sides move; the machines in this mod never face up or down.
 */
public final class SideRotation {

    /** Minecraft side indices of the four horizontal faces, clockwise seen from above. */
    private static final int[] CLOCKWISE = {2, 5, 3, 4};

    private SideRotation() {
    }

    /**
     * Returns a copy of {@code sides} rotated from a machine facing {@code fromFacing} to one
     * facing {@code toFacing}: whatever was on the old front ends up on the new front, whatever
     * was on the old left ends up on the new left, and so on. An unknown facing on either side
     * (e.g. -1 for data saved before facings were recorded) returns an unrotated copy.
     */
    public static byte[] rotate(byte[] sides, int fromFacing, int toFacing) {
        byte[] out = sides.clone();
        int from = indexOf(fromFacing);
        int to = indexOf(toFacing);
        if (from < 0 || to < 0 || from == to) {
            return out;
        }
        int steps = (to - from + 4) % 4;
        for (int i = 0; i < 4; i++) {
            out[CLOCKWISE[(i + steps) % 4]] = sides[CLOCKWISE[i]];
        }
        return out;
    }

    private static int indexOf(int side) {
        for (int i = 0; i < CLOCKWISE.length; i++) {
            if (CLOCKWISE[i] == side) {
                return i;
            }
        }
        return -1;
    }
}
