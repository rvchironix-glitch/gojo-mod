package com.jjs.gojo.effect;

/** A server-side timed effect (orb, projectile...). Ticked once per server tick. */
public interface ServerEffect {
    /** @return true when the effect is finished and should be removed. */
    boolean tick();
}
