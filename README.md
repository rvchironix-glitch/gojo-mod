# Jujutsu Shenanigans Gojo Moveset (Fabric 1.20.1)

## Build
Upload this folder to a GitHub repo (keep the hidden `.github` folder). The "Build mod" action runs
automatically; download `gojomoveset-jar` from the finished run under **Actions -> Artifacts**.
Local build: `gradle build` (Java 17), jar at `build/libs/gojomoveset-<version>.jar`.

## Install
Fabric Loader 1.20.1 + Fabric API 0.92.2 + this jar in the `mods` folder (client and server).

## Default keys (all rebindable under Controls -> Gojo Moveset)
| Key | Action |
|---|---|
| G / double-tap WASD | Dash |
| B (hold) | Block |
| Z | Lapis Blue |
| X | Reversal Red |
| H | Rapid Punches |
| N | Twofold Kick |
| C | Reverse Cursed Technique |
| R | Hollow Purple (only while Limitless Awakened) |
| V | Ultimate (Gojo: Limitless Awakened 2:00, Yuji: Sukuna 1:00) |
| J | Choose / switch sorcerer (Gojo or Yuji) |
| O | Touch Ability Pad (tappable ability buttons) |
| K | Customize Controls (move / resize / fade HUD) |

## Ultimate
Meter fills from damage dealt, damage taken and kills. At 100% press V for Limitless Awakened
(exactly 120 s): +50% damage, half cooldowns, +40% Blue/Red radius, Hollow Purple unlocked.
The meter resets when it ends. Tunables are in `config/gojomoveset.json`.

## Characters
Pick Gojo or Yuji on the first sorcerer join (press J to switch later; blocked while transformed).
Move keys 1-4 (Z, X, H, N) map to the current character's moves:

| Key | Gojo | Yuji | Sukuna (Yuji ultimate) |
|---|---|---|---|
| Z | Lapis Blue | Divergent Fist | Cleave |
| X | Reversal Red | Black Flash | Dismantle |
| H | Rapid Punches | Crushing Blow | Flame Arrow |
| N | Twofold Kick | Rapid Strikes | Malevolent Pressure |

Sukuna is a 60 s transformation, never a selectable character. The player skin is not replaced;
Sukuna is shown with markings, extra eyes, aura, outline and title.

## Stage 5D: Gojo ultimate overhaul
While Limitless Awakened is active, Gojo's four moves are replaced (same keys):

| Key | Move | Behaviour |
|---|---|---|
| Z | Maximum Blue | huge Blue, pulls and holds everything in range, exactly 2 damage/s for exactly 10 s |
| X | Maximum Red | exactly 4 s charge, then fires. Press X again during the charge for a Black Flash instead (cancels the red): 35 damage once to the nearest target within `maxRedFlashRange` (4.5 blocks) in front of Gojo, strong knockback. No target = it whiffs and hurts nobody. No teleport, no pulling, never starts Hollow Nuke |
| H | Hollow Nuke | Gojo rises, giant Blue forms, Red is fired into it: exactly 50 damage once per target, exactly 10 to Gojo, no block damage, then EXHAUSTED |
| N | 0.2 Domain | stuns every mob/player in range for exactly 15 s (not Gojo, spectators or creative players), then EXHAUSTED |

Burnout (after Hollow Nuke / Domain, default 20 s, `exhaustionTicks`): no abilities, no blocking, red BURNOUT bar with the time left. No potion effects are applied; you can still move and look around normally.
**RCT during burnout:** does not heal; it removes exactly 10 s from the burnout timer (ending it at zero) and uses its normal 10 s cooldown. Outside burnout RCT heals 4 hearts as before.
It is stored in the world save so relogging cannot skip it, and always ends on its own.
Six Eyes (Gojo only): permanent Night Vision (one effect, topped up every second) and through-wall outlines of
nearby living things (`sixEyesRange`), drawn only on Gojo's own screen.
New config keys: maxBlue*, maxRed*, hollowNukeCooldownTicks/Radius, domainCooldownTicks/Radius, exhaustionTicks, sixEyesRange.

## Stage 5E: Sukuna damage (final values, `config/gojomoveset.json`)
| Move | Before | Now |
|---|---|---|
| Cleave | 12 | 22 per hit, heavier knockback, thicker crescent |
| Dismantle (3 cuts) | 7 per cut | 12 per cut (max 36), each cut hits a target once |
| Flame Arrow | 14 | direct hit 26 x 1.4 = 36.4 (once), explosion 26 with falloff to 50% for others, radius 6, never hits the direct target twice, no block damage |
| Malevolent Pressure (3 s) | 4 per 0.5 s pulse (8/s) | 7 per 0.5 s pulse (14/s, 6 pulses = 42 max), same 3 s |
Old config files get these four numbers once (`configVersion`); edit them afterwards freely.
Timings are unchanged: Gojo Limitless Awakened 120 s, Sukuna 60 s, and the meter resets to 0 when a transformation ends (recharge by fighting, no separate timed cooldown).

## HUD and touch controls
- PC HUD: ability column on the right edge, ultimate meter directly above the armor row.
- Customize Controls (K): Edit Mode ON/OFF, drag elements, resize (scroll or Size +/-), opacity, Reset Layout.
  PC and touch layouts are separate and saved per player in `config/gojomoveset-hud.json`.
- Touch: Java Edition has no touchscreen API. On phones the launcher emulates mouse/keyboard, so the touch
  buttons live on the Ability Pad: bind a launcher button to the "Touch Ability Pad" key, tap it, then tap an
  ability. Block toggles on/off. Auto-detection of mobile launchers is best-effort; override it in Customize Controls.

## Status
Stages 5A to 5E written. Not compiled or played by the author; see the delivery notes for known limits.
