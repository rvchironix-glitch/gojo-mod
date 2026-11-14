package com.jjs.gojo;

/** Per-player server-side state. All times are server ticks. */
public class PlayerState {
    private final long[] cooldownEnd = new long[Ability.values().length];

    public boolean blocking;
    public int guard = GojoConfig.get().maxGuard;
    public long guardBrokenUntil;
    public long fallImmuneUntil;
    /** Server tick at which Limitless Awakened ends; 0 when not transformed. */
    public long awakenedUntil;

    public boolean isReady(Ability a, long now) {
        return now >= cooldownEnd[a.ordinal()];
    }

    public int remaining(Ability a, long now) {
        return (int) Math.max(0, cooldownEnd[a.ordinal()] - now);
    }

    public void startCooldown(Ability a, long now, int ticks) {
        cooldownEnd[a.ordinal()] = now + ticks;
    }

    public boolean isGuardBroken(long now) {
        return now < guardBrokenUntil;
    }
}
