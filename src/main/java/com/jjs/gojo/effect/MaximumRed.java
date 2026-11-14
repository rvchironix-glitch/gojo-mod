package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
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
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maximum Red. A 4 second (80 tick) red charge, then it fires.
 * Pressing the move again during the charge cancels the red and performs a Black Flash instead:
 * a devastating punch on the nearest valid target close to Gojo (config maxRedFlashRange). With no target in
 * range it whiffs and hurts nobody. It never teleports Gojo, never pulls targets and never starts Hollow Nuke
 * (Hollow Nuke is its own ability). One Black Flash per cast; extra presses are ignored.
 */
public class MaximumRed implements ServerEffect {
    /** Exactly 4 seconds. */
    public static final int CHARGE_TICKS = 80;
    /** Ignores the very first ticks so a single key press can never count as the second press. */
    private static final int SECOND_PRESS_MIN_AGE = 3;

    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.08f, 0.08f), 2.0f);
    private static final DustParticleEffect DEEP = new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.05f), 1.4f);
    private static final Map<UUID, MaximumRed> ACTIVE = new HashMap<>();

    private enum Phase { CHARGE, FIRE, FLASHED }

    private final ServerWorld world;
    private final UUID ownerId;
    private Phase phase = Phase.CHARGE;
    private int age;
    private int fireAge;
    private Vec3d orb = Vec3d.ZERO;
    private Vec3d dir = Vec3d.ZERO;

    private MaximumRed(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    public static boolean isActive(UUID id) {
        return ACTIVE.containsKey(id);
    }

    /** First press. False when one is already running. */
    public static boolean start(ServerPlayerEntity owner) {
        if (ACTIVE.containsKey(owner.getUuid())) return false;
        MaximumRed r = new MaximumRed(owner);
        ACTIVE.put(owner.getUuid(), r);
        EffectManager.add(r);
        return true;
    }

    /** Any later press while a Maximum Red is running. Always consumed; only one in the charge window does anything. */
    public static void handleSecondPress(ServerPlayerEntity owner) {
        MaximumRed r = ACTIVE.get(owner.getUuid());
        if (r != null) r.blackFlash(owner);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    private void blackFlash(ServerPlayerEntity owner) {
        if (phase != Phase.CHARGE || age < SECOND_PRESS_MIN_AGE || age >= CHARGE_TICKS) return;
        phase = Phase.FLASHED; // locks out repeated presses; the effect ends on its next tick

        GojoConfig c = GojoConfig.get();
        owner.swingHand(Hand.MAIN_HAND, true);

        // nearest valid target close to Gojo and roughly in front of him; nothing else is ever touched
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : CombatUtil.inFront(owner, Math.max(1.0, c.maxRedFlashRange), 0.2)) {
            if (e instanceof ServerPlayerEntity sp && sp.isCreative()) continue;
            double d = e.squaredDistanceTo(owner);
            if (d < best) {
                best = d;
                target = e;
            }
        }

        Vec3d fist = Fx.fist(owner);
        if (target == null) { // whiff
            world.spawnParticles(Fx.BLACK, fist.x, fist.y, fist.z, 25, 0.4, 0.4, 0.4, 0.02);
            world.spawnParticles(Fx.CRIMSON, fist.x, fist.y, fist.z, 15, 0.3, 0.3, 0.3, 0.02);
            world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.5f);
            owner.sendMessage(Text.literal("Black Flash missed - no target in range"), true);
            return;
        }

        CombatUtil.damage(owner, target, c.maxRedFlashDamage, true); // exactly one hit
        CombatUtil.knockback(target, owner.getPos(), 3.0, 0.5);
        Vec3d at = target.getPos().add(0, target.getHeight() * 0.6, 0);
        world.spawnParticles(Fx.BLACK, at.x, at.y, at.z, 90, 0.8, 0.8, 0.8, 0.05);
        world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 70, 0.8, 0.8, 0.8, 0.05);
        world.spawnParticles(RED, at.x, at.y, at.z, 40, 0.6, 0.6, 0.6, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 80, 0.7, 0.7, 0.7, 0.6);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20, 0.4, 0.4, 0.4, 0.08);
        world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0);
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0, 0, 0, 0);
        Fx.ring(world, at, 2.5, 36, Fx.CRIMSON);
        Fx.ring(world, at, 1.5, 24, Fx.BLACK);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 2.0f, 1.3f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0f, 0.7f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 1.5f, 0.5f);
    }

    private boolean free(Vec3d p) {
        BlockPos bp = BlockPos.ofFloored(p);
        return world.getBlockState(bp).getCollisionShape(world, bp).isEmpty();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.getServerWorld() != world || phase == Phase.FLASHED) {
            end(owner);
            return true;
        }
        PlayerState st = GojoMod.state(owner);
        long now = world.getServer().getTicks();
        st.channelUntil = now + 3;

        if (phase == Phase.CHARGE) {
            owner.setVelocity(0, Math.min(owner.getVelocity().y, 0), 0);
            owner.velocityModified = true;
            Vec3d h = Fx.hand(owner);
            double t = age / (double) CHARGE_TICKS;
            double r = 0.25 + t * 1.1;
            for (int i = 0; i < 6 + age / 8; i++) {
                Vec3d v = new Vec3d(world.getRandom().nextGaussian(), world.getRandom().nextGaussian(), world.getRandom().nextGaussian());
                if (v.lengthSquared() < 1.0E-4) continue;
                Vec3d p = h.add(v.normalize().multiply(r));
                world.spawnParticles(RED, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            world.spawnParticles(DEEP, h.x, h.y, h.z, 3, r * 0.4, r * 0.4, r * 0.4, 0);
            if (age % 10 == 0) {
                world.playSound(null, h.x, h.y, h.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.5f, 0.6f + (float) t);
            }
            age++;
            if (age >= CHARGE_TICKS) {
                phase = Phase.FIRE;
                dir = owner.getRotationVec(1.0f).normalize();
                orb = Fx.hand(owner);
                world.playSound(null, orb.x, orb.y, orb.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 1.4f);
            }
            return false;
        }

        // FIRE
        for (int i = 0; i < 3; i++) {
            orb = orb.add(dir);
            world.spawnParticles(RED, orb.x, orb.y, orb.z, 14, 0.5, 0.5, 0.5, 0);
            boolean hitBlock = !free(orb);
            boolean hitEntity = !Fx.around(owner, orb, 1.6).isEmpty();
            if (hitBlock || hitEntity || ++fireAge > 14) {
                explode(owner, hitBlock ? orb.subtract(dir) : orb);
                end(owner);
                return true;
            }
        }
        return false;
    }

    /** Particles + damage only; never touches blocks. */
    private void explode(ServerPlayerEntity owner, Vec3d at) {
        GojoConfig c = GojoConfig.get();
        List<LivingEntity> hit = Fx.around(owner, at, c.maxRedRadius);
        for (LivingEntity e : hit) {
            CombatUtil.damage(owner, e, c.maxRedDamage);
            CombatUtil.knockback(e, at, 2.2, 0.6);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        world.spawnParticles(RED, at.x, at.y, at.z, 200, c.maxRedRadius * 0.4, c.maxRedRadius * 0.4, c.maxRedRadius * 0.4, 0);
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0.5, 0.5, 0.5, 0);
        Fx.ring(world, at, c.maxRedRadius * 0.8, 60, RED);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 4.0f, 0.6f);
    }

    private void end(ServerPlayerEntity owner) {
        ACTIVE.remove(ownerId);
        if (owner != null) GojoMod.state(owner).channelUntil = 0;
    }
}
