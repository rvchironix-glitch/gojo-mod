package com.jjs.gojo;

import com.jjs.gojo.effect.CombatUtil;
import com.jjs.gojo.effect.Fx;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import org.joml.Vector3f;

/**
 * Yuji's R special: Cursed Energy. 30 s of cursed energy around his hands, 10 s cooldown after it ends.
 *  - While active, each normal M1 melee hit has an independent 20% chance to become a Black Flash (3x damage).
 *  - Abilities never get it, except Rapid Strikes (see YujiStrikes), which can flash at most once per use.
 * Timing is stored in the world save (overworld game time), so death, relog and restarts neither end it early
 * nor reset its cooldown. Everything is decided on the server.
 */
public final class CursedEnergy {
    public static final int DURATION_TICKS = 600;   // exactly 30 s
    public static final int COOLDOWN_TICKS = 200;   // exactly 10 s, counted from the end of the effect
    public static final float FLASH_CHANCE = 0.20f;
    public static final float FLASH_MULT = 3.0f;

    private static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.45f, 0.05f, 0.85f), 1.3f);

    private CursedEnergy() {}

    private static long worldTime(MinecraftServer s) {
        return s.getOverworld().getTime();
    }

    /** True while Yuji (not Sukuna) has Cursed Energy running. */
    public static boolean active(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null || !Characters.isSorcerer(p) || Ultimate.isAwakened(p)) return false;
        CharacterData d = CharacterData.get(s);
        return d.hero(p.getUuid()) == Heroes.YUJI && d.cursedUntil(p.getUuid()) > worldTime(s);
    }

    /** Rolls the independent 20% chance (used by M1 and by Rapid Strikes). */
    public static boolean roll(ServerPlayerEntity p) {
        return p.getRandom().nextFloat() < FLASH_CHANCE;
    }

    public static void activate(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        CharacterData d = CharacterData.get(s);
        UUID id = p.getUuid();
        if (d.hero(id) != Heroes.YUJI || Ultimate.isAwakened(p)) return; // never for Gojo or Sukuna
        long now = worldTime(s);
        if (d.cursedUntil(id) > now) return;          // already running
        if (!GojoMod.state(p).noCooldowns && now < d.cursedReadyAt(id)) return; // cooldown
        d.setCursed(id, now + DURATION_TICKS, now + DURATION_TICKS + COOLDOWN_TICKS);
        ServerWorld w = p.getServerWorld();
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.0f, 0.6f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 0.5f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.5f, 1.6f);
        Vec3d c = p.getPos().add(0, 1.0, 0);
        w.spawnParticles(Fx.BLACK, c.x, c.y, c.z, 60, 0.5, 0.8, 0.5, 0.02);
        w.spawnParticles(VIOLET, c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.6, 0.9, 0.6, 0.4);
        Fx.ring(w, p.getPos().add(0, 0.1, 0), 1.6, 28, Fx.BLACK);
        sync(p);
    }

    /** Called every server tick: hand aura for everyone who has it, visible to nearby players. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 2 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isAlive() || p.isSpectator() || !active(p)) continue;
            ServerWorld w = p.getServerWorld();
            Vec3d look = p.getRotationVec(1.0f);
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
            Vec3d right = new Vec3d(-flat.z, 0, flat.x);
            Vec3d base = p.getEyePos().add(look.multiply(0.7)).subtract(0, 0.55, 0);
            for (int side = -1; side <= 1; side += 2) {
                Vec3d hand = base.add(right.multiply(0.4 * side));
                w.spawnParticles(Fx.BLACK, hand.x, hand.y, hand.z, 3, 0.12, 0.12, 0.12, 0.01);
                w.spawnParticles(VIOLET, hand.x, hand.y, hand.z, 2, 0.1, 0.1, 0.1, 0);
                if (p.age % 6 == 0) {
                    w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 2, 0.15, 0.15, 0.15, 0.2);
                }
            }
        }
    }

    /**
     * Called from the damage event for every allowed hit. If it is a normal M1 melee hit by a Yuji with Cursed
     * Energy and the independent 20% roll succeeds, the hit is replaced by a Black Flash and true is returned.
     */
    public static boolean tryM1(LivingEntity victim, DamageSource source, float amount) {
        if (!source.isOf(DamageTypes.PLAYER_ATTACK)) return false;
        if (!(source.getAttacker() instanceof ServerPlayerEntity p) || source.getSource() != p) return false;
        if (victim == p || !victim.isAlive() || amount <= 0f) return false;
        if (!active(p) || !roll(p)) return false;

        GojoMod.inAbility = true;
        try {
            victim.damage(source, amount * FLASH_MULT); // same source, 3x the M1 damage
        } finally {
            GojoMod.inAbility = false;
        }
        flashEffects(p, victim);
        return true; // the original (un-multiplied) hit is cancelled so the target is hurt exactly once
    }

    /** Black Flash visuals + sound + knockback (also used by Rapid Strikes). */
    public static void flashEffects(ServerPlayerEntity p, LivingEntity victim) {
        ServerWorld w = p.getServerWorld();
        if (victim.isAlive()) CombatUtil.knockback(victim, p.getPos(), 1.9, 0.35);
        Vec3d at = victim.getPos().add(0, victim.getHeight() * 0.6, 0);
        w.spawnParticles(Fx.BLACK, at.x, at.y, at.z, 55, 0.5, 0.5, 0.5, 0.04);
        w.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 40, 0.5, 0.5, 0.5, 0.04);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 50, 0.5, 0.5, 0.5, 0.5);
        w.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0);
        Fx.ring(w, at, 1.8, 28, Fx.CRIMSON);
        w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.4f, 1.5f);
        w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.9f);
        p.sendMessage(Text.literal("BLACK FLASH!"), true);
    }

    /** Sends the timers to the client (activation, join, respawn). */
    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.STATUS)) return;
        MinecraftServer s = p.getServer();
        CharacterData d = CharacterData.get(s);
        long now = worldTime(s);
        long until = d.cursedUntil(p.getUuid());
        long ready = d.cursedReadyAt(p.getUuid());
        int dur = (int) Math.max(0, until - now);
        int cd = dur > 0 ? COOLDOWN_TICKS : (GojoMod.state(p).noCooldowns ? 0 : (int) Math.max(0, ready - now));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(0);
        buf.writeInt(dur);
        buf.writeInt(cd);
        ServerPlayNetworking.send(p, GojoNet.STATUS, buf);
    }
}
