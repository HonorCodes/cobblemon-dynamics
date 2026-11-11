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
| FTB Chunks (`ftbchunks`) | no | any |
| Open Parties and Claims (`openpartiesandclaims`) | no | any |

Without Create, the roles that need it turn off: the Wheel Driver, the Cranker, the Superheater, the Fan Catalyst and
Tailwind. Each of their role files logs one line, and the other roles work as before. Without AE2, no role turns off. The
mod uses no AE2 class: it reaches AE2 blocks through NeoForge's energy and item capabilities and the block tag
`#c:budding_blocks`.

## Defaults on a shared server

- **Work Mode is off.** A pasture works only after its owner or an operator turns Work Mode on in the pasture panel. The
  Cobblemon of a pasture with Work Mode off do no work for this mod, and the pasture keeps its Cobbleworkers and Cobblebase
  jobs.
- **Quick Work is on.** A player who sends out a party Cobblemon with R while aiming at a machine gives it a work order on
  that machine. The order ends for good when the owner goes more than `quickWork.leashRange` (16 blocks) from the
  machine, or for another reason that [the player guide](player-guide.md#when-quick-work-stops) lists. Before an order
  starts, the server asks `ServerPlayer.mayInteract`, which checks spawn protection and the world border. A spectator
  gets no order.
- **Quick Work respects the claims of FTB Chunks and Open Parties and Claims.** Before an order starts, the server asks
  each of the two claim mods that is installed whether the owner may right-click the machine with an empty hand. It asks
  again every 20 ticks while the order runs, and it ends the order when the answer becomes no. The claim mod's own rules
  decide, so the members of the claim's team or party can use its machines, and its allies can where its rules allow
  them. [Claim mods](#claim-mods) gives the details.
- **Other claim mods do not see Quick Work.** Quick Work makes no block click, because the server traces the player's
  view. A claim mod that only cancels block clicks therefore does not stop it. On a server with such a mod, set
  `quickWork.enabled = false` in the server config if players must not run machines in other players' claims.
- **Work is silent.** Workers and healers play no sounds and no cries.

## Claim mods

FTB Chunks and Open Parties and Claims are optional. `neoforge.mods.toml` lists both as optional dependencies with any
version, as it lists Cobbleworkers and Cobblebase, so NeoForge checks no version of them. The checks are built against
these versions. No automated test has run them against the real claim mods yet, because the approver deferred those tests
on 2026-11-11.

| Mod | Tested version |
| --- | --- |
| FTB Chunks | 2101.1.23, with FTB Teams 2101.1.12, FTB Library 2101.1.39 and Architectury API 13.0.11, the versions of COHO 2.4.0 |
| Open Parties and Claims | 0.33.0 for NeoForge 1.21.1 |

Another version works as long as it keeps the calls that
[Amendment 1 of ADR-0013](adr/0013-quick-work-for-party-pokemon.md#amendment-1-land-claims-2026-11-11) names.

**When a check fails.** A check that throws an error counts as a refusal. If a call is gone or has changed, every check of
that claim mod throws, so the server refuses every Quick Work order, also outside claims. The log holds one warning for
each claim mod and each kind of error, in this form:

```text
Quick Work refuses orders while the claim check of ftbchunks fails: java.lang.NoSuchMethodError: ...
```

The warning means that this build of Cobblemon Dynamics cannot read the installed claim mod. Install a tested version of
the claim mod, or a release of Cobblemon Dynamics that supports the installed one. Until then, the sent-out Cobblemon come out
as normal, with "You may not use {target}" on the action bar, and the rest of the mod works as before. A server can also
set `quickWork.enabled = false`.

Each claim mod applies its own rules. By default, the allies of an FTB Chunks team can Quick Work in its claims, and the
allies of an Open Parties and Claims party cannot.

- **FTB Chunks.** The check is the one that FTB Chunks runs for a right-click on a block. The team's block interaction
  setting decides, with `ally_mode`, `disable_protection`, `no_wilderness` and the admin bypass of FTB Chunks. The value
  of `fake_players` does not apply, because the check asks about the owner, not about a fake player. When FTB Chunks
  refuses, it shows "Interaction prevented here by claim protection!" on the owner's action bar. In the same tick, Quick
  Work replaces that text with "You may not use {target}".
- **Open Parties and Claims.** The check is the mod's own check of a block interaction, with its messages off. The player
  config of the claim's owner decides. By default, `claims.protection.exceptions.fullAccess` gives access to the owner's
  party, and the value `A` gives access to allied parties too.
- **No switch.** No config key turns these checks off. `quickWork.enabled = false` turns off all of Quick Work.
- **Pastures do not ask claim mods.** The work area of a Work Mode pasture can reach into the claim of another player, and
  its workers then work the machines there. Keep the work areas of pastures out of other players' claims.

## Other mods

- **Cobbleworkers.** At each poll of a Work Mode pasture, the mod sets `cobbleworkers.resting` in the `persistentData` of
  each of its Cobblemon, so Cobbleworkers gives them no job. When a Cobblemon leaves Work Mode, the mod clears the flag only
  if the mod set it. `compat.cobbleworkers = false` in the server config turns this off.
- **Cobblebase.** Four mixins on Cobblebase's helper methods stop its jobs, idle behaviours, water moves and passive
  experience for a Work Mode pasture and its Cobblemon. If a hook does not apply, the mod logs one warning that names it.
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

When mod loading completes, the log holds one INFO line that starts with `Mixins:`. It names each mixin as applied,
skipped with its reason, applied with no injection, or not applied yet. On a client, the two client mixins read "not
applied yet" until a pasture panel opens.

## Network payloads

All payloads use the namespace `cobblemon_dynamics`.

| Direction | Payloads |
| --- | --- |
| To the client | `pasture_panel_state`, `leash_overview` |
| To the server | `work_mode_toggle`, `pasture_panel_open`, `pasture_panel_close`, `leash_overview_request` |

The server checks each request against its own state: the sender's permission, distance, dimension and ownership, as
the request needs them. Rate limits apply to each player. Quick Work adds no payload. When the payloads register, the log
holds one INFO line that starts with `Payloads, version 3:` and names each payload by direction.

## Files and saved data

- **Config.** `config/cobblemon_dynamics-server.toml` and `config/cobblemon_dynamics-client.toml`. NeoForge writes each
  file at the first start. A copy of the server config in a world's `serverconfig/` folder overrides the copy in
  `config/` for that world.
- **Saved state.** The mod saves its state in these places:
  - each Cobblemon's `persistentData`: its Work Stamina, its leash binding, and a mark on each Cobbleworkers flag that the
    mod set
  - a data attachment on each pasture block entity: its Work Mode
  - data attachments on each level: the index of Work Mode pastures and the index of leash bindings
  - the Work Leash item: the selected Cobblemon and a pending Telekinesis source
  - Create block entities: the drive direction of water wheels and hand cranks, and the fire mode of encased fans
  - the Charge Cell: its energy, which the item of a broken cell keeps

  Section 4 of [ARCHITECTURE.md](../ARCHITECTURE.md) lists every entry. Claims, worker states and Quick Work orders are
  never saved. Quick Work saves nothing except Work Stamina.
- **Data pack content.** The mod adds these:
  - 16 role files under `data/cobblemon_dynamics/cobblemon_dynamics/roles/`
  - recipes for the Work Leash, the four beds and the Charge Cell, and loot tables for the beds and the Charge Cell
  - one Cobblemon behaviour, `data/cobblemon_dynamics/behaviours/pokemon/auto/work.json`, which adds the work tasks to the
    Brain of every Cobblemon
  - the block tag `minecraft:mineable/pickaxe`, for the Charge Cell

  A data pack can override any role file. `roles.disabled` in the server config turns a role off. Each load of the role
  files logs one INFO line, such as `Roles: 16 loaded, 16 active, 0 disabled`, which names each disabled role with its
  reason. A config change that changes the active roles logs a new line.

## What is tested

- **Tested on every push and pull request to `main`.** CI runs the build with its unit tests and jar checks. GameTests
  then run on a GameTest server, once with Create and AE2 and once without them, followed by a perf run. The run with
  Create and AE2 also tests the claim checks of Quick Work with a test claim mod. The release workflow runs the build, the
  unit tests and the jar checks again for each tag.
- **Tested at each phase gate.** The isolated test pack runs clean dedicated servers with only this mod and its pinned
  dependencies. It checks the dependency ranges, including the NeoForge floor, plus servers with only Cobbleworkers or
  only Cobblebase, `/reload` and restarts. The servers with only FTB Chunks or only Open Parties and Claims, and two
  GameTest runs of Quick Work against the real claim mods, are planned. The approver deferred them on 2026-11-11.
- **Not tested automatically:**
  - Play with two or more players. The approver ruled out multiplayer GameTests, so an audit checks the code instead,
    in [the multiplayer audit](testing/multiplayer-audit.md).
  - Client rendering, which the approver's play-test checks.
  - Mods that this page does not name.
