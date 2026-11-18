# VFX rework brief (read fully before touching code)

Repo: `/home/claude/animespellsrc` (Forge 1.20.1, Java 17, package `com.example.jjkspells`, Iron's Spells 'n Spellbooks addon).
Read `docs/VFX_GUIDE.md` first (the sprite-sheet system and the SpriteFx API).

## Why this job exists
The owner is happy with the visuals of the Vergil, Yone, Yasuo, Dante, Yhwach and Frieren packages and unhappy with the rest. Your package
looks bad. Typical problems in the old code:
* one flat, static texture scaled up (a "flash" blob 20-75 blocks wide, a big glowing disc, a single frame slash),
* additive glow stacked on glow, everything the same soft round blob,
* effects that do not match the beat of the mechanic (nothing telegraphs, nothing reads as a hit),
* controller-entity vanilla particle spam.

## What good looks like (the reference packages)
Read `vergil/client/JudgementCutRenderer.java` and `vergil/client/Pen.java` to see the bar: every spell is a short timeline of
layered, hard-edged shapes (thin blade lines, shards, rifts, impact stars), each pass tied to a moment of the mechanic
(anticipation -> strike -> aftermath), with restrained colour and very little halo.

## Hard rules
1. **Do not change gameplay**: no damage, mana, cooldown, range, hitbox, duration, AI or networking changes. Visuals only.
   Do not redraw or retexture entity models of existing franchise characters / bosses.
2. **No compiler is available** (Forge and Maven are unreachable). Code must be correct by inspection: re-read every edit, check imports,
   check that pattern variables (`instanceof X sl`) do not collide with names already in scope, no unused-but-required returns.
3. **Glow must be subtle**: never a big glowing circle/disc/halo, never glow attached to the caster, never a full-screen white flash
   bigger than the existing camera-flash system already does. Remove existing giant `glow`/`flash` billboards (size > ~6 blocks) or
   replace them with sharp shapes. Prefer `shards`, `streak`, `claws`, `cracks`, `fracture`, `tendrils`, `petals`, thin `ring`,
   `impact`, `lightning`, `beam`, plus `smoke` for weight. `burst` only for real explosions, and keep scale sensible.
4. **Sprite sheets**: server-side `SpriteFx.*` calls at the moments that matter (see API below). A moment is a tick or a few ticks; do not
   spam every tick. Looping effects get a lifetime instead of being re-spawned. Max ~6 SpriteFx calls per tick per entity.
   You may also draw in the existing client renderers when the effect is a continuous geometry (a beam, a moving blade), but replace the
   crude textures with the new look and animate by time, and keep the per-frame vertex counts low.
5. If a new look needs an effect kind that does not exist, do NOT edit the generator or SpriteFx. Compose from what exists, and list
   what you wished for in your final report ("wishlist").
6. Perf: no per-tick allocations in hot render paths where avoidable, no scans of large AABBs each tick, respect the existing
   camera-mode / low-detail config of the package (e.g. `cm()`).
7. Do not touch files outside your package (and the Madara files you were told about). Shared files (`ModNetwork`, `SpriteFx`, generator,
   registry classes in other packages) are off limits. Do not run git commit/push, and do not leave stray files in `src`
   (`find src -name 'javac*.args' -delete`).

## SpriteFx API (server side, `net.minecraft.server.level.ServerLevel`, `com.example.jjkspells.vfx.sprite.SpriteFx`)
Palettes: `FIRE CRIMSON AZURE VIOLET GOLD TOXIC WHITE ROT WIND`. Smoke: `ASH VOID EMBER`.
```java
SpriteFx.streak(level, pos, planeNormal, rollDeg, Palette.X, scale);   // thin tapered blade cut (scale 1 ~ 4.5 blocks long). planeNormal = the direction the viewer looks along; for a cut seen from the caster pass the look vector, roll turns it
SpriteFx.claws(level, pos, planeNormal, rollDeg, Palette.X, scale);    // three parallel cuts
SpriteFx.shards(level, pos, Palette.X, scale);                          // hard fragments flying out (billboard, scale 1 ~ 4 blocks)
SpriteFx.cracks(level, pos, Palette.X, scale);                          // ground cracks spreading (flat, scale 1 ~ 7 blocks)
SpriteFx.fracture(level, pos, Palette.X, scale);                        // shattered-glass web in the air (billboard)
SpriteFx.tendrils(level, pos, Palette.X, scale);                        // curling tendrils (billboard)
SpriteFx.petals(level, pos, Palette.X, scale);                          // petals spiralling out
SpriteFx.impact(level, pos, Palette.X, scale);                          // star flash with sparks, for hits (keep scale <= 2.5)
SpriteFx.ring(level, pos, Palette.X, scale);                            // ground shockwave ring (thin, scale 1 ~ 6 blocks)
SpriteFx.slash(level, pos, planeNormal, rollDeg, Palette.X, scale);     // big soft crescent (use sparingly)
SpriteFx.sparks(level, pos, Palette.X, scale);                          // spark shower
SpriteFx.smoke(level, pos, Smoke.ASH, scale);                           // dark/ashen billow, gives weight
SpriteFx.lightning(level, from, to, Palette.X, widthBlocks);            // jagged bolt
SpriteFx.beam(level, from, to, Palette.X, widthBlocks, ticks);          // straight beam for ticks
SpriteFx.vortex(level, pos, Palette.X, scale, ticks);                   // ground spiral
SpriteFx.flame(level, pos, Palette.X, scale, ticks);                    // upright flame column
SpriteFx.burst(level, pos, Palette.X, scale);                           // fireball (real explosions only)
```
Generic builder: `SpriteVfxApi.play(level, "slash_streak_crimson", pos).scale(f).dir(v).roll(deg).length(l).life(ticks).tint(r,g,b).attach(entity, yOff).decor().send();`
Effect names are `<kind>_<palette>`; kinds: `fire_burst flame_loop energy_orb slash_arc shockwave_ring lightning impact_star magic_circle vortex
beam_segment sparkle_burst glow_pulse slash_streak claw_rake shard_burst ground_crack petal_swirl fracture_web tendril_burst`, `smoke_puff_{ash,void,ember}`.
Existing reference usage: `grep -rn "SpriteFx\." src/main/java/com/example/jjkspells/irontomb`.

## Deliverable
1. Code changes in your package.
2. `docs/vfx/<package>.md`: per spell, a timeline table (tick/second -> what appears -> which call/layer) and what old effect it replaced.
3. Verification you ran (all must be clean):
   * `python3 -I tools_vfx/check_sprite_calls.py`
   * syntax-level javac on the files you changed:
     `mkdir -p $SCRATCH/jc && javac -proc:none -Xmaxerrs 5000 -d $SCRATCH/jc -sourcepath src/main/java <files> 2>&1 | grep "error:" | grep -vE "does not exist|cannot find symbol|does not override|static import only|cannot be applied|incompatible types|cannot be dereferenced|no suitable|is not abstract|unreported|cannot access"`
     (empty output = no syntax-level errors; the filtered kinds are expected because Forge jars are absent). Delete any `javac*.args`.
   * a manual re-read of every changed file for unused/duplicate variables and brace balance.
4. Final report (short): spells reworked, files changed, wishlist, anything you were unsure about.
