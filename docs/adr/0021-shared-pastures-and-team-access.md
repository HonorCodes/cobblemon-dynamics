# ADR-0021: Shared pastures, team access, and cooperative land claims

Status: Accepted (2026-11-28) by the approver (R97). Input R97 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) holds the rulings, and Task 1 of the
[2.0 plan](../plans/2026-11-28-v2.0-plan.md) builds them.

## Context

Through version 1.2.2, a pasture's controls and land-claim checks treated one player as the whole base:

- `WorkModePolicy.mayToggle` allowed only the pasture's placer (`PokemonPastureBlockEntity.ownerId`) and operators to
  toggle Work Mode (CD-WMD-03, CD-SEC-01).
- `LeashInteractions` allowed only `pokemon.getOwnerUUID() == player.uuid` to select, bind, or unbind a pastured
  Cobblemon (CD-LSH-01, CD-SEC-07), `FlipRules.mayFlip` allowed only `binder`, `placer`, or an operator (CD-SEC-10), and
  `LeashOverviewPayload` outlined only `allOwnedBy(player.uuid)` (CD-UI-09).
- `WorkEngine.canWork` checked `tethered.ownerId == pokemon.getOwnerUUID()`. When the owner was offline or in another
  dimension, Cobblemon's `pokemon.getOwnerUUID()` could read `null`, dropping the worker or failing `PCStore` lookups.
- `LandClaims` asked each loaded claim mod only about `runtime.placer` (CD-LND-05), so a teammate's pasture on a chunk
  owned by the worker's owner or binder was refused, and breaking roles (`Harvester`, `Lumberjack`, `Freezer`) checked
  `ownerMayBreak` only at the moment of work, after walking to the target and stalling there.
- `CD-ASG-04` barred every Cobblemon with `ElementalTypes.FAIRY` from auto-assigned roles, leaving unbound dual-type
  Fairy workers such as Azumarill, Tinkaton, and Whimsicott idle whenever every ally in the pasture had full stamina.

On multiplayer servers such as COHO, friends share one Cobblemon pasture, tether their own Cobblemon into it, and build
across chunks claimed through FTB Teams (`FTBTeamsAPI`) or Open Parties and Claims (`OpenPACServerAPI`). On 2026-11-28
(R97), the approver ruled that shared pastures are open to teammates and pastured contributors by default, with an
optional Access Lock on the Pasture Shelf, cooperative claim checks, offline-safe `tethering` ownership, and dual-type
Fairy auto-assignment when no ally needs healing.

## Decision

1. **Pasture control policy (`TEAM`).** Each pasture stores `locked: Boolean` (default `false`) in its
   `cobblemon_dynamics:work_mode` block entity attachment alongside `enabled` and the optional shelf `radius`.
   - While `locked` is `false`, a player may control the pasture — toggling Work Mode, stepping the Pasture Shelf
     horizontal radius, selecting, binding, or sneak-unbinding any Cobblemon tethered to that pasture with the Work
     Leash, flipping a driven wheel, crank, or fan of that pasture, and receiving that pasture's bound target outlines —
     when the player is the pasture's `ownerId` (`placer`), an operator (permission level 2 or higher), an FTB Teams
     teammate of `placer` (`FTBTeamsAPI`), an Open Parties and Claims party member of `placer` (`OpenPACServerAPI`), or a
     player who currently has at least one Cobblemon tethered in that pasture (`tethering.playerId == player.uuid`).
   - While `locked` is `true`, pasture controls are restricted to `placer`, FTB Teams teammates of `placer`, Open
     Parties and Claims party members of `placer`, and operators. A non-teammate player who owns a Cobblemon in a locked
     pasture may still sneak-right-click their own Cobblemon with the Work Leash to clear its binding.
   - Only `placer`, an FTB/OPAC teammate of `placer`, or an operator may change `locked`. A pasture with `ownerId == null`
     accepts control and lock changes from operators only.
2. **Optional team APIs.** Team membership checks sit behind `ModList.isLoaded("ftbteams")` and
   `ModList.isLoaded("openpartiesandclaims")` in `compat/ftbchunks/` (or `compat/ftbteams/`) and `compat/opac/`. Where
   either mod is absent, no class of it loads and two distinct UUIDs are not teammates for that mod. Any `Exception` or
   `LinkageError` from a team query returns `false` and logs one warning per cause.
3. **Offline-safe worker ownership and `LeashIndex.Ref.owner`.** `PastureAdapter` and `LeashResets` resolve a pastured
   Cobblemon's owner and `Pokemon` through `tethering.pcId` (`Cobblemon.storage.getPC(tethering.pcId)`) and
   `tethering.playerId`, never requiring `entity.pokemon.getOwnerUUID()` to be non-null. `WorkEngine.canWork` accepts a
   tethered Cobblemon when `tethering.playerId` is present and the `Pokemon` is not fainted or a battle clone, so workers
   keep working while their owner is offline or in another dimension. When any authorized controller binds a Cobblemon,
   `LeashIndex.Ref.owner` always stores the Cobblemon's PC owner (`tethering.pcId`, or `tethering.playerId` if `pcId` is
   absent), while `Binding.binder` records the player who clicked so loss notices reach the binder or owner cleanly.
4. **Cooperative `LandClaims` and pre-claim break checks.** For every pasture land-claim question (`ownerMayUse` and
   `ownerMayBreak`), `LandClaims` evaluates the non-null set `{placer, pokemonOwner, binder}` (deduplicated) and allows
   the action when at least one UUID in that set passes every loaded claim mod (`FTB Chunks`, `Open Parties and Claims`,
   `MineColonies`). When all three UUIDs are null, the ownerless-pasture rule of CD-LND-05 applies. Before `Assigner` or
   `claimBound` grants a work claim to a breaking role (`Harvester`, `Lumberjack`, `Freezer`), it checks
   `LandClaims.ownerMayBreak` on the target cell using those actors and skips a refused target for `6,000` ticks, so a
   worker never walks to a crop, tree, or water cell only to stall at work time.
5. **Unbound dual-type Fairy workers.** An unbound worker with `ElementalTypes.FAIRY` and a second type acts as a healer
   first whenever a non-Fairy ally in the same pasture qualifies for a heal (Tired, or below `heal.lowStaminaThreshold`)
   and the Fairy worker has at least `heal.cost` (`25%`) Work Stamina. Whenever no ally in the pasture needs a heal (or
   the Fairy worker is below `25%` stamina and not `Tired`), the engine offers it auto-assign roles of its non-Fairy
   type. At the next scan pass after an ally needs healing while that worker holds an auto-assigned work claim with at
   least `25%` stamina, the engine releases the auto work claim and assigns the heal claim. Pure Fairy workers keep no
   auto-assign machine role.

## Consequences

- Friends who drop Cobblemon into a shared pasture can manage Work Mode, radius, and leash assignments together with
  zero team setup, while base owners on public servers can flip `Access Lock` on to restrict controls to their party.
- Offline players and cross-dimension explorers no longer stall their pastured workforce or lose leash index lookups.
- Breaking workers skip edit-protected chunks before pathing instead of walking back and forth to refused blocks.
- Dual-type Fairy Cobblemon contribute non-Fairy auto work between heals without giving up healing priority.

## Alternatives considered

- **Require FTB Teams or OPAC for any shared pasture control.** Rejected by R97: small friend servers often run neither
  party mod, so tethering a Cobblemon in an unlocked pasture grants control by default, and `Access Lock` tightens it.
- **Require all of `placer`, `pokemonOwner`, and `binder` to pass `LandClaims`.** Rejected by R97: in a cooperative base
  where player A owns the chunk and player B owns the pasture or Cobblemon, requiring every UUID to hold chunk rights
  blocks legitimate shared pastures; an OR-union across the pasture's stakeholders matches cooperative play while still
  blocking strangers outside all three actors' claims.
