package com.jjs.gojo.client;

import com.jjs.gojo.Ability;

/** Client-side mirror of server state (character type and cooldowns). Display only; the server decides. */
public final class ClientState {
    public static boolean synced;
    public static int type;
    public static int tick;
    private static final int N = Ability.values().length;
    private static final int[] cdEnd = new int[N];
    private static final int[] cdTotal = new int[N];

    private ClientState() {}

    public static void reset() {
        synced = false;
        type = 0;
        for (int i = 0; i < N; i++) {
            cdEnd[i] = 0;
            cdTotal[i] = 1;
        }
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
