package com.jjs.gojo.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server-thread only. */
public final class EffectManager {
    private static final List<ServerEffect> ACTIVE = new ArrayList<>();
    private static final int MAX_EFFECTS = 256;

    private EffectManager() {}

    public static void add(ServerEffect e) {
        if (ACTIVE.size() < MAX_EFFECTS) ACTIVE.add(e);
    }

    public static void tick() {
        Iterator<ServerEffect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            ServerEffect e = it.next();
            boolean done;
            try {
                done = e.tick();
            } catch (RuntimeException ex) {
                done = true; // never let one bad effect break the server tick
            }
            if (done) it.remove();
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
