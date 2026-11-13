package com.jjs.gojo;

import net.minecraft.util.Identifier;

public final class GojoNet {
    private GojoNet() {}

    /** C2S: byte abilityId, byte dashDirection (0 fwd, 1 back, 2 left, 3 right) */
    public static final Identifier ABILITY = new Identifier(GojoMod.MOD_ID, "ability");
    /** C2S: boolean blocking */
    public static final Identifier BLOCK = new Identifier(GojoMod.MOD_ID, "block");
}
