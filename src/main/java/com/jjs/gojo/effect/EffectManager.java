package com.jjs.gojo.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server-thread only. */
public final class EffectManager {
    private static final List<ServerEffect> ACTIVE = new ArrayList<>();
    /** Effects added while ticking (e.g. a combo that starts Hollow Nuke) wait here until the next tick. */
    private static final List<ServerEffect> PENDING = new ArrayList<>();
    private static final int MAX_EFFECTS = 256;

    private EffectManager() {}

    public static void add(ServerEffect e) {
        if (ACTIVE.size() + PENDING.size() < MAX_EFFECTS) PENDING.add(e);
    }

    public static void tick() {
        if (!PENDING.isEmpty()) {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
        }
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
        PENDING.clear();
    }
}
