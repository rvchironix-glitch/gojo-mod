package com.jjs.gojo.client;

import com.jjs.gojo.Ability;
import com.jjs.gojo.Ultimate;

/** Client-side mirror of server state (character type, cooldowns, ultimate). Display only; the server decides. */
public final class ClientState {
    public static boolean synced;
    public static int type;
    public static int hero = 1;
    public static float meter;
    /** Smoothly animated copy of {@link #meter} for rendering. */
    public static float shownMeter;
    public static int tick;
    private static int awakenedEnd;
    private static final int N = Ability.values().length;
    private static final int[] cdEnd = new int[N];
    private static final int[] cdTotal = new int[N];

    private ClientState() {}

    public static void reset() {
        synced = false;
        type = 0;
        hero = 1;
        meter = 0f;
        shownMeter = 0f;
        awakenedEnd = 0;
        for (int i = 0; i < N; i++) {
            cdEnd[i] = 0;
            cdTotal[i] = 1;
        }
    }

    public static void setUltimate(int hero, float meter, int awakenedRemainingTicks) {
        ClientState.hero = hero;
        ClientState.meter = meter;
        awakenedEnd = awakenedRemainingTicks > 0 ? tick + awakenedRemainingTicks : 0;
    }

    public static int awakenedRemaining() {
        return Math.max(0, awakenedEnd - tick);
    }

    public static boolean awakened() {
        return awakenedRemaining() > 0;
    }

    public static int awakenedTotal() {
        return Ultimate.AWAKENED_TICKS;
    }

    /** Called once per client tick. */
    public static void animate() {
        shownMeter += (meter - shownMeter) * 0.2f;
        if (Math.abs(meter - shownMeter) < 0.05f) shownMeter = meter;
    }

    public static void setCooldown(int id, int remaining, int total) {
        if (id < 0 || id >= N) return;
        cdEnd[id] = tick + remaining;
        cdTotal[id] = Math.max(1, total);
    }

    public static int remaining(int id) {
        return Math.max(0, cdEnd[id] - tick);
    }

    public static int total(int id) {
        return Math.max(1, cdTotal[id]);
    }
}
