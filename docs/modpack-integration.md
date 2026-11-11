# Modpack integration

This page is for maintainers who add Cobblemon Dynamics to a modpack or a server. [ARCHITECTURE.md](../ARCHITECTURE.md)
holds the full design. This page lists what a pack maintainer must know.

## Install

Install the jar on the server and on every client. Both sides need it, because the mod adds blocks, items, a creative
tab and network payloads. The client also draws a button and bars in Cobblemon's pasture panel, and bars and outlines in
the world. A client without the mod cannot join a server that has it.

NeoForge checks these dependencies when the game loads:

| Mod | Required | Version range |
| --- | --- | --- |
| NeoForge | yes | `[21.1.219,)` |
| Minecraft | yes | `[1.21.1,1.21.2)` |
| Kotlin for Forge | yes | `[5.10,)` |
| Cobblemon | yes | `[1.8.4,1.9)` |
| Create | no | `[6.0.10,6.1)` |
| Applied Energistics 2 | no | `[19.2,20)` |
| Cobbleworkers | no | any |
| Cobblebase | no | any |

Without Create, the roles that need it turn off: the Wheel Driver, the Cranker, the Superheater, the Fan Catalyst and
Tailwind. Each of their role files logs one line, and the other roles work as before. Without AE2, no role turns off. The
mod uses no AE2 class: it reaches AE2 blocks through NeoForge's energy and item capabilities and the block tag
`#c:budding_blocks`.

## Defaults on a shared server

- **Work Mode is off.** A pasture works only after its owner or an operator turns Work Mode on in the pasture panel. The
  Pokémon of a pasture with Work Mode off do no work for this mod, and the pasture keeps its Cobbleworkers and Cobblebase
  jobs.
- **Quick Work is on.** A player who sends out a party Pokémon with R while aiming at a machine gives it a work order on
  that machine. The order ends for good when the owner goes more than `quickWork.leashRange` (16 blocks) from the
  machine, or for another reason that [the player guide](player-guide.md#when-quick-work-stops) lists. Before an order
  starts, the server asks `ServerPlayer.mayInteract`, which checks spawn protection and the world border. A spectator
  gets no order.
- **Claim mods do not see Quick Work.** Quick Work makes no block click, because the server traces the player's view.
  Claim mods that only cancel block clicks, such as FTB Chunks and Open Parties and Claims, therefore do not stop it. If
  players must not run machines in other players' claims, set `quickWork.enabled = false` in the server config.
- **Work is silent.** Workers and healers play no sounds and no cries.

## Other mods

- **Cobbleworkers.** At each poll of a Work Mode pasture, the mod sets `cobbleworkers.resting` in the `persistentData` of
  each of its Pokémon, so Cobbleworkers gives them no job. When a Pokémon leaves Work Mode, the mod clears the flag only
  if the mod set it. `compat.cobbleworkers = false` in the server config turns this off.
- **Cobblebase.** Four mixins on Cobblebase's helper methods stop its jobs, idle behaviours, water moves and passive
  experience for a Work Mode pasture and its Pokémon. If a hook does not apply, the mod logs one warning that names it.
  `compat.cobblebase = false` in the server config turns this off.
- **Both.** Pastures with Work Mode off keep their Cobbleworkers and Cobblebase jobs. See
  [ADR-0009](adr/0009-coexistence-with-pasture-job-mods.md).

## Mixins

| Applies | Targets |
| --- | --- |
| Always | `EnchantmentMenu`, `LivingEntity` |
| With Create | `AirCurrent`, `BlazeBurnerBlockEntity`, `ItemUseOverrides`, `WaterWheelBlockEntity`, `WindmillBearingBlockEntity` |
| With Cobblebase | `AmbientBehavior`, `BaseManager`, `NavigationHelper`, `PassiveXp` |
| Client only | Cobblemon's `PastureWidget` and `PasturePokemonScrollList` |

A config plugin skips the Create and Cobblebase mixins when their mod is absent. It also skips a mixin whose target class
lacks a field or method that the mixin needs. A mixin that does not apply turns off only the part that it serves, with
one warning: a role, the Work Leash on Create's hand crank, the worker push, a Cobblebase hook or a part of the pasture
panel. So a renamed field in a later Create 6.0.x release turns a role off and does not crash the game.

## Network payloads

All payloads use the namespace `cobblemon_dynamics`.

| Direction | Payloads |
| --- | --- |
| To the client | `pasture_panel_state`, `leash_overview` |
| To the server | `work_mode_toggle`, `pasture_panel_open`, `pasture_panel_close`, `leash_overview_request` |

The server checks each request against its own state: the sender's permission, distance, dimension and ownership, as
the request needs them. Rate limits apply to each player. Quick Work adds no payload.

## Files and saved data

- **Config.** `config/cobblemon_dynamics-server.toml` and `config/cobblemon_dynamics-client.toml`. NeoForge writes each
  file at the first start. A copy of the server config in a world's `serverconfig/` folder overrides the copy in
  `config/` for that world.
- **Saved state.** The mod saves its state in these places:
  - each Pokémon's `persistentData`: its Work Stamina, its leash binding, and a mark on each Cobbleworkers flag that the
    mod set
  - a data attachment on each pasture block entity: its Work Mode
  - data attachments on each level: the index of Work Mode pastures and the index of leash bindings
  - the Work Leash item: the selected Pokémon and a pending Telekinesis source
  - Create block entities: the drive direction of water wheels and hand cranks, and the fire mode of encased fans
  - the Charge Cell: its energy, which the item of a broken cell keeps

  Section 4 of [ARCHITECTURE.md](../ARCHITECTURE.md) lists every entry. Claims, worker states and Quick Work orders are
  never saved. Quick Work saves nothing except Work Stamina.
- **Data pack content.** The mod adds these:
  - 16 role files under `data/cobblemon_dynamics/cobblemon_dynamics/roles/`
  - recipes for the Work Leash, the four beds and the Charge Cell, and loot tables for the beds and the Charge Cell
  - one Cobblemon behaviour, `data/cobblemon_dynamics/behaviours/pokemon/auto/work.json`, which adds the work tasks to the
    Brain of every Pokémon
  - the block tag `minecraft:mineable/pickaxe`, for the Charge Cell

  A data pack can override any role file. `roles.disabled` in the server config turns a role off.

## What is tested

- **Tested on every push and pull request to `main`.** CI runs the build with its unit tests and jar checks. GameTests
  then run on a GameTest server, once with Create and AE2 and once without them, followed by a perf run. The release
  workflow runs the build, the unit tests and the jar checks again for each tag.
- **Tested at each phase gate.** The isolated test pack runs clean dedicated servers with only this mod and its pinned
  dependencies. It checks the dependency ranges, including the NeoForge floor, plus servers with only one of Cobbleworkers
  and Cobblebase, `/reload` and restarts.
- **Not tested automatically:**
  - Play with two or more players. The approver ruled out multiplayer GameTests, so an audit checks the code instead,
    in [the multiplayer audit](testing/multiplayer-audit.md).
  - Client rendering, which the approver's play-test checks.
  - Mods that this page does not name.
