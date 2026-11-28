# ADR-0023: Variant, Move Mastery, and Mate multipliers, Slacking Off, and uncapped Work XP and Friendship

Status: Accepted (2026-11-28) by the approver (R97). Input R97 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) holds the rulings, and Task 3 (with client/HUD display in Task 5) of
the [2.0 plan](../plans/2026-11-28-v2.0-plan.md) builds them.

## Context

In versions 1.0 through 1.2.2, two workers of the same type and level performed identically in every role except the
Generator, where `legendary` and `mythical` doubled FE output (CD-GEN-02). Shiny hunts, Alpha captures, `Cobblemon
Majesty` ("majestic") variants, curated movesets, Natures, and breeding pairs gave zero work benefit, and pastured
workers gained neither Cobblemon XP nor Friendship from hours of base work.

On 2026-11-28 (R97), the approver ruled a multiplicative progression layer across variants, Move Mastery, and Mate pairs,
extended `rare_multiplier` across all output- and pace-scaled roles, defined how multipliers apply to machine-capped
roles without breaking Create or modded machine caps (ADR-0010), and added passive EV-free Work XP and Friendship. On
worker wandering, Natures, beds, and Friendship, the approver also ruled verbatim:

> another note is that some workers seem to randomly wander rather than do work. I was thinking we could integrate
> something related to their Nature, which would affect how often they go into a Slacking Off... state (which we should
> implement, they are less-likely to go into that state if they have a nature that is geared towards speed/hard working,
> beds, and friendship levels) (and fix the issue where they unintentionally slack off when there is work to do)

[ADR-0022](0022-surface-spots-voxel-sight-and-zone-tether.md) eliminates the unintended wander gaps when demand exists,
while this record defines the intentional, stat-governed `Slacking` state. In the same ruling, the approver explicitly
rejected auto-depositing farm and tree drops into adjacent pasture chests so the Steel-type `Magnet` keeps its full
logistics role.

## Decision

1. **Server config `[progression]`.** `cobblemon_dynamics-server.toml` adds `[progression]`:
   - `shinyMultiplier` (`1.0` to `5.0`, default `1.15`)
   - `alphaMultiplier` (`1.0` to `5.0`, default `1.25`)
   - `majesticMultiplier` (`1.0` to `5.0`, default `1.35`)
   - `moveMasteryMultiplier` (`1.0` to `5.0`, default `1.20`)
   - `mateMultiplier` (`1.0` to `5.0`, default `1.15`)
   - `workXpEnabled` (`Boolean`, default `true`) and `friendshipEnabled` (`Boolean`, default `true`)
   - `baseSlackChance` (`0.0` to `1.0`, default `0.12`, where `0.0` disables slacking), `slackCheckTicks` (`100` to
     `72,000`, default `1,200` = `60 s`), and `slackDurationTicks` (`20` to `6,000`, default `300` = `15 s`).
2. **Trait rules and side bonuses.**
   - **Shiny (`SHINY`).** Active when `pokemon.shiny` is `true`. Multiplies by `shinyMultiplier` (`1.15` default).
   - **Alpha (`ALPHA`).** Active when `pokemon.isAlpha` is `true` or `"alpha"` is in `pokemon.aspects` or
     `pokemon.forcedAspects`. Multiplies by `alphaMultiplier` (`1.25` default), multiplies Work Stamina capacity by
     `1.25` (`+25%`), and adds `+1` extra crop, fruit, or water cell per work cycle on the area roles (`Cultivator`,
     `Harvester`, `Picker`, `Freezer`).
   - **Majestic (`MAJESTIC`).** Active when `"majestic"` is in `pokemon.aspects` or `pokemon.forcedAspects`, or
     `pokemon.features` holds an enabled `FlagSpeciesFeature` named `"majestic"`. This supports `Cobblemon Majesty` with
     zero compile-time or runtime jar dependency. Multiplies by `majesticMultiplier` (`1.35` default) and multiplies Work
     Stamina drain by `0.85` (`15%` slower drain).
   - **Move Mastery (`MASTERY`).** Active when `pokemon.moveSet` contains a damaging move (`PHYSICAL` or `SPECIAL` with
     `power >= 60.0`) whose elemental type matches one of the active role's eligible `types`, or a move named in the role
     file's optional `mastery_moves` list. Multiplies by `moveMasteryMultiplier` (`1.20` default). While Working,
     `EffectPlayer` plays that mastery move's silent Cobblemon animation and particles when available in the loaded
     Cobblemon catalog, falling back to the role's configured `effect` when absent or when the move's timeline plays a
     sound (CD-FX-09).
   - **Mate Bonus (`MATE`).** Evaluated per Work Mode pasture poll: active for a tethered worker when the same Work Mode
     pasture tethers at least one `Gender.MALE` and at least one `Gender.FEMALE` Cobblemon of the same species
     (`species.resourceIdentifier`), or of a canon gendered counterpart family (`nidoranm`/`nidorino`/`nidoking` with
     `nidoranf`/`nidorina`/`nidoqueen`, `volbeat` with `illumise`, `tauros` with `miltank`). Multiplies by
     `mateMultiplier` (`1.15` default) and raises passive Friendship gain by `×1.5` (yielding `+2` per interval). Quick
     Workers have no pasture and receive no Mate bonus.
3. **Legendary and Mythical `rare_multiplier`.** A worker carrying a label in `rare_labels` (default `legendary`,
   `mythical`) multiplies its combined multiplier by `rare_multiplier` (default `2.0`) **once** on every output- or
   pace-scaled role (`Generator`, `Pump`, `Telekinesis`, `Magnet`, `Cultivator`, `Harvester`, `Picker`, `Lumberjack`,
   `Soul Collector`, `Freezer`, `Crystal Tender`, `Heater`, and `Muse`). On `Generator` (`FeCurve` / `FePushHandler`), the
   unrounded curve value is multiplied by `rare_multiplier` (once) and by the non-rare trait product `M` **before**
   rounding once at the end, keeping a plain Lv 50 Legendary `Generator` at `433` FE/t and a plain Lv 100 Legendary
   `Generator` at `10,000` FE/t (`5,000 * 2.0`), never `20,000` FE/t.
4. **Output/pace roles vs machine-capped/fixed roles.** Let `M` be the product of all active multipliers for the worker
   on its active role:
   - **Output- and pace-scaled roles** (`Generator`, `Pump`, `Telekinesis`, `Magnet`, `Cultivator`, `Harvester`,
     `Picker`, `Lumberjack`, `Soul Collector`, `Freezer`, `Crystal Tender`, `Heater`, `Muse`): `M` scales the output per
     tick or batch (`round(base * M)` or `ceil(base * M)`) or divides the tick interval between actions (`max(1,
     round(interval / M))`). On `Muse`, vanilla enchanting power clamps at `15` bookshelves (vanilla's table cap), while
     Apothic Enchanting `Quanta` scales by `M`.
   - **Machine-capped and fixed roles** (`Stoker`, `Brew Stoker`, `Brewer`, `Wheel Driver`, `Cranker`, `Tailwind`, `Fan
     Catalyst`, `Sentry`, `Healer`): machine caps stay untouched (CD-BAL-04, CD-BAL-14), and Work Stamina drain (or heal
     stamina cost on `Healer`) divides by `M` (`drain / M`), composed multiplicatively with Majestic's `×0.85` and Dark
     Nocturnal's `×0.5`. On `Stoker` (`AbstractFurnaceBlockEntity` targets), when `M >= 1.5`, each Working tick also
     adds `+floor((M - 1.0) * 2.0)` bonus cook ticks to active `cookingProgress`, clamped to `cookingTotalTime - 1` so
     the furnace's own tick completes the smelt cleanly.
5. **Uncapped Work XP and passive Friendship.**
   - Every `1,200` Working ticks (`1` minute) accumulated on a worker while `workXpEnabled` is `true` and
     `pokemon.level < Cobblemon.config.maxPokemonLevel`, the engine awards `4 + (pokemon.level / 15)` Cobblemon XP via
     `SidemodExperienceSource("cobblemon_dynamics")` with **zero EVs** (`0` EVs in every stat).
   - Every `12,000` Working ticks (`10` minutes) accumulated while `friendshipEnabled` is `true`, the engine increments
     `pokemon.incrementFriendship` by `+1`, or by `+2` when the worker has an active Mate bonus (`ceil(1 * 1.5) = 2`) or
     its owner is online in the same level within `16.0` blocks.
6. **`Slacking` (`"Slacking Off..."`) worker state and handler lifecycle.**
   - `WorkerStatus` adds `SLACKING`, and `TargetHandler` adds `fun canSlack(ctx: WorkContext): Boolean = true`.
     `LumberjackHandler` overrides `canSlack` to return `false` while a fell is mid-run (`FellRun` active on `ctx.memo`)
     so a Lumberjack never aborts or instant-finishes a tree mid-fell to slack off.
   - While a pasture worker (never a Quick Worker) is `Working`, `handler.canSlack(ctx)` is `true`, and `baseSlackChance >
     0.0`, every `slackCheckTicks` (`1,200` Working ticks = `60 s`) the engine rolls
     `p = clamp(baseChance * natureFactor * bedFactor * friendshipFactor, 0.0, 1.0)`:
     - **`natureFactor`** from `pokemon.effectiveNature` (falling back to `pokemon.nature`):
       - **Driven (`0.35`)**: `timid`, `hasty`, `jolly`, `naive`, `hardy`, `serious` (6 natures).
       - **Steady (`0.75`)**: `adamant`, `modest`, `careful`, `calm`, `bold`, `impish`, `docile`, `bashful` (8 natures).
       - **Distracted (`1.15`)**: `lonely`, `brave`, `naughty`, `mild`, `quiet`, `rash`, `gentle`, `sassy`, `quirky` (9
         natures).
       - **Lazy (`1.60`)**: `relaxed`, `lax` (2 natures).
     - **`bedFactor`** from the highest Dynamics bed tier present in the pasture's `workArea` or last slept in by the
       worker: `No Bed = 1.00`, `Hay Bed = 0.85`, `Wool Bed = 0.70`, `Plush Bed = 0.55`, `Starlit Bed = 0.35`.
     - **`friendshipFactor`** from `pokemon.friendship`: `1.0 - 0.75 * (friendship.coerceIn(0, 255) / 255.0)` (`1.0` at
       `0` Friendship down to `0.25` at `255` Friendship).
   - On a hit, the worker transitions `WORKING -> SLACKING` for `slackDurationTicks` (`300` ticks = `15 s`). Entering
     `SLACKING` immediately calls `handler.onStop(ctx)` — so `SentryHandler` removes its `SentryWatch` entry (CD-SNT-04)
     and `WheelDriver`, `Cranker`, `FanCatalyst`, and `Superheater` stop driving their machines — while **retaining** the
     worker's `ClaimKind.WORK` claim in `ClaimRegistry` and any leash binding without blacklisting the target. During
     `SLACKING`, the engine skips `applyWork`, drains `0` Work Stamina, lets the worker wander locally inside `workArea`,
     and displays `"Slacking Off..."` in the pasture panel, Pasture Shelf, Work Leash head-bar, and Jade tooltip while
     sharing `StateIcons.STANDBY` (just as `TO_BED` and `RESTING_IN_PLACE` share `StateIcons.TIRED`, so `StateIconsTest`
     and `art.py check --release` need no new PNG assets).
   - When `slackDurationTicks` expires, the worker transitions `SLACKING -> TRAVELING` toward its retained target spot,
     revalidating `stillValid`, land claims, and `hasDemand`: if valid with demand, arrival runs `handler.onStart(ctx)`
     and enters `WORKING`; if valid without demand, arrival enters `STANDBY`; if no longer valid or claim-denied, the
     engine releases the claim cleanly.
7. **No auto-deposit into pasture chests.** Farm and tree roles keep popping drops on the ground beside the crop or trunk
   (R73, R97) so the Steel-type `Magnet` retains its dedicated collection role.

## Consequences

- Base role files and plain workers keep the exact numbers of 1.0 through 1.2.2 (`BalanceFilesTest` invariants hold for
  unboosted workers), while rare variants, trained movesets, and mated pairs stack multiplicatively.
- Natures, higher bed tiers, and high Friendship visibly cut `Slacking Off...` breaks — a Jolly worker with a Starlit Bed
  and max Friendship slacks at `0.12 * 0.35 * 0.35 * 0.25 = 0.37%` per minute, versus `19.2%` per minute for a newly
  caught Relaxed worker with no bed.
- Working Cobblemon level up slowly up to the server's configured `maxPokemonLevel` without gaining unwanted EVs, and
  gain Friendship that simultaneously unlocks Friendship evolutions and reduces slacking.

## Alternatives considered

- **Release the target claim during `Slacking`.** Rejected: releasing the claim would let another worker steal a bound or
  stoked machine for a 15-second breather and cause unnecessary musical-chairs pathing.
- **Hard compile dependency on `Cobblemon Majesty`.** Rejected: reading `"majestic"` from `pokemon.aspects`,
  `pokemon.forcedAspects`, and `FlagSpeciesFeature` works with any version of `Cobblemon Majesty` and needs no extra jar.
- **Grant battle EVs from role work, or auto-deposit drops into pasture chests.** Both rejected by R97.
