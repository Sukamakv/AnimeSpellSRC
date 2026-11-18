# Malenia VFX rework

Old look: soft glow blobs on the caster, ground GLOW discs, single-frame crescents, huge flash stars. New look: every beat is a server-timed
sprite composition (thin streak and claw cuts, shards, cracks, fracture webs, petals, rot tendrils, thin rings); the renderers keep blade
ribbons, trails, flowers, rune circles, wings and the rot sphere, and lose the glows, big flash stars and the duplicate crescents.

| Spell | Beat | New layers |
|---|---|---|
| Waterfowl Dance | each of 24 slashes (every 2 ticks) | one streak, alternating white / gold, roll varies per slash; every 8th: claws + gold shards; impact star on every 3rd hit enemy |
| | final crescent | thin ring, cracks, white + gold horizontal blades, shards; Dance bonus: gold claws + petals |
| Phantom Blossom | phantoms appear | fracture web + petals at each of the seven |
| | each phantom's strike | thrust: thin crimson beam + impact; diagonals: crimson + white streak (mirrored); spin: three streaks + ring; downward: beam from above + cracks; shards on all; rot cracks + tendrils where a cloud is left |
| | dive | fracture web, claws, streak, shards, ring (perfect: petals + rot tendrils) |
| Scarlet Bloom | dive | petals every 8 ticks |
| | impact | rot cracks, ring, impact star, shards, petals, ember smoke |
| | flower opening | petals along the opening radius every 4 ticks |
| | second burst | ring, cracks, petals, gold shards, rot tendrils |
| | garden | once a second: tendrils + petals at a random point |
| Miquella's Blade | dash | thin gold beam + impact |
| | 4 slashes | cuts matching the move (right to left, reverse diagonal, upward, spin = three streaks + ring) + shards |
| | thrust | gold beam through the target, impact, shards |
| | execution | vertical beam, fracture web, cracks, ring, shards (perfect: petals) |
| Goddess of Rot | wings | shards, petals, thin ring |
| | rise | rot cracks marking the target area |
| | impact | cracks, ring, impact, two shard bursts, tendrils, ember smoke |
| | bloom burst | ring, cracks, petals, gold shards |
| | each of 8 swords | thin beam from above, cracks, impact, shards, ring |
| | judgment wave | ring + three horizontal blades + cracks |
| | final detonation | fracture web, two shard bursts, tendrils, petals, ring, cracks |

Removed from renderers: caster glows, GLOW ground discs, flash stars of 3 blocks and bigger, the old crescent-slash draws (Waterfowl, Blade,
Phantom) and the sphere glow around the rot ball of the Goddess.
