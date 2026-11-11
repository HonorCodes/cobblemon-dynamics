# ADR-0006: Data-driven roles

Status: Accepted (2026-11-09)

## Context

Version 1.0 has 16 roles. Phase 1 builds 12 roles across 9 types: Wheel Driver, Pump, Generator,
Stoker, Burner Keeper, Brew Stoker, Superheater, Cranker, Crystal Tender, Brewer, Healer and Muse.
Phase 2 adds Telekinesis, Magnet, Fan Catalyst and Tailwind
([ADR-0011](0011-v2-roles-in-first-release.md)).

Modpack makers expect to tune jobs. Cobbleworkers' data-pack jobs are popular, but a fixed set of
components limits them. Cobblebase hand-assigns skills to each species in about 1,367 files.

The operator chose type-based eligibility, not per-species lists.

## Decision

- A role is a JSON file at `data/<namespace>/cobblemon_dynamics/roles/<id>.json`. It loads on server
  start and on `/reload`.
- A role file holds:
  - A `schema` version.
  - Eligibility: `types`. A Pokémon qualifies when its primary or secondary type is in the list. There
    are no per-species lists.
  - A `handler` id and its `params`.
  - `auto_assign`, `priority` and `range`. When one worker fits two roles on the same target, the role
    with the higher `priority` wins. For example, the Superheater outranks the Burner Keeper.
  - An `effect`: animation names, actor particles, target particles, the idle ticks after each animation
    and the loop interval of a worker whose model has none of the animations.
- Handlers are Kotlin classes registered by id. Create handlers register only when Create is loaded.
  A role whose handler is missing does not load, and the mod logs one warning.
- **Role-specific numbers live in the role file:**
  - the Generator's FE base, maximum, rarity multiplier and rarity labels
  - the Pump rate
  - the Muse formula
  - the Telekinesis and Magnet rates
  - the Tailwind formula
  - the Superheater's level gate
  - effect ids and ranges.
- **Engine-wide numbers live in the server config.** Examples: the work area, scan budgets, timeouts,
  Work Stamina constants, bed speed factors and heal constants.
- No number lives in both places. No hard cap applies beyond the configured values. A pack that raises
  a role's numbers owns that balance choice.
- An invalid role file logs one error that names the file and the field, and the mod skips that role.
- After a reload, the engine checks every current claim and binding against the new roles. A claim
  whose role no longer fits is released.

## Consequences

- Pack makers can change rates, effects and eligible types without code.
- The JSON format is a public interface. A change to it needs a new `schema` number and a migration
  note. The format becomes public with the first public release. Until then, schema 1 is the format
  that release ships. The play-test changes of 2026-11-10 (`idle_ticks`, and no `cry` or `sound`
  animation) stay in schema 1.
- The built-in roles double as the reference examples for pack makers.
- Server owners and pack makers each have one place to tune, so they do not fight over one number.
- The built-in numbers follow the balance rule ([ADR-0010](0010-balance-rule.md)). A pack can break that
  rule on purpose.

## Alternatives considered

- **Hard-coded roles.** Less code, but no tuning without a new release.
- **Per-species skill lists** (Cobblebase). Detailed, but about 1,367 files to maintain. The operator
  chose types.
- **Role numbers in the server config.** Easy for server owners, but data packs could not ship
  balanced role sets.
