# ADR-0024: Dark Sentry, Campfire Pot Stoker, Pasture Shelf, and Jade

Status: Accepted (2026-11-28) by the approver (R97). Input R97 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) holds the rulings, and Tasks 4 and 5 of the
[2.0 plan](../plans/2026-11-28-v2.0-plan.md) build them.

## Context

Three gaps remained after 1.2.2:

1. **The eighteenth type and Cobblemon's Campfire Pot.** Seventeen of the eighteen elemental types had roles or healing,
   leaving `Dark` as the sole type with no role (the 1.0 non-goal). Meanwhile, Cobblemon 1.8's Campfire Pot
   (`CampfireBlock` / `CampfireBlockEntity`) cooks apricorn and pot recipes over fire, yet Fire-type `Stoker` workers
   ignored it.
2. **Pasture controls and work-area visibility.** Cobblemon's pasture panel had room for only one narrow `Work Mode`
   button above `Recall`. Players had no per-pasture horizontal radius control, no `Access Lock` toggle, and no way to
   see a pasture's 3D `workArea` boundaries in the world while placing machines or crops.
3. **Opaque idle workers.** When a worker stood `Idle` because its level was too low, every target was claimed, a land
   claim refused the chunk, or its role needed a Work Leash, both the pasture panel and the head-bar showed only `Idle`
   with no explanation, and players running **Jade** saw no Dynamics state when looking at workers, machines, or beds.

On 2026-11-28 (R97), the approver approved the Dark-type `Sentry` area role, Dark-type Nocturnal stamina efficiency,
`Stoker` support for Cobblemon's Campfire Pot, a minimal fold-out **Pasture Shelf** beside the pasture screen (ruling out
new trough, workstation, or job-board blocks), a persistent 3D `workArea` wireframe, synced `IdleReason` and trait
badges, and optional **Jade** (`15.10.6`) tooltips.

## Decision

1. **Dark-type `Sentry` (`sentry.json`).**
   - `sentry.json` registers handler `cobblemon_dynamics:sentry` for type `dark`, `auto_assign: true`, `quick_work:
     false`, `leash: false`, `priority: 0`, `range: 6.0`, and the silent move effect `snarl`.
   - `SentryHandler` scans `ScanKind.BLOCK_ENTITY` and matches the worker's own `PokemonPastureBlockEntity` (`pos ==
     ctx.pasturePos`), claiming it exclusively so at most one `Sentry` drains Work Stamina per pasture at a time while
     any second Dark-type stays rested to rotate in. No block-state iteration runs (`CD-PERF-03`).
   - While at least one `Sentry` is `Working` on a pasture, `SentryWatch` holds that pasture's `(dimension, workArea)` in
     memory and listens to NeoForge's `FinalizeSpawnEvent`. When `event.spawnType == MobSpawnType.NATURAL`,
     `event.entity.type.category == MobCategory.MONSTER`, `event.entity !is PokemonEntity`, and the spawn position lies
     inside an active `workArea`, the listener calls `event.isSpawnCancelled = true`. Spawners (`SPAWNER`,
     `TRIAL_SPAWNER`), raids (`EVENT`), structure spawns (`STRUCTURE`), patrols, summons, spawn eggs, non-monster mobs,
     and all Cobblemon spawns are untouched. Stopping `Working` removes the pasture's watch within 1 tick.
2. **Nocturnal stamina rule.** Any Working worker that has `ElementalTypes.DARK` (primary or secondary type, in `Sentry`
   or any other role) multiplies its Work Stamina drain by `0.5` (`50%` less drain) while `level.isNight` is `true`.
3. **Campfire Pot Stoker.** `FurnaceHandler` (`cobblemon_dynamics:furnace`, used by the existing `stoker.json` with no new
   role file) matches Cobblemon's `com.cobblemon.mod.common.block.campfirepot.CampfireBlock` (`CampfireBlockEntity`),
   excluding `#cobblemon_dynamics:stoker/never`. It reports demand only while a recipe can cook or is actively cooking
   with the pot lid closed (`IS_LID_OPEN_INDEX == 0` and `cookingTotalTime > 0` on `dataAccess`, with either
   `CampfireBlock.COOKING == true && 0 < cookingProgress < cookingTotalTime` or `cookingProgress == 0` with a valid pot
   recipe whose output fits in `RESULT_SLOT`, preventing a 20-tick `Standby` drop between consecutive dishes). On each
   Working tick it keeps `LIT = true` where that property is present and advances `cookingProgress`
   by `+CampfireBlockEntity.COOKING_PROGRESS_PER_TICK` (`+2`, aligned to multiples of `2` and clamped to
   `cookingTotalTime - COOKING_PROGRESS_PER_TICK` = `198` so `CampfireBlockEntity.serverTick`'s exact-equality check
   `cookingProgress == cookingTotalTime` finishes the craft in half the ticks) through the pot's public `ContainerData`
   (`getDataAccess()`, indices `COOKING_PROGRESS_INDEX`, `COOKING_PROGRESS_TOTAL_TIME_INDEX`, and `IS_LID_OPEN_INDEX`),
   using zero server-side Cobblemon mixins.
4. **Pasture Shelf UI and per-pasture radius.**
   - Opening Cobblemon's `PCGUI` with `PasturePCGUIConfiguration` attaches a fold-out `PastureShelf` widget immediately
     to the right of the pasture panel (keeping `WorkModeButton` or folding it cleanly into the shelf without overlapping
     Cobblemon's widgets). The shelf holds four controls: `Work Mode` (`On`/`Off`), horizontal `Work Radius` stepper
     (`4, 8, 12, 16`, clamped to `workArea.maxHorizontalRadius`, default `16`), `Show Work Area` (client toggle), and
     `Access Lock` (`Unlocked`/`Locked`).
   - `WorkModeData` persists `enabled: Boolean`, `locked: Boolean` (default `false`), and `radius: Optional<Int>` on
     `PokemonPastureBlockEntity`. Server config adds `workArea.maxHorizontalRadius` (`4..32`, default `16`).
5. **Persistent 3D `workArea` wireframe and post-close watch sync.**
   - Closing `PCGUI` removes the player from `PanelViewers`, so the wireframe uses its own lightweight watch channel:
     toggling `Show Work Area` sends `AreaWatchPayload(pos, watching: Boolean)` to the server.
   - `AreaWatchers` tracks watching players per `(dimension, pasturePos)` (cleared on player disconnect or dimension
     change) and pushes `AreaBoundsPayload(pos, active: Boolean, minX, minY, minZ, maxX, maxY, maxZ)` immediately on
     watch registration, whenever `WorkModeService` changes `enabled` or `radius`, and with `active = false` whenever Work
     Mode turns off or the pasture leaves `WorkPastureIndex` / breaks.
   - `WorkAreaOverlay` draws active watched boxes in `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS` relative to
     the camera across `PCGUI` close, and also drops any watched `pos` in a loaded client chunk whose block is no longer a
     Cobblemon `PastureBlock`, or when the client changes level or disconnects.
6. **Synced `IdleReason`, trait badges, and Jade (`15.10.6`).**
   - `IdleReason` (`NONE`, `NO_TARGET`, `NEEDS_LEASH`, `TARGET_TAKEN`, `CANNOT_REACH`, `CLAIM_DENIED`, `LEVEL_TOO_LOW`,
     `RESTING`), `WorkerStatus.SLACKING` (`"Slacking Off..."`), and the 5-bit active trait mask (`SHINY`, `ALPHA`,
     `MAJESTIC`, `MASTERY`, `MATE`) sync in `StaminaView` and `PanelEntry` (`payload version 4`), rendering localized
     lines/badges in pasture slot tooltips, Pasture Shelf views, and Work Leash head-bars while sharing
     `StateIcons.STANDBY` for `SLACKING` (matching `TO_BED` and `RESTING_IN_PLACE` sharing `StateIcons.TIRED` so the
     existing seven `16 × 16` state icons stay unchanged).
   - `compat/jade/` provides an optional `@WailaPlugin` compiled against Jade `15.10.6` (`compileOnly`, optional in
     `neoforge.mods.toml`, listed in `OptionalMods` and `verifyJar`) that shows worker role, state, stamina,
     `IdleReason`, traits, and multiplier on workers, assigned worker and role on worked machines, and bed tier factor and
     sleeper on beds, loading zero Jade classes when Jade is absent.

## Consequences

- Every one of the 18 Pokémon types now has a role in Cobblemon Dynamics, and Dark-types excel on night shifts.
- Players can size each pasture's horizontal footprint, visualize its exact 3D box while building, lock controls on
  public servers, and diagnose why any worker is idle at a glance in the pasture screen, with the Work Leash, or in Jade.

## Alternatives considered

- **Add physical Job Board or Feed Trough blocks.** Rejected by R97: extra control blocks clutter bases when a fold-out
  shelf on the existing pasture screen holds every setting in one place.
- **Server-side Mixin into `CampfireBlockEntity`.** Rejected by R97: Cobblemon's public `ContainerData` (`dataAccess`)
  and block state properties expose cooking progress, total time, and lit/lid state without fragile bytecode hooks.
