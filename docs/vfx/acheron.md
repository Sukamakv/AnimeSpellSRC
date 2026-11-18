# Acheron VFX rework

Old look: every beat was a static texture scaled up (flash blob up to 75 blocks, cross-impact 60 blocks, soft glow discs at the hand and the
hole) plus huge soft crescents. New look: server-timed sprite compositions (hard-edged streaks, claws, shards, fracture webs, ground cracks,
tendrils, petals) on the existing beats; the client renderer keeps only continuous geometry (thread ribbons, line rings, reflective ground,
fracture plates, crescent walls, the black sun and the final crescents of the Ultimate).

| Spell | Beat (time) | New layers |
|---|---|---|
| Octobolt Flash | arrival 0.35s | violet lightning strike from above, impact star, cracks |
| | 5 hits 0.75-1.95s | violet streak + shards per hit (white claws on every second hit), different roll each |
| | 3 waves 1.2/1.8/2.4s | bolt + impact on each nearby enemy |
| | final 2.7s | fracture web, crimson shards + tendrils, thin ring |
| Trilateral Wiltcross | cuts 0.45/0.85/1.25s | violet + white streak across the target, shards |
| | cross 1.7s | X of two white streaks, fracture web, shards, ring, cracks |
| Quadrivalent Ascendance | each dash 0.5+0.55i s | thin crimson beam along the dash, white streak, impact + shards at the arrival |
| | executions 3.0s | tendrils + shards on every executed enemy |
| | final 3.0s | vertical white beam, fracture web, cracks, ring, shards |
| Atop Rainleaf Hangs Oneness | cast 0s | petals + cracks over the area |
| | every 2s per marked enemy | petals |
| | knot transfer | crimson beam + tendrils on the receiver |
| | full knots | fracture + shards |
| Rainblade | thin cuts 0.8-5.6s | one white streak every 5 ticks at a random point |
| | waves 2.2/3.8/5.4s | 5-7 white streaks, crimson claws, cracks, ring (fracture web on the last) |
| | per enemy | impact, shards, tendrils when knots were eaten |
| Stygian Resurge | fall 3.4s | thin white beam |
| | impact 4.2s | cracks, ring, fracture, void smoke, 6 shards |
| | shockwave 4.6s | wide white ring, cracks |
| | 5.0s | petals |
| Slashed Dream Cries in Red | 13 slashes | white + crimson streak through the hole, claws on every second, fracture web growing, shards, cracks every third |
| | final 13.8s | two huge streaks, fracture, two shard bursts, ring, cracks, petals |

Removed from the renderer: all `acheron_final_flash` / `acheron_cross_impact` glows, the soft `GLOW` discs (hand, hole, ascent, transfer),
the glow layer of the black sun, the crescents of Octobolt/Wiltcross/Quadrivalent/Rainblade (replaced by sprite streaks).
