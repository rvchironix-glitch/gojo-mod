package com.jjs.gojo.client;

import com.jjs.gojo.Ability;

/** Client-side mirror of server state (character type, cooldowns, ultimate). Display only; the server decides. */
public final class ClientState {
    public static boolean synced;
    public static int type;
    /** 0 = not chosen yet (or not synced). */
    public static int hero;
    public static boolean heroSynced;
    public static float meter;
    /** Smoothly animated copy of {@link #meter} for rendering. */
    public static float shownMeter;
    public static int tick;
    private static int awakenedEnd;
    private static int awakenedTotal = 2400;
    private static int exhaustEnd;
    private static int exhaustTotal = 400;
    /** Six Eyes passive (Gojo only) as told by the server. */
    public static boolean sixEyes;
    public static int sixEyesRange = 32;
    private static final int N = Ability.values().length;
    private static final int[] cdEnd = new int[N];
    private static final int[] cdTotal = new int[N];

    private ClientState() {}

    public static void reset() {
        synced = false;
        type = 0;
        hero = 0;
        heroSynced = false;
        meter = 0f;
        shownMeter = 0f;
        awakenedEnd = 0;
        exhaustEnd = 0;
        sixEyes = false;
        for (int i = 0; i < N; i++) {
            cdEnd[i] = 0;
            cdTotal[i] = 1;
        }
    }

    public static void setUltimate(int hero, float meter, int awakenedRemainingTicks, int totalTicks) {
        ClientState.hero = hero;
        ClientState.heroSynced = true;
        awakenedTotal = Math.max(1, totalTicks);
        ClientState.meter = meter;
        awakenedEnd = awakenedRemainingTicks > 0 ? tick + awakenedRemainingTicks : 0;
    }

    public static void setExhaust(int remaining, int total) {
        exhaustTotal = Math.max(1, total);
        exhaustEnd = remaining > 0 ? tick + remaining : 0;
    }

    public static int exhaustedRemaining() {
        return Math.max(0, exhaustEnd - tick);
    }

    /** Display only: the server enforces exhaustion on every ability and on blocking. */
    public static boolean exhausted() {
        return exhaustedRemaining() > 0;
    }

    public static int exhaustedTotal() {
        return exhaustTotal;
    }

    public static boolean gojoAwakened() {
        return hero == com.jjs.gojo.Heroes.GOJO && awakened();
    }

    public static int awakenedRemaining() {
        return Math.max(0, awakenedEnd - tick);
    }

    public static boolean awakened() {
        return awakenedRemaining() > 0;
    }

    public static int awakenedTotal() {
        return awakenedTotal;
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
