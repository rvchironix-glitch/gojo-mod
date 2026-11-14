package com.jjs.gojo.effect;

import com.jjs.gojo.Exhaustion;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maximum Red. A 4 second (80 tick) red charge, then it fires. Pressing the move again while the charge is
 * running (after a short debounce, with a target in sight) starts the combo instead:
 * red orb jumps behind the target, shoves it back into Gojo, Black Flash, then Hollow Nuke.
 * The combo is decided entirely on the server; presses outside the charge window do nothing.
 */
public class MaximumRed implements ServerEffect {
    /** Exactly 4 seconds. */
    public static final int CHARGE_TICKS = 80;
    private static final int COMBO_MIN_AGE = 8;
    private static final int PUSH_MAX = 14;

    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.08f, 0.08f), 2.0f);
    private static final DustParticleEffect DEEP = new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.05f), 1.4f);
    private static final Map<UUID, MaximumRed> ACTIVE = new HashMap<>();

    private enum Phase { CHARGE, FIRE, PUSH }

    private final ServerWorld world;
    private final UUID ownerId;
    private Phase phase = Phase.CHARGE;
    private int age;
    private int phaseAge;
    private Vec3d orb = Vec3d.ZERO;
    private Vec3d dir = Vec3d.ZERO;
    private UUID targetId;
    private Vec3d pushDir = Vec3d.ZERO;

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

    /** Any later press while a Maximum Red is running. Always consumed; only valid ones do anything. */
    public static void handleSecondPress(ServerPlayerEntity owner) {
        MaximumRed r = ACTIVE.get(owner.getUuid());
        if (r != null) r.tryCombo(owner);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    private void tryCombo(ServerPlayerEntity owner) {
        if (phase != Phase.CHARGE || age < COMBO_MIN_AGE || age >= CHARGE_TICKS) return;
        if (HollowNuke.isActive(ownerId) || Exhaustion.isExhausted(owner)) return;
        LivingEntity target = findTarget(owner);
        if (target == null) {
            owner.sendMessage(Text.literal("No target in sight for the combo"), true);
            return;
        }
        targetId = target.getUuid();
        Vec3d toT = target.getPos().subtract(owner.getPos()).multiply(1, 0, 1);
        pushDir = toT.lengthSquared() < 1.0E-4 ? owner.getRotationVec(1.0f).multiply(1, 0, 1) : toT;
        pushDir = pushDir.normalize(); // Gojo -> target
        Vec3d center = target.getPos().add(0, target.getHeight() / 2.0, 0);
        orb = behind(center);
        phase = Phase.PUSH;
        phaseAge = 0;
        // flash where the red reappears
        world.spawnParticles(ParticleTypes.FLASH, orb.x, orb.y, orb.z, 1, 0, 0, 0, 0);
        world.spawnParticles(RED, orb.x, orb.y, orb.z, 60, 0.8, 0.8, 0.8, 0);
        world.playSound(null, orb.x, orb.y, orb.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 2.0f, 0.5f);
        CombatUtil.damage(owner, target, GojoConfig.get().maxRedDamage);
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, 0.4, 0.4, 0.4, 0);
    }

    private LivingEntity findTarget(ServerPlayerEntity owner) {
        Vec3d eye = owner.getEyePos();
        Vec3d end = eye.add(owner.getRotationVec(1.0f).normalize().multiply(30));
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Fx.alongSegment(owner, eye, end, 1.3)) {
            if (e instanceof ServerPlayerEntity sp && sp.isCreative()) continue;
            double d = e.squaredDistanceTo(owner);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** A spot a few blocks behind the target (as seen from Gojo) that is not inside blocks; else the target itself. */
    private Vec3d behind(Vec3d center) {
        for (double d = 3.0; d >= 1.0; d -= 1.0) {
            Vec3d p = center.add(pushDir.multiply(d));
            if (free(p) && free(p.add(0, -1, 0).add(0, 0.5, 0))) return p;
        }
        return center;
    }

    private boolean free(Vec3d p) {
        return world.getBlockState(BlockPos.ofFloored(p)).getCollisionShape(world, BlockPos.ofFloored(p)).isEmpty();
    }

    @Override
    public boolean tick() {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.getServerWorld() != world) {
            end(owner);
            return true;
        }
        PlayerState st = GojoMod.state(owner);
        long now = world.getServer().getTicks();
        switch (phase) {
            case CHARGE -> {
                st.channelUntil = now + 3;
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
                    phaseAge = 0;
                    dir = owner.getRotationVec(1.0f).normalize();
                    orb = Fx.hand(owner);
                    world.playSound(null, orb.x, orb.y, orb.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 1.4f);
                }
            }
            case FIRE -> {
                st.channelUntil = now + 3;
                for (int i = 0; i < 3; i++) {
                    orb = orb.add(dir);
                    world.spawnParticles(RED, orb.x, orb.y, orb.z, 14, 0.5, 0.5, 0.5, 0);
                    boolean hitBlock = !free(orb);
                    boolean hitEntity = !Fx.around(owner, orb, 1.6).isEmpty();
                    if (hitBlock || hitEntity || ++phaseAge > 14) {
                        explode(owner, hitBlock ? orb.subtract(dir) : orb);
                        end(owner);
                        return true;
                    }
                }
            }
            case PUSH -> {
                st.channelUntil = now + 3;
                owner.setVelocity(0, Math.min(owner.getVelocity().y, 0), 0);
                owner.velocityModified = true;
                LivingEntity target = null;
                var e = world.getEntity(targetId);
                if (e instanceof LivingEntity le && le.isAlive()) target = le;
                boolean close = target != null && target.getPos().distanceTo(owner.getPos()) < 3.0;
                if (target == null || close || phaseAge >= PUSH_MAX) {
                    if (target != null) blackFlash(owner, target);
                    HollowNuke.start(owner);
                    GojoMod.startCooldown(owner, com.jjs.gojo.Ability.HOLLOW_NUKE, com.jjs.gojo.Ability.HOLLOW_NUKE.cooldownTicks());
                    end(owner);
                    return true;
                }
                BlueTrap.hold(target, now); // cannot attack or move on its own while being shoved
                Vec3d toG = owner.getPos().subtract(target.getPos()).multiply(1, 0, 1);
                if (toG.lengthSquared() > 1.0E-4) {
                    Vec3d v = toG.normalize().multiply(1.3);
                    target.setVelocity(v.x, 0.12, v.z);
                    target.velocityModified = true;
                }
                orb = target.getPos().add(0, target.getHeight() / 2.0, 0).add(pushDir.multiply(1.2));
                world.spawnParticles(RED, orb.x, orb.y, orb.z, 20, 0.5, 0.5, 0.5, 0);
                world.spawnParticles(ParticleTypes.CRIMSON_SPORE, orb.x, orb.y, orb.z, 6, 0.4, 0.4, 0.4, 0);
                phaseAge++;
            }
        }
        return false;
    }

    private void blackFlash(ServerPlayerEntity owner, LivingEntity t) {
        owner.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        CombatUtil.damage(owner, t, GojoConfig.get().maxRedComboFlashDamage, true);
        CombatUtil.knockback(t, owner.getPos(), 1.2, 0.3);
        Vec3d at = t.getPos().add(0, t.getHeight() * 0.6, 0);
        world.spawnParticles(Fx.BLACK, at.x, at.y, at.z, 50, 0.6, 0.6, 0.6, 0);
        world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 40, 0.6, 0.6, 0.6, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 0.5, 0.5, 0.5, 0.4);
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.5f, 1.4f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.8f);
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
