package net.thermaladd.mod.util;

/** Shared RF arithmetic for the processing lines. */
public final class EnergyMath {

    private EnergyMath() {
    }

    /**
     * What one tick of work costs when only {@code remaining} progress is left and a full tick
     * would add {@code step}: the full {@code cost} normally, a proportional share (rounded up) on
     * the last tick. Real TE refunds that overshoot too (TileMachineBase#processTick); without it a
     * 3,200 RF Charger recipe cost a whole 16,000 RF tick - 960,000 RF at Machine Speed IV.
     */
    public static int tickCost(int cost, int step, int remaining) {
        if (step <= 0 || remaining >= step) {
            return cost;
        }
        if (remaining <= 0) {
            return 0;
        }
        return (int) (((long) cost * remaining + step - 1) / step);
    }
}
