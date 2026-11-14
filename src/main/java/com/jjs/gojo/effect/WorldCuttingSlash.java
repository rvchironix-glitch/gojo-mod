package com.jjs.gojo.effect;

import com.jjs.gojo.GojoConfig;
import com.jjs.gojo.GojoMod;
import com.jjs.gojo.GojoNet;
import com.jjs.gojo.Ultimate;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sukuna's World Cutting Slash (R). Exactly 100 ticks (5 s) of preparation with a three-line chant shown in
 * everyone's chat, then a huge slash travels the way Sukuna faces. It cannot be started again while preparing,
 * and is cancelled safely if Sukuna dies, disconnects, changes dimension or loses the transformation before
 * the release. Each target is hurt at most once; no blocks are damaged (the slash cuts through terrain visually).
 */
public class WorldCuttingSlash implements ServerEffect {
    /** Exactly 5 seconds from activation to release. */
    public static final int PREP_TICKS = 100;
    private static final String[] CHANT = {"Dragon Scales...", "Repulsion...", "Twin Meteors..."};
    private static final int[] CHANT_AT = {0, 35, 70};
    private static final double SPEED = 3.0;

    /** owner -> server tick of the release (for syncing the HUD to players who rejoin mid-prep). */
    private static final Map<UUID, Long> RELEASE_AT = new HashMap<>();

    private final ServerWorld world;
    private final UUID ownerId;
    private final String ownerName;
    private final Set<UUID> hit = new HashSet<>();
    private int age;
    private boolean released;
    private Vec3d pos = Vec3d.ZERO;
    private Vec3d dir = Vec3d.ZERO;
    private double traveled;

    private WorldCuttingSlash(ServerPlayerEntity owner) {
        this.world = owner.getServerWorld();
        this.ownerId = owner.getUuid();
        this.ownerName = owner.getName().getString();
    }

    /** False if one is already preparing / flying for this player. */
    public static boolean start(ServerPlayerEntity owner) {
        if (RELEASE_AT.containsKey(owner.getUuid())) return false;
        MinecraftServer s = owner.getServer();
        RELEASE_AT.put(owner.getUuid(), s.getTicks() + 1L + PREP_TICKS);
        EffectManager.add(new WorldCuttingSlash(owner));
        send(owner, PREP_TICKS);
        return true;
    }

    public static void clearAll() {
        RELEASE_AT.clear();
    }

    /** Join / respawn: tells the HUD whether a preparation is running. */
    public static void sync(ServerPlayerEntity p) {
        Long at = RELEASE_AT.get(p.getUuid());
        send(p, at == null ? 0 : (int) Math.max(0, at - p.getServer().getTicks()));
    }

    private static void send(ServerPlayerEntity p, int remaining) {
        if (!ServerPlayNetworking.canSend(p, GojoNet.STATUS)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(1);
        buf.writeInt(remaining);
        buf.writeInt(PREP_TICKS);
        ServerPlayNetworking.send(p, GojoNet.STATUS, buf);
    }

    @Override
    public boolean tick() {
        MinecraftServer server = world.getServer();
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.getServerWorld() != world || !Ultimate.isSukuna(owner)) {
            cancel(owner);
            return true;
        }
        long now = server.getTicks();

        if (!released) {
            GojoMod.state(owner).channelUntil = now + 3; // no other ability, and this one cannot be restarted
            owner.setVelocity(0, Math.min(owner.getVelocity().y, 0), 0);
            owner.velocityModified = true;

            for (int i = 0; i < CHANT.length; i++) {
                if (age == CHANT_AT[i]) {
                    server.getPlayerManager().broadcast(
                            Text.literal(ownerName + ": ").formatted(Formatting.DARK_RED)
                                    .append(Text.literal(CHANT[i]).formatted(Formatting.RED, Formatting.ITALIC)), false);
                    world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 2.5f, 0.5f + 0.25f * i);
                }
            }
            prepare(owner);
            age++;
            if (age >= PREP_TICKS) release(owner);
            return false;
        }

        // flying slash
        GojoConfig c = GojoConfig.get();
        Vec3d prev = pos;
        pos = pos.add(dir.multiply(SPEED));
        traveled += SPEED;
        drawSlash();
        for (LivingEntity t : Fx.alongSegment(owner, prev, pos, 4.0)) {
            if (t instanceof ServerPlayerEntity sp && sp.isCreative()) continue;
            if (!hit.add(t.getUuid())) continue; // one hit per target per slash
            CombatUtil.damage(owner, t, c.worldSlashDamage, true);
            CombatUtil.knockback(t, pos.subtract(dir.multiply(3)), 2.2, 0.5);
            Vec3d at = t.getPos().add(0, t.getHeight() * 0.5, 0);
            world.spawnParticles(Fx.CRIMSON, at.x, at.y, at.z, 60, 0.6, 0.7, 0.6, 0.05);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 3, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 1.2f);
        }
        if (traveled >= c.worldSlashRange) {
            end(owner);
            return true;
        }
        return false;
    }

    private void prepare(ServerPlayerEntity owner) {
        double t = age / (double) PREP_TICKS;
        Vec3d c = owner.getPos();
        // rising spiral of crimson / black, tightening as the chant goes on
        for (int k = 0; k < 3; k++) {
            double a = age * 0.4 + k * (Math.PI * 2 / 3);
            double r = 2.5 - 1.2 * t;
            double y = (age % 40) / 40.0 * 3.0;
            world.spawnParticles(Fx.CRIMSON, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            world.spawnParticles(Fx.BLACK, c.x + Math.cos(a + 1) * r, c.y + 3.0 - y, c.z + Math.sin(a + 1) * r, 1, 0, 0, 0, 0);
        }
        Fx.ring(world, c.add(0, 0.1, 0), 1.0 + t * 4.0, 36, Fx.CRIMSON);
        // power gathers in the raised hand
        Vec3d h = Fx.hand(owner).add(0, 0.5, 0);
        world.spawnParticles(Fx.CRIMSON, h.x, h.y, h.z, (int) (4 + t * 14), 0.25 + t * 0.4, 0.25 + t * 0.4, 0.25 + t * 0.4, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 2, 0.3, 0.3, 0.3, 0.3);
    }

    private void release(ServerPlayerEntity owner) {
        released = true;
        dir = owner.getRotationVec(1.0f).normalize();
        pos = owner.getEyePos().add(dir.multiply(1.0));
        owner.swingHand(Hand.MAIN_HAND, true);
        GojoMod.state(owner).channelUntil = 0;
        send(owner, 0);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 5.0f, 0.7f);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.0f, 1.2f);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 3.0f, 0.6f);
        Fx.broadcastTitle(world, owner.getPos(), 64, Text.literal("WORLD CUTTING SLASH").formatted(Formatting.DARK_RED, Formatting.BOLD),
                Text.literal(ownerName).formatted(Formatting.WHITE));
    }

    /** A tall, wide crescent perpendicular to the flight direction, with a bright core and a trailing smear. */
    private void drawSlash() {
        Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d side = dir.crossProduct(up).normalize();
        Vec3d vert = side.crossProduct(dir).normalize();
        for (double s = -5.0; s <= 5.0; s += 0.5) {
            double bow = -0.04 * s * s + 1.0; // crescent curve: centre pushed forward
            Vec3d p = pos.add(side.multiply(s)).add(dir.multiply(bow));
            world.spawnParticles(Fx.CRIMSON, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
            world.spawnParticles(Fx.BONE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        for (double v = -3.0; v <= 3.0; v += 0.5) { // vertical cut line through the middle
            Vec3d p = pos.add(vert.multiply(v)).add(dir.multiply(1.0));
            world.spawnParticles(Fx.BLACK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
        Vec3d back = pos.subtract(dir.multiply(1.5));
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 1, 1.5, 0.5, 1.5, 0);
        world.spawnParticles(ParticleTypes.CRIT, back.x, back.y, back.z, 6, 2.5, 1.5, 2.5, 0.2);
    }

    private void cancel(ServerPlayerEntity owner) {
        RELEASE_AT.remove(ownerId);
        if (owner != null) {
            GojoMod.state(owner).channelUntil = 0;
            send(owner, 0);
        }
    }

    private void end(ServerPlayerEntity owner) {
        RELEASE_AT.remove(ownerId);
        if (owner != null) GojoMod.state(owner).channelUntil = 0;
    }
}
