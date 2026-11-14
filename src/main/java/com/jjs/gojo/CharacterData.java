package com.jjs.gojo;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world, per-player persistent data stored with the overworld
 * (saved in the world folder under data/gojomoveset_characters.dat).
 * Survives death, respawn, rejoin and restarts.
 */
public class CharacterData extends PersistentState {
    private final Map<UUID, Integer> types = new HashMap<>();
    private final Map<UUID, Long> rctReady = new HashMap<>();
    private final Map<UUID, Float> meters = new HashMap<>();
    private final Map<UUID, Integer> heroes = new HashMap<>();

    public static CharacterData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(CharacterData::fromNbt, CharacterData::new, "gojomoveset_characters");
    }

    public static CharacterData fromNbt(NbtCompound nbt) {
        CharacterData d = new CharacterData();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                UUID id = UUID.fromString(key);
                NbtCompound p = players.getCompound(key);
                d.types.put(id, p.getInt("type"));
                if (p.contains("rctReady")) d.rctReady.put(id, p.getLong("rctReady"));
                if (p.contains("meter")) d.meters.put(id, p.getFloat("meter"));
                if (p.contains("hero")) d.heroes.put(id, p.getInt("hero"));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        Set<UUID> ids = new HashSet<>(types.keySet());
        ids.addAll(rctReady.keySet());
        ids.addAll(meters.keySet());
        ids.addAll(heroes.keySet());
        for (UUID id : ids) {
            NbtCompound p = new NbtCompound();
            p.putInt("type", types.getOrDefault(id, Characters.NONE));
            if (rctReady.containsKey(id)) p.putLong("rctReady", rctReady.get(id));
            if (meters.containsKey(id)) p.putFloat("meter", meters.get(id));
            if (heroes.containsKey(id)) p.putInt("hero", heroes.get(id));
            players.put(id.toString(), p);
        }
        nbt.put("players", players);
        return nbt;
    }

    public int type(UUID id) {
        return types.getOrDefault(id, Characters.NONE);
    }

    /** @return true if the choice was saved; false if the player had already chosen. */
    public boolean setTypeIfUnset(UUID id, int type) {
        if (type(id) != Characters.NONE) return false;
        types.put(id, type);
        markDirty();
        return true;
    }

    public long rctReadyAt(UUID id) {
        return rctReady.getOrDefault(id, 0L);
    }

    public void setRctReadyAt(UUID id, long worldTime) {
        rctReady.put(id, worldTime);
        markDirty();
    }

    /** Ultimate charge, 0..100. */
    public float meter(UUID id) {
        return meters.getOrDefault(id, 0f);
    }

    public void setMeter(UUID id, float value) {
        meters.put(id, Math.max(0f, Math.min(100f, value)));
        markDirty();
    }

    /** Selected sorcerer character (see {@link Heroes}); 0 = not chosen yet. */
    public int hero(UUID id) {
        return heroes.getOrDefault(id, 0);
    }

    public void setHero(UUID id, int hero) {
        heroes.put(id, hero);
        markDirty();
    }
}
