package com.jjs.gojo;

public enum Ability {
    DASH(0),
    LAPIS_BLUE(1),
    REVERSAL_RED(2),
    RAPID_PUNCHES(3),
    TWOFOLD_KICK(4),
    R_SPECIAL(5);

    public final int id;

    Ability(int id) {
        this.id = id;
    }

    public static Ability fromId(int id) {
        for (Ability a : values()) {
            if (a.id == id) return a;
        }
        return null;
    }

    public int cooldownTicks() {
        GojoConfig c = GojoConfig.get();
        return switch (this) {
            case DASH -> c.dashCooldownTicks;
            case LAPIS_BLUE -> c.blueCooldownTicks;
            case REVERSAL_RED -> c.redCooldownTicks;
            case RAPID_PUNCHES -> c.punchesCooldownTicks;
            case TWOFOLD_KICK -> c.kickCooldownTicks;
            case R_SPECIAL -> c.specialCooldownTicks;
        };
    }
}
