# ADR-0022: Terrain-smart surface spots, voxel sight, size reach, and zone tether

Status: Accepted (2026-11-28) by the approver (R97). Input R97 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) holds the rulings, and Task 2 of the
[2.0 plan](../plans/2026-11-28-v2.0-plan.md) builds them.

## Context

Field play on decorated bases exposed four navigation and spot-selection defects in 1.2.2:

1. **Thin and partial floors.** `LevelSpotGrid.canStand` required `isFaceSturdy(level, below, Direction.UP)` and empty
   collision shapes in `y` and `y + 1`. Carpets (`1/16`), moss carpets, snow layers, Cobblemon 1.8 placeable item blocks,
   farmland and dirt paths (`15/16`), mud and soul sand (`14/16`), bottom slabs, and leaf blocks (`LeavesBlock`) either
   occupy `y` with a thin collision box or have a non-sturdy top face at `y - 1`, so workers refused valid floors around
   chests, beds, and gardens.
2. **Whole-cell sight blocking.** `LevelSpotGrid.blocksSight` returned `true` whenever a traversed cell's collision
   shape was non-empty. A 1-block ring of carpet around a chest or furnace sits in the floor cell beside the target and
   blocked `WorkSpots.hasLineOfSight` when the ray grazed that cell.
3. **Wide evolutions and Alphas.** Wide species and Alpha Cobblemon had the same 2-block or 6-block role reach as tiny
   first-stage species, forcing large bodies into tight machine corners where `fitsBody` or pathing rejected spots.
4. **Idle drift, unintended wander gaps, and rigid timeouts.** While Work Mode was on, idle workers fell back to Cobblemon's
   ambient Brain tasks: `pokemon_follows_owner` pulled workers toward a nearby owner and Cobblemon's pasture wander let them
   drift up to `±32` blocks away. Worse, workers unintentionally slacked off when work existed: residual `minecraft:idle`
   wander tasks could keep a stale `WALK_TARGET` active across a Brain activity switch, and `releasesWhenDone` area roles
   (`Cultivator`, `Harvester`, `Picker`, `Freezer`) dropped into `Idle` for up to `scanIntervalTicks` (`40` ticks) after each
   crop or cell, starting random wanders between back-to-back targets. `PastureAdapter.tethered` also searched only the
   pasture's roam AABB, missing workers that drifted outside it; returning from beyond `32` blocks exceeded `FOLLOW_RANGE`;
   and a fixed `600`-tick travel timeout with an immediate `6,000`-tick skip locked out slow or distant workers for 5 minutes
   on their first delay.

## Decision

1. **`SurfaceGrid` and `supportTopY`.** `SurfaceGrid` / `LevelSpotGrid` resolves the walkable support surface
   `supportTopY` of column `(x, y, z)`:
   - Full blocks and sturdy floors at `(x, y - 1, z)` with empty `(x, y, z)` give `supportTopY = y.toDouble()`.
   - Partial-height support blocks at `(x, y - 1, z)` — farmland and dirt paths (`15/16`), mud and soul sand (`14/16`),
     bottom slabs (`0.5`), and `LeavesBlock` — give `supportTopY = (y - 1) + shape.max(Axis.Y)`.
   - Thin walkable blocks occupying `(x, y, z)` over a supporting block at `(x, y - 1, z)` — carpets (`1/16`), moss
     carpets, snow layers (up to `8/16`), bottom slabs, and Cobblemon 1.8 placeable item blocks whose collision top in
     `(x, y, z)` is at most `0.5` — give `supportTopY = y + shape.max(Axis.Y)`.
   - A column is standable only when `supportTopY` has no fluid or hazard at the feet and `WorkerBody` has clear headroom
     of `bodyHeight` from `supportTopY + 0.001` upward. `WorkerBody.boxes`, `Arrival.isAt`, and the arrival snap use
     `supportTopY + 0.001`, matching `OmniPathNodeMaker`'s node height.
2. **Voxel-shape line of sight.** `SpotGrid` exposes `clipSight(x, y, z, from, to): Boolean` (defaulting to
   `blocksSight` on unit-test grids without shapes). In `LevelSpotGrid`, a non-target cell blocks sight only when
   `state.getCollisionShape(level, pos).clip(from, to, pos) != null`. Thin floor shapes such as carpets (`y` up to
   `0.0625`) never intersect a sight ray between a worker's spot center and a chest or furnace center.
3. **Size and Alpha reach bonus.** Effective interaction reach for spot selection (`WorkSpots.find`) and range checks
   adds `Reach.sizeBonus(bbWidth, isAlpha) = max(0.0, (bbWidth - 1.0) * 0.85 + (if (isAlpha) 1.0 else 0.0))` to the
   role's configured `range`. Narrow workers (`bbWidth <= 1.0`, non-Alpha) keep the exact role range; wide evolutions
   and Alphas claim and work from open aisles.
4. **Work-area zone tether, back-to-back chaining, `entityId` lookup, and waypoint stepping.**
   - While Work Mode is on, workers in `Idle`, `RestingInPlace`, or waiting states stay under Dynamics' Brain control
     within `workArea` expanded by `2` blocks horizontally and vertically, suppressing Cobblemon's
     `pokemon_follows_owner` pull and `±32`-block random roam drift. Pastures with Work Mode off keep Cobblemon's
     unaltered roam and follow tasks.
   - `BrainBridge.enterWork` erases any residual `MemoryModuleType.WALK_TARGET` and stops active `navigation` immediately
     after `brain.stopAll(level, entity)` so a lingering `minecraft:idle` wander task never overwrites the new work walk
     target. When a pasture worker releases a completed `releasesWhenDone` claim (`Cultivator`, `Harvester`, `Picker`,
     `Freezer`, with `skip == null`), the engine pauses wander (`BrainBridge.pauseWander`) and schedules an immediate
     pasture scan for the next tick so back-to-back ready targets chain without an idle wander gap.
   - `PastureAdapter.tethered` checks `level.getEntity(tethering.entityId)` first when it is a live `PokemonEntity`
     matching `tethering.pokemonId` and `tethering.tetheringId`, falling back to the roam-box AABB scan only when the id
     is absent or stale.
   - When a worker's walk target (work spot, bed, or `workArea` return point) is farther than `32.0` blocks (or the
     entity's `FOLLOW_RANGE`), the Brain walk order steps to an intermediate standable waypoint `16` to `24` blocks
     toward the goal until the goal lies within `FOLLOW_RANGE`.
5. **Distance-scaled travel timeout and progressive retry backoff.**
   - Effective walk speed is `effSpeed = if (speed.isFinite() && speed > 0.0) speed.coerceAtLeast(0.005) else 0.10`.
     Travel timeout for `Traveling` and `ToBed` is
     `max(settings.travelTimeoutTicks, ceil(distance / effSpeed * 2.5).toInt()).coerceAtMost(6000)` ticks (minimum `600`
     ticks with default config, `3,334` ticks for a `0.012`-blocks/tick Torterra crossing `16.0` blocks, and capped at
     `6,000` ticks).
   - Consecutive travel timeouts or `NO_SPOT` misses on one `(worker, target)` key back off progressively at `200`,
     `600`, and `2,400` ticks (clamped to `settings.retryWaitTicks` when configured lower), resetting to the first step
     whenever the worker arrives at that target.

## Consequences

- Decorated bases with carpets, paths, slabs, leaves, snow layers, and Cobblemon placeables work without hidden spot or
  line-of-sight failures.
- Wide final evolutions and Alpha Cobblemon stand cleanly in aisles instead of failing corner checks.
- Workers remain visibly inside their pasture's work zone and recover quickly from temporary path obstructions.

## Alternatives considered

- **Keep whole-block sight checks and whitelist `CarpetBlock` only.** Rejected: moss carpets, snow layers, slabs, trapdoors,
  and Cobblemon placeable blocks share the same geometry; `VoxelShape.clip` solves every partial shape uniformly.
- **Teleport drifted idle workers straight to the pasture block.** Rejected: intermediate `FOLLOW_RANGE` waypoints walk
  workers home naturally without popping entities across a base.
