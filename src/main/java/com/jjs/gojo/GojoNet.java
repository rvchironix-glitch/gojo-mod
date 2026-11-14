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
    /** S2C: byte hero, float meter (0-100), int awakenedRemainingTicks */
    public static final Identifier ULT = new Identifier(GojoMod.MOD_ID, "ult");
    /** S2C: byte abilityId, int remainingTicks, int totalTicks */
    public static final Identifier COOLDOWN = new Identifier(GojoMod.MOD_ID, "cooldown");
}
