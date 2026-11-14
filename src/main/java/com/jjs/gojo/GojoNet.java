package com.jjs.gojo;

import net.minecraft.util.Identifier;

public final class GojoNet {
    private GojoNet() {}

    /** C2S: byte abilityId, byte dashDirection (0 fwd, 1 back, 2 left, 3 right) */
    public static final Identifier ABILITY = new Identifier(GojoMod.MOD_ID, "ability");
    /** C2S: boolean blocking */
    public static final Identifier BLOCK = new Identifier(GojoMod.MOD_ID, "block");
    /** C2S: byte character type (1 human, 2 sorcerer). Only accepted once per player per world. */
    public static final Identifier CHOOSE = new Identifier(GojoMod.MOD_ID, "choose");
    /** S2C: byte character type (0 none, 1 human, 2 sorcerer) */
    public static final Identifier SYNC = new Identifier(GojoMod.MOD_ID, "sync");
    /** C2S: byte hero (1 Gojo, 2 Yuji). Sorcerers only, never while transformed. */
    public static final Identifier HERO = new Identifier(GojoMod.MOD_ID, "hero");
    /** S2C: byte hero (0 = not chosen), float meter (0-100), int transformRemainingTicks, int transformTotalTicks */
    public static final Identifier ULT = new Identifier(GojoMod.MOD_ID, "ult");
    /** S2C: int remainingTicks, int totalTicks (0 = not exhausted) */
    public static final Identifier EXHAUST = new Identifier(GojoMod.MOD_ID, "exhaust");
    /** S2C: boolean sixEyesActive, int range */
    public static final Identifier PASSIVE = new Identifier(GojoMod.MOD_ID, "passive");
    /**
     * S2C status timers. byte kind, int a, int b.
     * kind 0 = Yuji Cursed Energy: a = ticks of effect left, b = ticks of cooldown left once the effect ends.
     * kind 1 = World Cutting Slash preparation: a = ticks left until release (0 = not preparing).
     */
    public static final Identifier STATUS = new Identifier(GojoMod.MOD_ID, "status");
    /** S2C: byte abilityId, int remainingTicks, int totalTicks */
    public static final Identifier COOLDOWN = new Identifier(GojoMod.MOD_ID, "cooldown");
}
