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
| MineColonies (`minecolonies`) | no | any |
| Apothic Enchanting (`apothic_enchanting`) | no | any |

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
- **Quick Work and pastures respect the land claims of FTB Chunks, Open Parties and Claims and MineColonies.** Before a
  Quick Work order starts, the server asks each installed claim mod whether the owner may right-click the machine with an
  empty hand. It asks again every 20 ticks while the order runs, and it ends the order when the answer becomes no. The
  workers of a Work Mode pasture ask about the pasture's owner, the player who placed the pasture block, also while that
  player is offline. Each claim mod's own rules decide, so the members of a claim's team, party or colony can use its
  machines, and its allies can where its rules allow them. [Claim mods](#claim-mods) gives the details.
- **Other claim mods do not see Quick Work or the pastures.** Neither makes a block click. Quick Work traces the player's
  view on the server, and a pasture works its machines directly. A claim mod that only cancels block clicks therefore does
  not stop them. On a server with such a mod, set `quickWork.enabled = false` in the server config, and keep the work
  areas of pastures out of other players' claims.
- **Work is silent.** Workers and healers play no sounds and no cries.

## Claim mods

FTB Chunks, Open Parties and Claims and MineColonies are optional. `neoforge.mods.toml` lists each as an optional
dependency with any version, as it lists Cobbleworkers and Cobblebase, so NeoForge checks no version of them. Three
GameTest runs test the checks against these versions, each with one claim mod: `runGameTestServerFtbChunks`,
`runGameTestServerOpac` and `runGameTestServerMineColonies`. CI does not run them. A pull request that touches the claims
code runs them, and so does each release gate. The test pack also starts a dedicated server with each claim mod.

| Mod | Tested version |
| --- | --- |
| FTB Chunks | FTB Chunks 2101.1.23, with FTB Teams 2101.1.12, FTB Library 2101.1.39 and Architectury API 13.0.11, the versions of COHO 2.4.0 |
| Open Parties and Claims | Open Parties and Claims 0.33.0 for NeoForge 1.21.1 |
| MineColonies | MineColonies 1.1.1419-1.21.1-snapshot, with Structurize 1.0.839-1.21.1-snapshot, BlockUI 1.0.212-1.21.1-snapshot and Domum Ornamentum 1.0.237-snapshot, the versions of COHO 2.4.0 |

Another version works as long as it keeps the calls that
[Amendment 1 of ADR-0013](adr/0013-quick-work-for-party-pokemon.md#amendment-1-land-claims-2026-11-11) and
[ADR-0014](adr/0014-land-claims-for-work-mode-pastures.md) name. FTB Chunks has no check by UUID, so the pasture check of
FTB Chunks copies the steps of its player check. A later FTB Chunks that changes those steps makes the copy wrong without
an error. Run `runGameTestServerFtbChunks` with a new FTB Chunks before a pack ships it.

**Whose rights decide.** Quick Work asks about the owner of the order, the online player who sent the Cobblemon out. A Work
Mode pasture asks about its owner, the player who placed the pasture block, by UUID. That player gets the same answer
online and offline. A pasture with no owner works no block in a land claim.

**What the pastures check.** The work engine asks the pasture check of each claim mod at three points:

- after each scan pass, for each target that the pass found, before it offers the targets to the workers
- on each poll of the pasture, every 20 ticks, for each claim, bound target and Telekinesis destination of its workers
- in the choice of a bed, for each bed of the work area

A refused target gets no worker, and a worker gets no message for it. A claim on a machine or a bed that the owner may no
longer use ends within 20 ticks. A Work Leash binding on such a block clears, and the binder gets a chat message with the
reason "the owner of its pasture may not use its target" (or "its destination"). The pasture checks send no message and
count no attempt in a claim mod.

**When a check fails.** A check that throws an error counts as a refusal. If a call is gone or has changed, every check of
that claim mod throws. The server then refuses every Quick Work order, and the pastures work no target, also outside
claims. The log holds one warning for each asker, each claim mod and each kind of error, in this form:

```text
Quick Work refuses orders while the claim check of ftbchunks fails: java.lang.NoSuchMethodError: ...
Pastures work no target while the claim check of ftbchunks fails: java.lang.NoSuchMethodError: ...
```

The warning means that this build of Cobblemon Dynamics cannot read the installed claim mod. Install a tested version of
the claim mod, or a release of Cobblemon Dynamics that supports the installed one. Until then, the sent-out Cobblemon come out
as normal, with "You may not use {target}" on the action bar, the workers of pastures stay idle, and the rest of the mod
works as before. A server can also set `quickWork.enabled = false`.

Each claim mod applies its own rules. By default, the allies of an FTB Chunks team can use the machines in its claims, and
the allies of an Open Parties and Claims party cannot.

- **FTB Chunks.** The check of Quick Work is the one that FTB Chunks runs for a right-click on a block. The team's block interaction
  setting decides, with `ally_mode`, `disable_protection`, `no_wilderness` and the admin bypass of FTB Chunks. The value
  of `fake_players` does not apply, because the check asks about the owner, not about a fake player. When FTB Chunks
  refuses, it shows "Interaction prevented here by claim protection!" on the owner's action bar. In the same tick, Quick
  Work replaces that text with "You may not use {target}".
  The pasture check takes the same steps through the APIs of FTB Chunks and FTB Teams, by UUID: `disable_protection`,
  the owner's admin bypass, `no_wilderness`, the tag `ftbchunks:interact_whitelist` and the team's block interaction
  setting with `ally_mode`. It shows no text.
- **Open Parties and Claims.** The check of Quick Work is the mod's own check of a block interaction, with its messages
  off. The player config of the claim's owner decides. By default, `claims.protection.exceptions.fullAccess` gives access
  to the owner's party, and the value `A` gives access to allied parties too. The pasture check asks
  `IChunkProtectionAPI.hasChunkAccess` by UUID, which is the same full access. A player without full access can still
  click the blocks of the server's exception lists, such as a crafting table, but the pasture check refuses them. No role
  works such a block.
- **MineColonies.** The ranks of the colony decide, by the colony's own right-click rule. A hut block needs the rank's
  permission to access huts. A block of the colony's protection exception tag passes for a rank that is not hostile, and a
  door or a fence gate passes with the permission for toggleables. While `enableColonyProtection` is on, any other block
  needs the permission to right-click blocks, and a block with an inventory also needs the permission to open containers.
  A player outside the colony's list has the Neutral rank. The check of Quick Work asks
  `IPermissions.hasPermission(Player, Action)`, so an operator whose permission level is at least
  `permissionEventMinBypassPermLevel` (2 by default) passes in creative mode, as that player's own click does. The pasture
  check reads the permissions of the owner's rank, with no operator bypass, because the bypass needs a player in creative
  mode at the click. It refuses the free blocks of a colony, because the API of MineColonies has no getter for them.
- **No switch.** No config key turns these checks off. `quickWork.enabled = false` turns off all of Quick Work.
- **On COHO 2.4.0.** With FTB Chunks and MineColonies, the pastures keep out of the claims of other teams, and out of the
  colonies whose ranks do not let their owner use a block. The members and allies of a team, and the Officers of a colony,
  keep their machines working.

## Other mods

- **Cobbleworkers.** At each poll of a Work Mode pasture, the mod sets `cobbleworkers.resting` in the `persistentData` of
  each of its Cobblemon, so Cobbleworkers gives them no job. When a Cobblemon leaves Work Mode, the mod clears the flag only
  if the mod set it. `compat.cobbleworkers = false` in the server config turns this off.
- **Cobblebase.** Four mixins on Cobblebase's helper methods stop its jobs, idle behaviours, water moves and passive
  experience for a Work Mode pasture and its Cobblemon. If a hook does not apply, the mod logs one warning that names it.
  `compat.cobblebase = false` in the server config turns this off.
- **Both.** Pastures with Work Mode off keep their Cobbleworkers and Cobblebase jobs. See
  [ADR-0009](adr/0009-coexistence-with-pasture-job-mods.md).
- **Apothic Enchanting**, the enchanting module of Apotheosis. Its enchanting table computes the offers from the
  table's stats and reads no bookshelf count. So with it, a Muse adds Quanta to those stats and no bookshelves. By
  default it adds ceil(45 × L ÷ 100) Quanta, from `quanta_at_level_100` in the Muse's role file. A level 100 Muse
  takes a table without Quanta shelves from 15% to 60%. A mixin on `EnchantmentTableStats.gatherStats` adds the Quanta, and the
  unit tests read Apothic Enchanting 1.21.1-1.6.4, the version of COHO 2.4.0. If the mixin does not apply, the Muse role
  turns off with one warning.

## Mixins

| Applies | Targets |
| --- | --- |
| Always | `EnchantmentMenu`, `LivingEntity` |
| With Create | `AirCurrent`, `BlazeBurnerBlockEntity`, `ItemUseOverrides`, `WaterWheelBlockEntity`, `WindmillBearingBlockEntity` |
| With Cobblebase | `AmbientBehavior`, `BaseManager`, `NavigationHelper`, `PassiveXp` |
| With Apothic Enchanting | `EnchantmentTableStats` |
| Client only | Cobblemon's `PastureWidget` and `PasturePokemonScrollList` |

A config plugin skips the Create, Cobblebase and Apothic Enchanting mixins when their mod is absent. It also skips a
mixin whose target class lacks a field or method that the mixin needs. A mixin that does not apply turns off only the
part that it serves, with one warning: a role, the Work Leash on Create's hand crank, the worker push, a Cobblebase hook
or a part of the pasture panel. So a renamed field in a later Create 6.0.x release turns a role off and does not crash
the game.

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
  Create and AE2 also tests the claim checks of Quick Work and of the pastures with a test claim mod. The release workflow
  runs the build, the unit tests and the jar checks again for each tag.
- **Tested at each phase gate.** The isolated test pack runs clean dedicated servers with only this mod and its pinned
  dependencies. It checks the dependency ranges, including the NeoForge floor, plus servers with only Cobbleworkers,
  only Cobblebase, only FTB Chunks, only Open Parties and Claims or only MineColonies, `/reload` and restarts. Its smoke
  server checks the three load lines. The three claim runs test Quick Work and the pastures against the real claim mods:
  each pasture check must give the answer of the claim mod's own check for each player, online and after a logout.
- **Not tested automatically:**
  - Play with two or more players. The approver ruled out multiplayer GameTests, so an audit checks the code instead,
    in [the multiplayer audit](testing/multiplayer-audit.md).
  - Client rendering, which the approver's play-test checks.
  - Mods that this page does not name.
