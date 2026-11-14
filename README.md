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

## Status
Stages 5A and 5B done. Not yet implemented: touchscreen controls and HUD customization (Stage 5C).
