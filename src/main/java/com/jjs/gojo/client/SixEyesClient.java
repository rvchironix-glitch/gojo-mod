package com.jjs.gojo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Six Eyes through-wall vision. Only runs when the server says this player is Gojo.
 * Nearby living entities get the vanilla glow outline on THIS client only (in 1.20.1 {@code setGlowing} on the
 * client sets a local field), so other players never see it. Entities that leave range, or everything when the
 * passive turns off, are un-glowed again.
 */
public final class SixEyesClient {
    private static final Set<Integer> GLOWED = new HashSet<>();

    private SixEyesClient() {}

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !ClientState.sixEyes) {
            if (!GLOWED.isEmpty()) clear();
            return;
        }
        if (mc.player.age % 4 != 0) return;
        double r = ClientState.sixEyesRange;
        Set<Integer> now = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive() || e.isSpectator()) continue;
            if (e.squaredDistanceTo(mc.player) > r * r) continue;
            e.setGlowing(true);
            now.add(e.getId());
        }
        Iterator<Integer> it = GLOWED.iterator();
        while (it.hasNext()) {
            int id = it.next();
            if (!now.contains(id)) {
                Entity e = mc.world.getEntityById(id);
                if (e != null) e.setGlowing(false);
                it.remove();
            }
        }
        GLOWED.addAll(now);
    }

    public static void clear() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.world != null) {
            for (int id : GLOWED) {
                Entity e = mc.world.getEntityById(id);
                if (e != null) e.setGlowing(false);
            }
        }
        GLOWED.clear();
    }
}
