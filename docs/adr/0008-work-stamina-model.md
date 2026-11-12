# ADR-0008: Work Stamina model

Status: Accepted (2026-11-09)

## Context

The operator first deferred stamina, then moved it into v1 with these rulings:

- Each level adds 3 minutes of work before a Cobblemon gets tired. Rarity has no effect.
- Recovery mirrors drain. A full recovery takes as long as a full drain, so a level-100 Cobblemon
  works 300 minutes and then rests 300 minutes. A Cobblemon that runs out does not work again until it
  has fully recovered.
- A bed block, in four tiers, cuts recovery time to 1/2, 1/3, 1/4 or 1/8. The tier-4 bed needs a
  Nether Star and 4 Rare Candy, and each tier is crafted from the tier below.
- A tired Cobblemon claims the nearest unclaimed bed, walks to it, and loops the sleep visual silently
  while it recovers. A bed fits any size of Cobblemon.
- Fairy-types heal the stamina of other Cobblemon in the same pasture. A heal costs the healer 25% and
  restores 100% of the ally, at any level. A healer picks Tired allies first, then the lowest ally
  under 25%. Fairy-types cannot heal other Fairy-types.
- A healer's level changes only its own recovery time. That time falls linearly from 300 minutes at
  level 1 to 30 minutes at level 100, then the operator multiplied it by 4: 1,200 minutes at level 1
  and 120 minutes at level 100.

Cobblemon already uses the name "stamina" for riding: `Pokemon.rideStamina` (saved as `RideStamina`),
`RidingStat.STAMINA` and the config key `infiniteRideStamina`.

## Decision

- **Name.** Call the value "Work Stamina" in all text and code.
- **Storage.** `Pokemon.persistentData`, compound `cobblemon_dynamics`, entry `stamina`, with
  `percent` (0.0 to 1.0), `tired` (boolean) and `last_game_time` (overworld game time). A level-up
  keeps the percent.
- **Capacity.** 3,600 ticks of Working per level.
- **Drain.** Only in the Working state, one tick of capacity per tick.
- **Worker recovery.** In every other state, a full recovery takes 3 min × level at the base rate. A
  bed divides that time by its speed factor while the Cobblemon lies on it: 2, 3, 4 or 8 for tiers 1
  to 4. The config stores these factors, so a tier-2 bed gives exactly 100 min at level 100.
- **Healer recovery.** A Fairy-type with no machine binding recovers in
  4 × (300 − 270 × (L − 1) ÷ 99) min. A leashed Fairy-type, for example a Muse, uses the worker rule.
- **Tired.** At 0%, the Cobblemon is Tired until it reaches 100%. It does not work or heal while Tired.
  Tired is a flag. While it is set, the worker is in the ToBed, Sleeping or RestingInPlace state.
- **Away.** In the PC, in the party, or in an unloaded chunk, recovery runs at the base rate. The
  engine applies it from `last_game_time` when it next loads the Cobblemon.
- **Live value.** The work engine holds the live value. It writes to `persistentData` and calls
  `onChange()` on each state change, every 600 ticks, on entity unload and on server stop. A write
  every tick would be too costly.
- **Display.** A synced entity attachment, `stamina_view`, mirrors the percent and state for head bars.
  The pasture panel gets its values through our panel payload.

## Consequences

- Uptime depends only on the bed tier, because work time and rest time both scale with level. Uptime
  = 1 ÷ (1 + bed fraction): 50% with no bed, 67% at tier 1, 75% at tier 2, 80% at tier 3, 89% at tier 4.
- A low-level worker cycles faster but produces the same output for roles that do not scale with level.
  Level changes the output of the Generator, the Pump, the Muse, Telekinesis, the Magnet and Tailwind.
  The Superheater superheats only from level 50. Work Stamina is part of the balance rule
  ([ADR-0010](0010-balance-rule.md)): without a healer, a worker is down 11% to 50% of the time.
- A level-100 healer needs 30 minutes of recovery per heal, and a level-1 healer needs 300 minutes.
  High-level healers are therefore the strong ones.
- A crash loses up to 600 ticks of Work Stamina change for loaded workers.

## Alternatives considered

- **No stamina.** It was the default first, and the operator then moved stamina into v1.
- **Hunger or feeding.** Rejected. It adds an item loop and a UI, and machines would lose uptime.
- **Scale heals by level, or limit heals to allies of equal or lower level.** These would close the
  balance hole where a level-1 healer outworks beds. The operator rejected both and scaled healer
  recovery instead.
- **Store the value in an entity attachment only.** A pastured Cobblemon's entity is not its storage.
  The value would be lost when the Cobblemon goes back to the PC.
