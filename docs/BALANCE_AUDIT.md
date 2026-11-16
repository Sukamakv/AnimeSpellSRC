# Spell balance audit

Generated from the `setCooldownSeconds / baseManaCost / spellPower / setMinRarity` declarations in `spells/*`.
**89 of 129 spell classes parse cleanly. The other 40 (most of the per-character packages: garou, rimuru-line, gilgamesh, ...) set
their stats in a helper or base class, so they are NOT covered by the tables below.** No numbers were changed: there is no play-test data
or stated design intent to rebalance against, and a blind pass over 100+ spells is more likely to break the mod than fix it.

## Tier medians (parsed spells)
| Rarity | n | cooldown (s) | base mana | mana / second of cooldown |
|---|---|---|---|---|
| UNCOMMON | 4 | 8.5 | 20 | 2.7 |
| RARE | 6 | 11 | 37.5 | 3.4 |
| EPIC | 13 | 18 | 70 | 4.5 |
| LEGENDARY | 23 | 120 | 250 | 2.9 |

Reading: cost per second rises UNCOMMON -> EPIC, then **drops** for LEGENDARY, because legendaries lean on very long cooldowns instead of
mana. That is a legitimate design, but it means a legendary is "cheap to hold, expensive to wait for" and an EPIC spammed on cooldown drains
more mana per second than a legendary. If that is not intended, raise legendary base mana (or shorten cooldowns).

## Outliers (mana/s vs own tier median)
| Spell | Tier | cd | mana | note |
|---|---|---|---|---|
| PunchBarrageSpell | UNCOMMON | 10 | 8 | 0.3x of tier. Cheapest spell in the mod; 50-tick cast. |
| HarrowedPathSpell | LEGENDARY | 260 | 120 | 0.2x. Longest non-ultimate cooldown yet one of the cheapest. |
| SovereignsDominationSpell | LEGENDARY | 155 | 90 | 0.2x. Same pattern. |

## Dead or suspicious values
* `maxLevel = 1` with per-level scaling that can never apply: StormLordsWrath (+60 mana/level), GaleBarrage (+40), TwilightWorldInFlames (+2 power), SunEternal (+1 power).
  Harmless today; will silently do nothing if someone raises the level cap, and mislead anyone tuning them.
* TimeStopSpell: `maxLevel 5` but 0 power scaling per level (levels only change mana: +100 per level). Levels 2-5 cost more and do nothing extra
  unless the duration is read from the level elsewhere.
* LEGENDARY by rarity but cheap and fast: DarkinBlade (8 s, 50 mana), InfernalChains (14 s, 55 mana) cost less than EPIC Seethe (18 s, 90 mana).

## Recommended next step (needs your decision)
1. Decide the intended mana/second band per rarity (e.g. 2-3 / 3-4 / 4-5 / 2-4 for legendary), then a script can flag and fix everything outside it.
2. Move the 40 helper-driven spells' numbers into one table (like `BalanceConfig.TABLE` already does for debuffs) so they can be audited the same way and tuned from a config file.
3. Play-test the three outliers and the four cheap legendaries above first; they are the likeliest to feel wrong.
