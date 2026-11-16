# Sprite-sheet VFX guide

All new effects share one system: animated sprite sheets, drawn in one batched pass on the client.

## Pieces
| Piece | Where |
|---|---|
| Sheet generator (numpy + Pillow) | `tools_vfx/gen_sprite_sheets.py` -> `textures/sprite_vfx/*.png` + `vfx_sprites/*.json` |
| Effect definitions (hot reload with F3+T, resource-pack friendly) | `assets/jjk_spells/vfx_sprites/NAME.json` |
| Server API (queued, one packet per player per tick) | `vfx/sprite/SpriteVfxApi`, one-liners in `vfx/sprite/SpriteFx` |
| Client pool + batched renderer | `vfx/sprite/SpriteVfxManager` |
| Client config | `config/jjk_spells-vfx-client.toml` (maxActive, cullDistance, decorDensity, crossfade) |
| Debug | `/jjkvfx play NAME [scale]`, `/jjkvfx demo [palette]` |
| Call checker (no Minecraft needed) | `python3 tools_vfx/check_sprite_calls.py` |

## Effect kinds x palettes
Kinds: `fire_burst flame_loop energy_orb slash_arc shockwave_ring lightning impact_star magic_circle vortex beam_segment sparkle_burst glow_pulse`
Palettes: `fire crimson azure violet gold toxic white`. `smoke_puff` uses `ash void ember`.
Name = `<kind>_<palette>`, e.g. `impact_star_crimson`.

## SpriteFx one-liners (server side, `net.minecraft.server.level.ServerLevel`)
```java
SpriteFx.burst(level, pos, Palette.CRIMSON, 3f);                 // fireball / energy bloom, ~3 blocks at scale 1
SpriteFx.impact(level, pos, Palette.AZURE, 2f);                  // star flash + sparks: hits
SpriteFx.ring(level, pos, Palette.GOLD, 1.5f);                   // ground shockwave, ~6 blocks at scale 1
SpriteFx.slash(level, pos, planeNormal, rollDeg, Palette.WHITE, 2f);
SpriteFx.sparks(level, pos, Palette.VIOLET, 1f);                 // decor
SpriteFx.smoke(level, pos, Smoke.VOID, 2f);                      // decor
SpriteFx.lightning(level, from, to, Palette.AZURE, widthBlocks);
SpriteFx.beam(level, from, to, Palette.CRIMSON, widthBlocks, ticks);
SpriteFx.circle(level, pos, Palette.VIOLET, 1.2f, ticks);        // rotating rune circle on the ground (telegraphs, casts)
SpriteFx.vortex(level, pos, Palette.AZURE, 1f, ticks);
SpriteFx.flame(level, pos, Palette.FIRE, 1f, ticks);
SpriteFx.orbOn(level, entity, yOffset, Palette.GOLD, 1f, ticks); // follows the entity
SpriteFx.glowOn(level, entity, yOffset, Palette.WHITE, 1f, ticks);
SpriteFx.flameOn(level, entity, Palette.FIRE, 1f, ticks);
```
Anything else: `SpriteVfxApi.play(level, "name", pos).scale(..).velocity(..).dir(..).length(..).life(..).tint(r,g,b).roll(..).attach(entity, yOff).decor().radius(..).send();`

## Rules for spell code
1. Server side only (`level instanceof ServerLevel`). Never reference client classes from common code.
2. Use sprite effects for **moments** (cast, telegraph, impact, phase change, death), not per-tick spam. At most a few calls per tick per spell. Looping effects get a `ticks` lifetime instead of being re-spawned every tick.
3. Mark filler effects `.decor()` (sparks, smoke, flames) so low-end clients can thin them out.
4. Pick the palette from the spell's identity, keep it consistent within a spell.
5. Telegraph big attacks with `circle` (or `ring`) at the danger zone before the damage lands.
