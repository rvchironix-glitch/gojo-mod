package com.jjs.gojo;

/**
 * Maps the four "move slot" abilities (Move 1-4 keys) to the real ability for the player's character.
 * Clients only ever send slot ids; the server decides what a slot means. Adding a character = adding a case.
 */
public final class Kits {
    private Kits() {}

    /**
     * @param hero         selected character (see {@link Heroes}), never 0 here
     * @param transformed  true while Gojo is Awakened / Yuji is Sukuna
     * @return the ability to run, or null if the request is not valid for that character
     */
    public static Ability resolve(int hero, boolean transformed, Ability requested) {
        switch (requested) {
            case DASH:
            case RCT:
            case ULTIMATE:
                return requested;
            case R_SPECIAL:
                // R means a different move per character: Gojo = Hollow Purple, Yuji = Cursed Energy,
                // Sukuna (Yuji transformed) = World Cutting Slash. Never crosses characters.
                if (hero == Heroes.GOJO) return Ability.R_SPECIAL;
                if (hero == Heroes.YUJI) return transformed ? Ability.WORLD_CUTTING_SLASH : Ability.CURSED_ENERGY;
                return null;
            case LAPIS_BLUE:
                return slot(hero, transformed, Ability.LAPIS_BLUE, Ability.MAXIMUM_BLUE, Ability.DIVERGENT_FIST, Ability.CLEAVE);
            case REVERSAL_RED:
                return slot(hero, transformed, Ability.REVERSAL_RED, Ability.MAXIMUM_RED, Ability.BLACK_FLASH, Ability.DISMANTLE);
            case RAPID_PUNCHES:
                return slot(hero, transformed, Ability.RAPID_PUNCHES, Ability.HOLLOW_NUKE, Ability.CRUSHING_BLOW, Ability.FLAME_ARROW);
            case TWOFOLD_KICK:
                return slot(hero, transformed, Ability.TWOFOLD_KICK, Ability.DOMAIN, Ability.RAPID_STRIKES, Ability.MALEVOLENT_SHRINE);
            default:
                return null; // character-specific ids are never accepted directly from a client
        }
    }

    private static Ability slot(int hero, boolean transformed, Ability gojo, Ability gojoUltimate,
                                Ability yuji, Ability sukuna) {
        if (hero == Heroes.GOJO) return transformed ? gojoUltimate : gojo;
        if (hero == Heroes.YUJI) return transformed ? sukuna : yuji;
        return null;
    }
}
