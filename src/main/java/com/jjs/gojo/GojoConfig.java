package com.jjs.gojo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-side config, saved to config/gojomoveset.json. Values are in ticks (20 = 1 second). */
public class GojoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GojoConfig instance = new GojoConfig();

    public int dashCooldownTicks = 40;
    public int blueCooldownTicks = 200;
    public int redCooldownTicks = 160;
    public int punchesCooldownTicks = 240;
    public int kickCooldownTicks = 140;
    public int specialCooldownTicks = 600;

    public double dashSpeed = 1.6;
    public int dashFallImmunityTicks = 30;

    // Lapis Blue
    public int blueDurationTicks = 70;
    public double blueRadius = 9.0;
    public double bluePullStrength = 0.55;
    public float blueDamage = 2.0f;
    public int blueDamageIntervalTicks = 10;

    // Reversal Red
    public double redSpeed = 1.6;
    public double redRange = 45.0;
    public double redExplosionRadius = 5.0;
    public float redDamage = 12.0f;
    public double redKnockback = 2.2;

    // Rapid Punches
    public int punchCount = 12;
    public int punchIntervalTicks = 2;
    public float punchDamage = 1.5f;
    public float punchFinisherDamage = 4.0f;
    public double punchReach = 3.2;

    // Twofold Kick
    public int kickGapTicks = 8;
    public float kickDamage1 = 4.0f;
    public float kickDamage2 = 7.0f;
    public double kickReach = 3.5;

    // R Special (placeholder: Hollow Purple)
    public int specialChargeTicks = 40;
    public float specialDamage = 20.0f;
    public double specialSpeed = 2.0;
    public double specialRange = 60.0;
    public double specialRadius = 2.5;
    public double specialKnockback = 2.5;

    // Reverse Cursed Technique
    public int rctCooldownTicks = 200;
    public float rctHealAmount = 8.0f;

    // Ultimate meter (percent points)
    public float chargePerDamageDealt = 1.0f;
    public float chargePerDamageTaken = 0.5f;
    public float chargePerKill = 10.0f;

    // Gojo: Limitless Awakened (duration is fixed at 120 s in code)
    public float awakenedDamageMult = 1.5f;
    public float awakenedCooldownMult = 0.5f;
    public double awakenedRangeMult = 1.4;

    // Yuji (normal form)
    public int divergentCooldownTicks = 160;
    public int divergentDelayTicks = 12;
    public float divergentDamage1 = 6.0f;
    public float divergentDamage2 = 9.0f;
    public int blackFlashCooldownTicks = 300;
    public float blackFlashDamage = 16.0f;
    public int crushingCooldownTicks = 200;
    public float crushingDamage = 10.0f;
    public int strikesCooldownTicks = 220;
    public int strikeCount = 8;
    public float strikeDamage = 2.0f;
    public float strikeFinisherDamage = 5.0f;

    // Sukuna (transformation, fixed 60 s in code)
    public int cleaveCooldownTicks = 100;
    public float cleaveDamage = 12.0f;
    public int dismantleCooldownTicks = 120;
    public float dismantleDamage = 7.0f;
    public int flameArrowCooldownTicks = 240;
    public float flameArrowDamage = 14.0f;
    public int pressureCooldownTicks = 400;
    public float pressureDamagePerPulse = 4.0f;
    public double pressureRadius = 11.0;

    // Gojo ultimate moves. Fixed by design (not configurable): Maximum Blue = 2 dmg/s for 10 s,
    // Hollow Nuke = 50 dmg to targets / 10 dmg to Gojo, domain stun = 15 s.
    public int maxBlueCooldownTicks = 400;
    public double maxBlueRadius = 20.0;
    public double maxBluePullStrength = 0.9;
    public int maxRedCooldownTicks = 300;
    public float maxRedDamage = 25.0f;
    public double maxRedRadius = 8.0;
    public float maxRedComboFlashDamage = 20.0f;
    public int hollowNukeCooldownTicks = 1200;
    public double hollowNukeRadius = 14.0;
    public int domainCooldownTicks = 1200;
    public double domainRadius = 20.0;

    // Exhaustion after Hollow Nuke / 0.2 Domain (default 20 s)
    public int exhaustionTicks = 400;

    // Six Eyes passive (Gojo only)
    public int sixEyesRange = 32;

    public int maxGuard = 100;
    public double guardCostPerDamage = 4.0;
    public int guardBreakTicks = 60;
    public int guardRegenIntervalTicks = 2;

    public static GojoConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("gojomoveset.json");
        try {
            if (Files.exists(path)) {
                GojoConfig loaded = GSON.fromJson(Files.readString(path), GojoConfig.class);
                if (loaded != null) instance = loaded;
            }
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException | RuntimeException e) {
            GojoMod.LOGGER.error("Could not read/write gojomoveset.json, using defaults", e);
        }
    }
}
