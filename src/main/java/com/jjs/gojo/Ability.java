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
    MALEVOLENT_PRESSURE(15),
    // Gojo's Limitless Awakened moves (replace his four normal moves while transformed)
    MAXIMUM_BLUE(16),
    MAXIMUM_RED(17),
    HOLLOW_NUKE(18),
    DOMAIN(19);

    public final int id;

    Ability(int id) {
        this.id = id;
    }

    /** True for the four moves that replace Gojo's normal moves during Limitless Awakened. */
    public boolean isGojoUltimateMove() {
        return this == MAXIMUM_BLUE || this == MAXIMUM_RED || this == HOLLOW_NUKE || this == DOMAIN;
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
            case MAXIMUM_BLUE -> c.maxBlueCooldownTicks;
            case MAXIMUM_RED -> c.maxRedCooldownTicks;
            case HOLLOW_NUKE -> c.hollowNukeCooldownTicks;
            case DOMAIN -> c.domainCooldownTicks;
        };
    }
}
