package com.jjs.gojo;

import com.jjs.gojo.effect.BlackFlash;
import com.jjs.gojo.effect.DomainExpansion;
import com.jjs.gojo.effect.HollowNuke;
import com.jjs.gojo.effect.MaximumBlue;
import com.jjs.gojo.effect.MaximumRed;
import com.jjs.gojo.effect.Stun;
import com.jjs.gojo.effect.BlueOrb;
import com.jjs.gojo.effect.CrushingBlow;
import com.jjs.gojo.effect.Dismantle;
import com.jjs.gojo.effect.DivergentFist;
import com.jjs.gojo.effect.FlameArrow;
import com.jjs.gojo.effect.MalevolentShrine;
import com.jjs.gojo.effect.WorldCuttingSlash;
import com.jjs.gojo.effect.ServerEffect;
import com.jjs.gojo.effect.SukunaCleave;
import com.jjs.gojo.effect.YujiStrikes;
import com.jjs.gojo.effect.BlueTrap;
import com.jjs.gojo.effect.EffectManager;
import com.jjs.gojo.effect.HollowPurple;
import com.jjs.gojo.effect.RapidPunches;
import com.jjs.gojo.effect.RctAura;
import com.jjs.gojo.effect.RedProjectile;
import com.jjs.gojo.effect.TwofoldKick;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GojoMod implements ModInitializer {
    public static final String MOD_ID = "gojomoveset";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Set only for the duration of one fixed-damage call (CombatUtil.damageExact): blocking and charging are skipped. */
    public static boolean bypassGuard;

    /** True while one of the mod's abilities (or a replaced Black Flash hit) is dealing damage: not a plain M1. */
    public static boolean inAbility;

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    public static PlayerState state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), id -> new PlayerState());
    }

    /** Null when the player has no live state (e.g. disconnected). */
    public static PlayerState stateOrNull(UUID id) {
        return STATES.get(id);
    }

    /** Starts a cooldown and tells the client (used by combos that trigger another move). */
    public static void startCooldown(ServerPlayerEntity p, Ability a, int ticks) {
        PlayerState playerState = state(p);
        if (!playerState.noCooldowns) playerState.startCooldown(a, p.getServer().getTicks(), ticks);
        Characters.sendCooldown(p, a, playerState.noCooldowns ? 0 : ticks, ticks);
    }

    @Override
    public void onInitialize() {
        GojoConfig.load();

        // Admin/testing commands. Permission level 2 means cheats enabled / operator permission.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("gojo")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("help").executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal(
                                "Commands: /gojo cooldowns <target> <off|on>; /gojo noexhaustion <target> <on|off>; "
                                        + "/gojo burnout <target> <clear|set seconds>; /gojo ultimate <target> set <0-100>; "
                                        + "/gojo status <target>"), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("cooldowns")
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.literal("off").executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        PlayerState state = state(target);
                                        state.noCooldowns = true;
                                        state.clearCooldowns();
                                        MinecraftServer server = target.getServer();
                                        long now = server.getOverworld().getTime();
                                        CharacterData data = CharacterData.get(server);
                                        data.setRctReadyAt(target.getUuid(), now);
                                        long cursedUntil = data.cursedUntil(target.getUuid());
                                        data.setCursed(target.getUuid(), cursedUntil, Math.max(now, cursedUntil));
                                        for (Ability ability : Ability.values()) {
                                            Characters.sendCooldown(target, ability, 0, 1);
                                        }
                                        CursedEnergy.sync(target);
                                        ctx.getSource().sendFeedback(() -> Text.literal("No cooldowns enabled for " + target.getName().getString()), true);
                                        return 1;
                                    }))
                                    .then(CommandManager.literal("on").executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        state(target).noCooldowns = false;
                                        CursedEnergy.sync(target);
                                        ctx.getSource().sendFeedback(() -> Text.literal("Normal cooldowns restored for " + target.getName().getString()), true);
                                        return 1;
                                    }))))
                    .then(CommandManager.literal("burnout")
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.literal("clear").executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        Exhaustion.reduce(target.getServer(), target.getUuid(), Integer.MAX_VALUE);
                                        state(target).exhaustedFlag = false;
                                        ctx.getSource().sendFeedback(() -> Text.literal("Burnout cleared for " + target.getName().getString()), true);
                                        return 1;
                                    }))
                                    .then(CommandManager.literal("set")
                                            .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(ctx -> {
                                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                MinecraftServer server = target.getServer();
                                                long until = seconds == 0 ? 0 : server.getOverworld().getTime() + seconds * 20L;
                                                CharacterData.get(server).setExhaustedUntil(target.getUuid(), until);
                                                state(target).exhaustedFlag = seconds > 0;
                                                Exhaustion.sync(target);
                                                ctx.getSource().sendFeedback(() -> Text.literal("Burnout set to " + seconds + " seconds for " + target.getName().getString()), true);
                                                return 1;
                                            }))))))
                    .then(CommandManager.literal("ultimate")
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.literal("set")
                                            .then(CommandManager.argument("percent", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                                int percent = IntegerArgumentType.getInteger(ctx, "percent");
                                                CharacterData.get(target.getServer()).setMeter(target.getUuid(), percent);
                                                Ultimate.sync(target);
                                                ctx.getSource().sendFeedback(() -> Text.literal("Ultimate meter set to " + percent + "% for " + target.getName().getString()), true);
                                                return 1;
                                            })))))
                    .then(CommandManager.literal("cursedenergy")
                            .then(CommandManager.literal("reset")
                                    .then(CommandManager.argument("target", EntityArgumentType.player()).executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        long now = target.getServer().getOverworld().getTime();
                                        CharacterData.get(target.getServer()).setCursed(target.getUuid(), now, now);
                                        CursedEnergy.sync(target);
                                        ctx.getSource().sendFeedback(() -> Text.literal("Cursed Energy timer reset for " + target.getName().getString()), true);
                                        return 1;
                                    }))))
                    .then(CommandManager.literal("status")
                            .then(CommandManager.argument("target", EntityArgumentType.player()).executes(ctx -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                MinecraftServer server = target.getServer();
                                CharacterData data = CharacterData.get(server);
                                PlayerState state = state(target);
                                int burnout = Exhaustion.remaining(server, target.getUuid()) / 20;
                                int energy = (int) Math.max(0, data.cursedUntil(target.getUuid()) - server.getOverworld().getTime()) / 20;
                                String report = "Gojo status for " + target.getName().getString()
                                        + " | noCooldowns=" + state.noCooldowns
                                        + " | noExhaustion=" + state.noExhaustion
                                        + " | burnout=" + burnout + "s"
                                        + " | ultimate=" + Math.round(data.meter(target.getUuid())) + "%"
                                        + " | cursedEnergy=" + energy + "s";
                                ctx.getSource().sendFeedback(() -> Text.literal(report), false);
                                return 1;
                            })))
                    .then(CommandManager.literal("noexhaustion")
                            .then(CommandManager.argument("target", EntityArgumentType.player())
                                    .then(CommandManager.literal("on").executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        state(target).noExhaustion = true;
                                        Exhaustion.reduce(target.getServer(), target.getUuid(), Integer.MAX_VALUE);
                                        state(target).exhaustedFlag = false;
                                        ctx.getSource().sendFeedback(() -> Text.literal("No exhaustion enabled for " + target.getName().getString()), true);
                                        return 1;
                                    }))
                                    .then(CommandManager.literal("off").executes(ctx -> {
                                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                        state(target).noExhaustion = false;
                                        ctx.getSource().sendFeedback(() -> Text.literal("Normal exhaustion restored for " + target.getName().getString()), true);
                                        return 1;
                                    })))
            );
        });

        // Ability requests from clients. Everything is validated and executed on the server.
        ServerPlayNetworking.registerGlobalReceiver(GojoNet.ABILITY, (server, player, handler, buf, sender) -> {
            int abilityId = buf.readByte();
            int dir = buf.readByte();
            server.execute(() -> handleAbility(player, abilityId, dir));
        });

        ServerPlayNetworking.registerGlobalReceiver(GojoNet.BLOCK, (server, player, handler, buf, sender) -> {
            boolean wants = buf.readBoolean();
            server.execute(() -> {
                PlayerState s = state(player);
                long now = server.getTicks();
                s.blocking = wants && Characters.isSorcerer(player) && player.isAlive()
                        && !player.isSpectator() && !s.isGuardBroken(now)
                        && (!Exhaustion.isExhausted(player) || s.noExhaustion) && !Stun.isStunned(player);
            });
        });

        // Permanent character choice: accepted exactly once per player per world.
        ServerPlayNetworking.registerGlobalReceiver(GojoNet.CHOOSE, (server, player, handler, buf, sender) -> {
            int wanted = buf.readByte();
            server.execute(() -> Characters.choose(server, player, wanted));
        });

        ServerPlayNetworking.registerGlobalReceiver(GojoNet.HERO, (server, player, handler, buf, sender) -> {
            int hero = buf.readByte();
            server.execute(() -> Characters.chooseHero(server, player, hero));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            Characters.applyHealth(p);
            p.setNoGravity(false); // a Hollow Nuke interrupted by a disconnect must not leave Gojo floating
            Characters.syncAll(p);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Characters.applyHealth(newPlayer);
            newPlayer.setNoGravity(false);
            if (!alive && Characters.isSorcerer(newPlayer)) {
                newPlayer.setHealth(newPlayer.getMaxHealth());
            }
            Characters.syncAll(newPlayer);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            EffectManager.tick();
            Stun.tick(server);
            Exhaustion.tick(server);
            SixEyes.tick(server);
            CursedEnergy.tick(server);
        });
        // Defeating an enemy grants extra ultimate charge.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity killer && killer != entity) {
                Ultimate.charge(killer, GojoConfig.get().chargePerKill);
            }
        });

        // A transformation never survives a server stop: end it so the meter resets.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (Ultimate.isAwakened(p)) Ultimate.end(server, p.getUuid());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EffectManager.clear();
            BlueTrap.clear();
            Stun.clear();
            MaximumRed.clearAll();
            HollowNuke.clearAll();
            DomainExpansion.clearAll();
            MalevolentShrine.clearAll();
            WorldCuttingSlash.clearAll();
            STATES.clear();
        });

        // Guard regeneration
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GojoConfig c = GojoConfig.get();
            if (server.getTicks() % Math.max(1, c.guardRegenIntervalTicks) != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                PlayerState s = STATES.get(p.getUuid());
                if (s != null && !s.blocking && s.guard < c.maxGuard && !s.isGuardBroken(server.getTicks())) {
                    s.guard++;
                }
            }
        });

        // Fall damage immunity after dashing + blocking + trapped attackers deal no damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GojoMod::allowDamage);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        boolean allowed = allowDamageInner(entity, source, amount);
        // Yuji's Cursed Energy: a normal M1 may turn into a Black Flash (never ability damage).
        if (allowed && !bypassGuard && !inAbility && CursedEnergy.tryM1(entity, source, amount)) return false;
        if (allowed && !bypassGuard && amount > 0f && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            chargeFromDamage(entity, source, amount);
        }
        return allowed;
    }

    /** Dealing damage and taking damage both fill the ultimate meter (not while in invulnerability frames). */
    private static void chargeFromDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.timeUntilRegen > 10) return;
        GojoConfig c = GojoConfig.get();
        if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != victim) {
            Ultimate.charge(attacker, Math.min(amount, victim.getHealth()) * c.chargePerDamageDealt);
        }
        if (victim instanceof ServerPlayerEntity hurt) {
            Ultimate.charge(hurt, amount * c.chargePerDamageTaken);
        }
    }

    private static boolean allowDamageInner(LivingEntity entity, DamageSource source, float amount) {
        MinecraftServer srv = entity.getServer();
        if (srv == null) return true;
        if (bypassGuard) return true; // fixed-damage moves (Maximum Blue / Hollow Nuke) cannot be blocked
        long now = srv.getTicks();

        // Anything caught in Lapis Blue is helpless: it cannot hurt anyone.
        Entity attacker = source.getAttacker();
        if (attacker != null && (BlueTrap.isTrapped(attacker, now) || Stun.isStunned(attacker))) {
            return false;
        }

        if (!(entity instanceof ServerPlayerEntity player)) return true;
        PlayerState s = STATES.get(player.getUuid());
        if (s == null) return true;

        if (source.isOf(DamageTypes.FALL) && now < s.fallImmuneUntil) {
            return false;
        }

        if (!s.blocking || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isOf(DamageTypes.FALL)) {
            return true;
        }

        // Only block hits coming from the front
        Entity src = source.getSource() != null ? source.getSource() : source.getAttacker();
        if (src != null) {
            Vec3d toAttacker = src.getPos().subtract(player.getPos()).multiply(1, 0, 1);
            Vec3d look = player.getRotationVec(1.0f).multiply(1, 0, 1);
            if (toAttacker.lengthSquared() > 1.0E-4 && look.lengthSquared() > 1.0E-4
                    && toAttacker.normalize().dotProduct(look.normalize()) < 0.2) {
                return true;
            }
        }

        // Successful block: negate the hit, drain guard
        GojoConfig c = GojoConfig.get();
        s.guard -= (int) Math.ceil(amount * c.guardCostPerDamage);
        var world = player.getServerWorld();
        Vec3d front = player.getPos().add(player.getRotationVec(1.0f).multiply(1, 0, 1).normalize().multiply(0.6)).add(0, 1.2, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z, 14, 0.25, 0.25, 0.25, 0.15);
        world.spawnParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 8, 0.2, 0.2, 0.2, 0.2);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.2f);

        if (s.guard <= 0) {
            s.guard = 0;
            s.blocking = false;
            s.guardBrokenUntil = now + c.guardBreakTicks;
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f);
        }
        return false;
    }

    private static void handleAbility(ServerPlayerEntity player, int abilityId, int dir) {
        Ability ability = Ability.fromId(abilityId);
        if (ability == null || !player.isAlive() || player.isSpectator()) return;

        // Server-side restriction: only Sorcerers may use any ability.
        if (!Characters.isSorcerer(player)) return;

        // The client only sends move slots; the server decides what they mean for this character.
        int hero = CharacterData.get(player.getServer()).hero(player.getUuid());
        if (hero == 0) return; // must pick Gojo or Yuji first
        ability = Kits.resolve(hero, Ultimate.isAwakened(player), ability);
        if (ability == null) return;

        if (ability == Ability.RCT) {
            if (Stun.isStunned(player)) return; // RCT stays usable during burnout
            doRct(player);
            return;
        }

        PlayerState s = state(player);

        // Exhausted (after Hollow Nuke / Domain) or stunned: no abilities at all unless an admin has bypassed burnout.
        boolean exhausted = Exhaustion.isExhausted(player) && !s.noExhaustion;
        if (exhausted || Stun.isStunned(player)) {
            if (exhausted) player.sendMessage(Text.literal("BURNOUT - only RCT is usable"), true);
            return;
        }

        // Pressing Maximum Red again while it is running: combo (only valid during the charge window).
        if (ability == Ability.MAXIMUM_RED && MaximumRed.isActive(player.getUuid())) {
            MaximumRed.handleSecondPress(player);
            return;
        }

        if (ability == Ability.ULTIMATE) {
            Ultimate.tryActivate(player);
            return;
        }

        long now = player.getServer().getTicks();

        // Busy with a channelled move (Maximum Red charge, Hollow Nuke, Domain cast): nothing else may start.
        if (now < s.channelUntil) return;

        // Hollow Purple is ultimate-only.
        if (ability == Ability.R_SPECIAL && !Ultimate.isGojoAwakened(player)) {
            player.sendMessage(Text.literal("Hollow Purple requires Limitless Awakened"), true);
            return;
        }
        if (!s.noCooldowns && !s.isReady(ability, now)) return;

        boolean fired = switch (ability) {
            case DASH -> doDash(player, s, dir, now);
            case LAPIS_BLUE -> {
                EffectManager.add(new BlueOrb(player));
                yield true;
            }
            case REVERSAL_RED -> {
                EffectManager.add(new RedProjectile(player));
                yield true;
            }
            case RAPID_PUNCHES -> {
                EffectManager.add(new RapidPunches(player));
                yield true;
            }
            case TWOFOLD_KICK -> {
                EffectManager.add(new TwofoldKick(player));
                yield true;
            }
            case R_SPECIAL -> {
                EffectManager.add(new HollowPurple(player));
                yield true;
            }
            case MAXIMUM_BLUE -> {
                EffectManager.add(new MaximumBlue(player));
                yield true;
            }
            case MAXIMUM_RED -> MaximumRed.start(player);
            case HOLLOW_NUKE -> HollowNuke.start(player);
            case DOMAIN -> DomainExpansion.start(player);
            case DIVERGENT_FIST -> launch(new DivergentFist(player));
            case BLACK_FLASH -> launch(new BlackFlash(player));
            case CRUSHING_BLOW -> launch(new CrushingBlow(player));
            case RAPID_STRIKES -> launch(new YujiStrikes(player));
            case CLEAVE -> launch(new SukunaCleave(player));
            case DISMANTLE -> launch(new Dismantle(player));
            case FLAME_ARROW -> launch(new FlameArrow(player));
            case MALEVOLENT_SHRINE -> MalevolentShrine.start(player);
            case WORLD_CUTTING_SLASH -> Ultimate.isSukuna(player) && WorldCuttingSlash.start(player);
            case CURSED_ENERGY -> {
                CursedEnergy.activate(player); // has its own persistent timer, so no generic cooldown here
                yield false;
            }
            default -> false;
        };

        if (fired) {
            int cd = ability.cooldownTicks();
            if (ability != Ability.R_SPECIAL && !ability.isGojoUltimateMove() && Ultimate.isGojoAwakened(player)) {
                cd = Math.max(1, Math.round(cd * GojoConfig.get().awakenedCooldownMult));
            }
            if (!s.noCooldowns) s.startCooldown(ability, now, cd);
            Characters.sendCooldown(player, ability, s.noCooldowns ? 0 : cd, cd);
        }
    }

    private static boolean launch(ServerEffect effect) {
        EffectManager.add(effect);
        return true;
    }

    /** Reverse Cursed Technique. Cooldown is stored in the world's save data so death/relog cannot reset it. */
    private static void doRct(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        CharacterData data = CharacterData.get(server);
        long now = server.getOverworld().getTime();
        PlayerState state = state(player);
        if (!state.noCooldowns && now < data.rctReadyAt(player.getUuid())) return;

        if (Exhaustion.isExhausted(player) && !state.noExhaustion) {
            // Burnout: RCT does not heal; it burns off exactly 10 s of the timer (same 10 s cooldown applies).
            int left = Exhaustion.reduce(server, player.getUuid(), 200);
            player.sendMessage(Text.literal(left > 0 ? "Burnout reduced by 10s" : "Burnout ended"), true);
        } else {
            if (player.getHealth() >= player.getMaxHealth()) {
                player.sendMessage(Text.literal("Already at full health"), true);
                return;
            }
            player.heal(GojoConfig.get().rctHealAmount); // LivingEntity.heal never exceeds max health
        }
        int cd = Ability.RCT.cooldownTicks();
        data.setRctReadyAt(player.getUuid(), state.noCooldowns ? now : now + cd);
        Characters.sendCooldown(player, Ability.RCT, state.noCooldowns ? 0 : cd, cd);
        EffectManager.add(new RctAura(player));
    }

    private static boolean doDash(ServerPlayerEntity player, PlayerState s, int dir, long now) {
        GojoConfig c = GojoConfig.get();
        double yawRad = Math.toRadians(player.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);

        Vec3d wish = switch (dir) {
            case 1 -> forward.negate();
            case 2 -> right.negate();
            case 3 -> right;
            default -> forward;
        };

        Vec3d vel = wish.multiply(c.dashSpeed);
        double y = player.isOnGround() ? 0.12 : Math.min(player.getVelocity().y, 0.1);
        player.setVelocity(vel.x, y, vel.z);
        player.velocityModified = true;
        player.fallDistance = 0;
        s.fallImmuneUntil = now + c.dashFallImmunityTicks;

        var world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 14, 0.3, 0.05, 0.3, 0.03);
        world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.2, 0.4, 0.2, 0.05);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.5f);
        return true;
    }
}
