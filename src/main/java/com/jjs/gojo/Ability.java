package com.jjs.gojo;

public enum Ability {
    DASH(0),
    LAPIS_BLUE(1),
    REVERSAL_RED(2),
    RAPID_PUNCHES(3),
    TWOFOLD_KICK(4),
    R_SPECIAL(5),
    RCT(6),
    ULTIMATE(7),
    // Yuji
    DIVERGENT_FIST(8),
    BLACK_FLASH(9),
    CRUSHING_BLOW(10),
    RAPID_STRIKES(11),
    // Sukuna (Yuji's transformation)
    CLEAVE(12),
    DISMANTLE(13),
    FLAME_ARROW(14),
    MALEVOLENT_PRESSURE(15);

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
            case RCT -> c.rctCooldownTicks;
            case ULTIMATE -> 0;
            case DIVERGENT_FIST -> c.divergentCooldownTicks;
            case BLACK_FLASH -> c.blackFlashCooldownTicks;
            case CRUSHING_BLOW -> c.crushingCooldownTicks;
            case RAPID_STRIKES -> c.strikesCooldownTicks;
            case CLEAVE -> c.cleaveCooldownTicks;
            case DISMANTLE -> c.dismantleCooldownTicks;
            case FLAME_ARROW -> c.flameArrowCooldownTicks;
            case MALEVOLENT_PRESSURE -> c.pressureCooldownTicks;
        };
    }
}
