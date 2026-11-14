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
| X | Maximum Red | exactly 4 s charge, then fires. Press X again during the charge (after ~0.4 s, target in sight) for the combo: red jumps behind the target, shoves it into Gojo, Black Flash, then Hollow Nuke |
| H | Hollow Nuke | Gojo rises, giant Blue forms, Red is fired into it: exactly 50 damage once per target, exactly 10 to Gojo, no block damage, then EXHAUSTED |
| N | 0.2 Domain | stuns every mob/player in range for exactly 15 s (not Gojo, spectators or creative players), then EXHAUSTED |

Exhaustion (default 20 s, `exhaustionTicks`): no abilities, no blocking, Nausea, red EXHAUSTED bar with the time left.
It is stored in the world save so relogging cannot skip it, and always ends on its own.
Six Eyes (Gojo only): permanent Night Vision (one effect, topped up every second) and through-wall outlines of
nearby living things (`sixEyesRange`), drawn only on Gojo's own screen.
New config keys: maxBlue*, maxRed*, hollowNukeCooldownTicks/Radius, domainCooldownTicks/Radius, exhaustionTicks, sixEyesRange.

## HUD and touch controls
- PC HUD: ability column on the right edge, ultimate meter directly above the armor row.
- Customize Controls (K): Edit Mode ON/OFF, drag elements, resize (scroll or Size +/-), opacity, Reset Layout.
  PC and touch layouts are separate and saved per player in `config/gojomoveset-hud.json`.
- Touch: Java Edition has no touchscreen API. On phones the launcher emulates mouse/keyboard, so the touch
  buttons live on the Ability Pad: bind a launcher button to the "Touch Ability Pad" key, tap it, then tap an
  ability. Block toggles on/off. Auto-detection of mobile launchers is best-effort; override it in Customize Controls.

## Status
Stages 5A, 5B, 5C and 5D written. Not compiled or played by the author; see the delivery notes for known limits.
