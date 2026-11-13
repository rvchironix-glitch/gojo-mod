package com.jjs.gojo;

/** Per-player server-side state. All times are server ticks. */
public class PlayerState {
    private final long[] cooldownEnd = new long[Ability.values().length];

    public boolean blocking;
    public int guard = GojoConfig.get().maxGuard;
    public long guardBrokenUntil;
    public long fallImmuneUntil;

    public boolean isReady(Ability a, long now) {
        return now >= cooldownEnd[a.ordinal()];
    }

    public int remaining(Ability a, long now) {
        return (int) Math.max(0, cooldownEnd[a.ordinal()] - now);
    }

    public void startCooldown(Ability a, long now) {
        cooldownEnd[a.ordinal()] = now + a.cooldownTicks();
    }

    public boolean isGuardBroken(long now) {
        return now < guardBrokenUntil;
    }
}
