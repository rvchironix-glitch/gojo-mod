package com.jjs.gojo.effect;

import com.jjs.gojo.Exhaustion;
import com.jjs.gojo.GojoMod;
import com.jjs.gojo.PlayerState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * HOLLOW NUKE: Gojo rises, a giant Maximum Blue forms, he charges Maximum Red and fires it into the Blue.
 * The collision deals exactly 50 damage once to each living target in range, Gojo takes exactly 10, no blocks
 * are touched, and Gojo ends up EXHAUSTED. Total airtime is 70 ticks, which stays under vanilla's anti-fly kick.
 */
public class HollowNuke implements ServerEffect {
    public static final float TARGET_DAMAGE = 50.0f;
    public static final float SELF_DAMAGE = 10.0f;

    private static final int RISE = 20;
    private static final int FORM = 40;
    private static final int PREPARE = 60;
    private static final int END = 70;
    private static final double HEIGHT = 8.0;

    private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.1f, 0.4f, 1.0f), 2.4f);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.08f, 0.08f), 2.2f);
    private static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(0.9f, 0.9f, 1.0f), 2.0f);
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private final ServerWorld world;
    private final UUID ownerId;
    private final double startY;
    private final Vec3d bluePos;
    private int age;

    private HollowNuke(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        this.startY = owner.getY();
        Vec3d flat = owner.getRotationVec(1.0f).multiply(1, 0, 1);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        this.bluePos = owner.getEyePos().add(flat.multiply(12)).add(0, HEIGHT, 0);
        owner.setNoGravity(true);
        owner.fallDistance = 0;
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 3.0f, 0.5f);
    }

    public static boolean isActive(UUID id) {
        return ACTIVE.contains(id);
    }

    /** False if one is already running for this player. */
    public static boolean start(ServerPlayerEntity owner) {
        if (!ACTIVE.add(owner.getUuid())) return false;
        EffectManager.add(new HollowNuke(owner));
        return true;
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.getServerWorld() != world) {
            abort(owner);
            return true;
        }
        PlayerState st = GojoMod.state(owner);
        long now = world.getServer().getTicks();
        st.channelUntil = now + 3;
        owner.fallDistance = 0;

        // rise, then hover
        double vy = age < RISE ? HEIGHT / RISE : 0.0;
        owner.setVelocity(0, vy, 0);
        owner.velocityModified = true;

        Vec3d hand = Fx.hand(owner);

        if (age >= 5 && age < PREPARE + 10) {
            double t = Math.min(1.0, (age - 5) / 35.0);
            sphere(bluePos, 0.5 + t * 4.5, BLUE, 12 + (int) (t * 20));
            world.spawnParticles(ParticleTypes.END_ROD, bluePos.x, bluePos.y, bluePos.z, 4, 1.5 * t, 1.5 * t, 1.5 * t, 0.03);
            if (age % 8 == 0) {
                world.playSound(null, bluePos.x, bluePos.y, bluePos.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                        SoundCategory.PLAYERS, 3.0f, 0.5f + (float) t * 0.4f);
            }
        }
        if (age < RISE) {
            world.spawnParticles(BLUE, owner.getX(), owner.getY(), owner.getZ(), 10, 0.5, 0.1, 0.5, 0);
        }
        if (age >= FORM && age < PREPARE) {
            double t = (age - FORM) / (double) (PREPARE - FORM);
            sphere(hand, 0.2 + t * 1.0, RED, 10);
            if (age % 5 == 0) {
                world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_BEACON_POWER_SELECT,
                        SoundCategory.PLAYERS, 2.0f, 0.5f + (float) t);
            }
        }
        if (age >= PREPARE && age < END) {
            double t = (age - PREPARE) / (double) (END - PREPARE);
            Vec3d p = hand.add(bluePos.subtract(hand).multiply(t));
            world.spawnParticles(RED, p.x, p.y, p.z, 30, 0.7, 0.7, 0.7, 0);
            Fx.line(world, hand, p, 1.0, RED);
        }
        age++;
        if (age >= END) {
            detonate(owner);
            owner.setNoGravity(false);
            st.channelUntil = 0;
            st.fallImmuneUntil = now + 100;
            ACTIVE.remove(ownerId);
            return true;
        }
        return false;
    }

    private void sphere(Vec3d c, double r, DustParticleEffect e, int n) {
        for (int i = 0; i < n; i++) {
            Vec3d v = new Vec3d(world.getRandom().nextGaussian(), world.getRandom().nextGaussian(), world.getRandom().nextGaussian());
            if (v.lengthSquared() < 1.0E-4) continue;
            Vec3d p = c.add(v.normalize().multiply(r));
            world.spawnParticles(e, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void detonate(ServerPlayerEntity owner) {
        double radius = com.jjs.gojo.GojoConfig.get().hollowNukeRadius;
        Set<UUID> hit = new HashSet<>(); // each target only once
        for (LivingEntity e : Fx.around(owner, bluePos, radius)) {
            if (e instanceof ServerPlayerEntity sp && sp.isCreative()) continue;
            if (!hit.add(e.getUuid())) continue;
            CombatUtil.damageExact(owner, e, TARGET_DAMAGE);
            CombatUtil.knockback(e, bluePos, 2.5, 0.8);
        }
        CombatUtil.selfDamageExact(owner, SELF_DAMAGE);

        // huge visuals, no block damage
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, bluePos.x, bluePos.y, bluePos.z, 6, 3, 3, 3, 0);
        world.spawnParticles(ParticleTypes.FLASH, bluePos.x, bluePos.y, bluePos.z, 4, 1, 1, 1, 0);
        for (double rr = 2; rr <= radius; rr += 2) {
            Fx.ring(world, bluePos, rr, (int) (rr * 8), rr < radius / 2 ? WHITE : BLUE);
            Fx.ring(world, bluePos.add(0, 0.1, 0), rr * 0.9, (int) (rr * 6), RED);
        }
        sphere(bluePos, radius * 0.5, WHITE, 150);
        sphere(bluePos, radius * 0.8, BLUE, 150);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, bluePos.x, bluePos.y, bluePos.z, 120, radius * 0.4, radius * 0.4, radius * 0.4, 0.2);
        world.playSound(null, bluePos.x, bluePos.y, bluePos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 8.0f, 0.5f);
        world.playSound(null, bluePos.x, bluePos.y, bluePos.z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 6.0f, 0.5f);
        world.playSound(null, bluePos.x, bluePos.y, bluePos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 8.0f, 0.6f);
        Fx.broadcastTitle(world, bluePos, 64, Text.literal("HOLLOW NUKE").formatted(Formatting.AQUA, Formatting.BOLD),
                Text.literal(owner.getName().getString()).formatted(Formatting.WHITE));
        Exhaustion.apply(world.getServer(), ownerId);
    }

    /** Disconnect / death / dimension change mid-cast: stop cleanly with no explosion. */
    private void abort(ServerPlayerEntity owner) {
        ACTIVE.remove(ownerId);
        if (owner != null) {
            owner.setNoGravity(false);
            GojoMod.state(owner).channelUntil = 0;
            if (owner.isAlive()) Exhaustion.apply(world.getServer(), ownerId);
        }
    }
}
