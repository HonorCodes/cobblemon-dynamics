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
| Each mod that a compat package of 1.2 names, from Immersive Engineering to Create: Connected | no | any |

The mods of the last row are those of [Mods that the roles support](#mods-that-the-roles-support) with a role file or an
adapter of their own. NeoForge checks no version of them. No supported mod needs a setup step: no config key, no data
pack and no command. Install the mod, and its targets work.

Without Create, the roles that need it turn off: the Wheel Driver, the Cranker, the Superheater, the Fan Catalyst and
Tailwind. Each of their role files logs one line, and the other roles work as before. Without AE2, the role file
`cranker_ae2` does not load, and no role turns off. Only the compat package of the AE2 crank names AE2 classes. The mod
reaches the other AE2 blocks through NeoForge's energy and item capabilities and the block tag `#c:budding_blocks`.

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
| MineColonies | MineColonies 1.1.1420-1.21.1-snapshot, with Structurize 1.0.839-1.21.1-snapshot, BlockUI 1.0.212-1.21.1-snapshot and Domum Ornamentum 1.0.237-snapshot, the versions of COHO 2.5.0 |

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

## Farming roles

Version 1.1 adds two farming roles. The Cultivator (Ground) grows crops, and the Harvester (Grass) harvests them. Data
tags decide which blocks each role works, so a pack adds its own crops with a data pack:

- `#cobblemon_dynamics:cultivator_targets` names the blocks that a Cultivator grows. The default holds
  `#minecraft:crops` and the growable crops that no mod tags.
- `#cobblemon_dynamics:harvester_targets` names the blocks that a Harvester harvests. The default holds
  `#minecraft:crops` and the harvestable crops that no mod tags.

Each entry of another mod is optional, so the tags load without that mod. The defaults leave out
`#minecraft:bee_growables` and `#cobblemon:crops`, which hold Cobblemon's apricorns and terrain seeds. A Harvester never
takes an apricorn, also one that a pack adds to its tag.

A crop adapter decides how a role reads a block. The mod names no class of a crop mod. The adapters read vanilla
classes, Cobblemon's classes, block state properties and these adapter tags, which a pack can extend:

| Adapter tag | What the Harvester does |
| --- | --- |
| `#cobblemon_dynamics:harvest/never` | Leaves the block, for example a stem or a crop that turns into a flower. |
| `#cobblemon_dynamics:harvest/pick_to_age_0` | Drops the loot and sets the `age` property to 0. The plant stays. |
| `#cobblemon_dynamics:harvest/pick_to_age_1` | Drops the loot and sets the `age` property to 1. The plant stays. |
| `#cobblemon_dynamics:harvest/upper_half` | Takes the upper block of a two-block crop once the lower block is at its last age. |
| `#cobblemon_dynamics:harvest/cave_vines` | Picks the berries of a block with a `berries` property that is no `CaveVines`, as of cave vines. New in 1.2, between `upper_half` and `break_and_replant`. |
| `#cobblemon_dynamics:harvest/break_and_replant` | Breaks a block that is no `CropBlock` and replants it from the drops. |

The data map `cobblemon_dynamics:harvest_loot` (blocks, new in 1.2) comes before every adapter tag. Each entry gives a
block a loot table and its `age` after a pick, as `{"loot_table": "twilightforest:blocks/blackberry_bush_berries",
"age_after": 2}`. Such a block is fully grown at its highest `age`. A pick drops that table's loot, rolled with the
block's state, and sets `age`, with no block broken. The default file lists the 8 berry bushes and the 4 oreberries of the
Twilight Forest, each entry with its own condition `neoforge:mod_loaded` on `twilightforest`. A data map has no
`required` flag, so a pack's own entry for another mod needs such a condition too.

A block in no adapter tag gets the adapter of its class. A Cobblemon berry tree gives its berries, cave vines lose their
berries, an attached stem loses the fruit that it faces, and a `CropBlock` is broken and replanted. A Cultivator grows
any `BonemealableBlock` of its tag. At each data load, the server log has one line for each role, in the form
`Farming: harvester 2 blocks [crop 1, pick_to_age_1 1], no adapter []`. A block in the "no adapter" list gets no worker.

The defaults cover these crop mods, at the versions of COHO 2.5.0. [ADR-0015](adr/0015-farming-roles-and-crop-adapters.md)
lists each crop family with its adapters.

| Mod | Version | Crop families |
| --- | --- | --- |
| Minecraft | 1.21.1 | Wheat, carrots, potatoes, beetroots, melon and pumpkin stems, sweet berry bush, cave vines, cocoa, nether wart |
| Cobblemon | 1.8.4 | Berry trees, mints, Revival Herb, Medicinal Leek, Vivichoke, Hearty Grains, Galarica Nut Bush |
| Farmer's Delight | 1.21.1-1.3.4 | Cabbages, onions, tomatoes, rice |
| Expanded Delight | 0.1.4 | Asparagus, sweet potato, chili pepper, peanut, cranberry |
| Miner's Delight+ | 1.21.1-1.4.5 | Cave carrots |
| Supplementaries | 1.21.1-4.0.2 | Flax |
| Immersive Engineering | 12.4.2-194 | Hemp |
| The Aether | 1.5.10 | Blueberry bush |
| Deep Aether | 1.1.5.1 | Squash |
| Oh The Biomes We've Gone | 2.6.3 | Pale pumpkin, blueberry bush, oddion |
| The Undergarden | 0.9.6 | Underbean, blisterberry, gloomgourd |
| Ars Nouveau | 5.13.5 | Magebloom, sourceberry bush |
| Occultism | 1.227.0 | Datura |
| Regions Unexplored | 0.6.3 | Salmonberry bush, duskmelon |
| Pam's HarvestCraft 2 Crops and Trees | 1.0.9 | 97 crops and 50 tree fruits |

The Harvester leaves Supplementaries' rope and stick tomatoes, which have no loot table. The defaults leave out
Cobblemon's terrain seeds and roots, Farmer's Delight's mushroom colonies and wild crops, Pam's gardens and saplings,
Legendary Monuments' carrots, and the plants of My Nether's Delight and Ender's Delight. A pack can add any of them.

Version 1.2 adds these families, at the versions of [ADR-0020](adr/0020-machines-and-crops-of-other-mods.md#crops-of-other-mods),
and `#minecraft:saplings` for the Cultivator, so that a Ground-type grows the saplings that a Lumberjack plants.

| Mod | Version | Crop families | Cultivator | Harvester |
| --- | --- | --- | --- | --- |
| Cobblemon: Mega Showdown | 1.3.3 | Max mushroom | grows | `harvest/pick_to_age_1` |
| Ars Nouveau, Ars Elemental | 5.13.5, 0.7.10.4 | The pods of Ars Nouveau and the flashpine pod | grows | `harvest/break_and_replant` |
| The Twilight Forest | 4.8.3345 | The 8 berry bushes | grows | `harvest_loot`, `age` 2 after |
| The Twilight Forest | 4.8.3345 | The copper, iron, gold and essence oreberries | no bone meal | `harvest_loot`, `age` 2 after |
| Oh The Biomes We've Gone | 2.6.3 | Apple, green apple, baobab, yucca and soul fruit | grows | `harvest/pick_to_age_0` |
| Oh The Biomes We've Gone | 2.6.3 | White puffball | grows | `harvest/pick_to_age_1` |
| Allthemodium, Deep Aether, Deeper and Darker | 3.2.1, 1.1.5.1, 1.4.1 | Ancient cave vines, golden vines, the body of glowing vines | grows | `harvest/cave_vines` |
| Allthemodium, Ars Elemental, YUNG's Cave Biomes, Deeper and Darker | 3.2.1, 0.7.10.4, 3.1.1, 1.4.1 | The body of ancient cave vines, source vines, prickly peach cactus, the tip of glowing vines | grows | never: growth only |
| Unusual End | 2.3.1b | Gloopilon bulb | grows | crop |
| The Undergarden | 0.9.6 | Ditchbulb | grows | `harvest/pick_to_age_0` |
| Actually Additions | 1.3.26 | Coffee | grows | crop |
| Dynamic Trees | 1.7.2 | Its saplings and rooty soil | grows | none |

The five growth-only look-alikes of cave vines stay out of the Harvester's tag, because their block loot drops no berry
or drops the plant itself. The three of them whose class is `CaveVines` are in `#cobblemon_dynamics:harvest/never`. The
Cultivator never works a block of `#cobblemon_dynamics:cultivator/never`, whose default holds `#mysticalagriculture:crops`:
bone meal never grows them. The Harvester still harvests them.

## Mods that the roles support

Version 1.2 works the machines of these mods with the built-in role files, tags and data maps, and with no setup step
(CD-OPT-01). A role file of a mod holds the condition `neoforge:mod_loaded` of that mod, so it loads only with the mod.
Each entry of another mod in a built-in tag has `"required": false`, and each entry of the data map carries its own
condition. A pack changes a default with its own data pack. The versions are those that the tests read.
[ADR-0020](adr/0020-machines-and-crops-of-other-mods.md) holds each mechanism, number and benchmark, and the
[player guide](player-guide.md#other-mods) tells players what each role does there.

| Mod | Tested version | Role files (assignment) | Targets |
| --- | --- | --- | --- |
| Immersive Engineering | 12.4.2-194 | `stoker_immersiveengineering` (auto), `wheel_driver_immersiveengineering` (auto), `tailwind_immersiveengineering` (leash) | The alloy kiln, and the crude and improved blast furnaces from level 50. The watermill, while a Kinetic Dynamo takes its power. The windmill. |
| Mekanism | 10.7.19.85 | `stoker_mekanism` (auto), `heater_mekanism` (auto) | The Fuelwood Heater, while a neighbour takes its heat. `#cobblemon_dynamics:heater_targets`: the Thermal Evaporation valve and the Thermoelectric Boiler valve. |
| Mekanism Generators | 10.7.19.85 | `tailwind_mekanism` (leash) | The Wind Generator, by any of its blocks. |
| Railcraft Reborn | 1.2.17 | `stoker_railcraft` (auto), `stoker_railcraft_boiler` (leash) | The blast furnace from level 50. Each block of a solid-fueled firebox, in a pool of one worker per block. |
| PneumaticCraft: Repressurized | 8.2.24 | `stoker_pneumaticcraft` (auto), `heater_pneumaticcraft` (leash), `tailwind_pneumaticcraft` (leash) | The Air Compressor and the Advanced Air Compressor. A block that takes heat on a horizontal face. A block that holds air. |
| Botany Pots, with Bookshelf 21.1.81 and Prickle 21.1.11 | 21.1.44 | `cultivator_botanypots` (auto), `harvester_botanypots` (auto) | Each pot with a crop, except a waxed pot, which never grows. The Harvester takes a basic pot only, never a hopper pot. |
| Iron Furnaces | 4.3.2 | `stoker_ironfurnaces` (auto) | Each furnace that runs as a furnace, at its own speed. |
| Cooking for Blockheads, with Balm | 21.1.24 | `stoker_cookingforblockheads` (auto) | The Oven of each color. |
| Forbidden & Arcanus, with Valhelsia Core | 2.6.1 | `stoker_forbidden_arcanus` (auto) | The Clibano, by any of its blocks. |
| Cobblemon Incubator, with Cobbreeding 2.4.1 | 1.1.1 | `stoker_cobblemon_incubator` (auto) | The Egg Incubator, while its config turns Speed Upgrades on. |
| Ars Nouveau | 5.13.5 | `stoker_ars_nouveau` (auto) | The Volcanic Sourcelink. |
| Create Crafts & Additions | 1.7.2 | `superheater_createaddition` (auto) | The Liquid Blaze Burner under a heat user. |
| Iron's Spells 'n Spellbooks, with Iron's Lib 2.3.0 | 3.16.3 | `brewer_irons_spellbooks` (auto) | The Alchemist Cauldron. |
| Applied Energistics 2, with ExtendedAE 2.2.43 | 19.2.19 | `cranker_ae2` (auto) | The AE2 crank on a machine that a crank powers. |
| Create: Connected | 1.3.3 | the Cranker's own file | The Crank Wheel and the Large Crank Wheel. |
| Easy Magic, Enchanting Infuser, with Puzzles Lib | v21.1.4-1.21.1-NeoForge | the Muse's own file | Easy Magic's table, and both infusers, through `#cobblemon_dynamics:muse_targets`. |
| Create: Enchantment Industry, with Apothic Enchanting 1.21.1-1.6.4 | 2.5.4b | the Muse's own file | The Infuser over its basin. |
| experiencelib, with Applied Experienced 1.3.2 | 1.2.1 | none | The XP capability on the outputs of the Experience Collector. |
| Dynamic Trees | 1.7.2 | the Lumberjack's own file | Its trees, through its own fell. |
| Quark, Unusual End, GeOre | 4.1-488, 2.3.1b, 6.2.4 | the Crystal Tender's own file | Quark's corundum, Unusual End's budding citrine and GeOre's budding blocks, through the crystal tags. |
| Farmer's Delight with Create: Central Kitchen, Create: Diesel Generators, Create: New Age | 1.3.4 with 2.6.2b, 1.21.1-1.3.15, 1.2.0+mc1.21.1 | the Superheater's own file | The cooking pot and the skillet, the distillation tank and the heat pipe above a burner, through `#cobblemon_dynamics:superheater/heat_users`. |
| The Aether | 1.5.10 | none | Its Freezer stays out of the Stoker's targets, through `#cobblemon_dynamics:stoker/never`. |
| Mystical Agriculture | 8.0.29 | none | Its crops stay out of the Cultivator's targets, through `#cobblemon_dynamics:cultivator/never`. |
| CobbleFurnies, Handcrafted, Valhelsia Furniture | 1.2, 4.0.3, 1.1.1 | the files of Telekinesis and the Magnet | Their furniture containers, as plain containers. |
| Cobblemon | 1.8.4 | `generator_healing_machine` (auto, no condition) | The Healing Machine. |

The crop mods of 1.2 are in [Farming roles](#farming-roles). The ids that ADR-0020 marks "unverified" stay optional
entries: a wrong id loads nothing and breaks nothing.

### Tags and the data map of 1.2

These tags and the data map are new in 1.2. Each lives under `data/cobblemon_dynamics/`.

| Tag or data map | Default | What it does |
| --- | --- | --- |
| `#cobblemon_dynamics:lumberjack_targets` | `#minecraft:logs` | The logs that the Lumberjack fells. A sapling is a target too. |
| `#cobblemon_dynamics:lumberjack/never` | Pam's fruit, apricorns and their logs and leaves, the magic log cores of the Twilight Forest, bee nests, beehives, crimson and warped stems, mangrove logs, Unusual End's chorus cane, Better Archeology's rotten log | The Lumberjack skips a whole tree that holds such a block or touches one. |
| `#cobblemon_dynamics:lumberjack/never_saplings` | The mangrove propagule, apricorn saplings, the magic saplings of the Twilight Forest, the saplings of Pam's trees | The Work Leash refuses a Lumberjack bind on such a sapling. |
| `#cobblemon_dynamics:lumberjack/needs_four` | `minecraft:dark_oak_sapling` | The replant plants such a sapling only as a 2 × 2. |
| `#cobblemon_dynamics:needs_adjacent_water` | Sugar cane, coral blocks, corals, wall corals, the conduit, Create's encased fan | The Freezer keeps the water that such a block needs. |
| `#cobblemon_dynamics:stoker/never` | The Aether's Freezer | The Stoker never works such a block. |
| `#cobblemon_dynamics:cultivator/never` | `#mysticalagriculture:crops` | The Cultivator never works such a block. |
| `#cobblemon_dynamics:superheater/heat_users` | Create's basin and fluid tank, Farmer's Delight's cooking pot and skillet, the distillation tank of Create: Diesel Generators, the heat pipe of Create: New Age | Such a block above a burner gives the Superheater work. |
| `#cobblemon_dynamics:heater_targets` | The Thermal Evaporation valve and the Thermoelectric Boiler valve of Mekanism | The targets of the Heater on Mekanism. A fission or fusion reactor port, the Heat Generator and the Quantum Entangloporter stay out. |
| `#cobblemon_dynamics:crystal_tender_targets` | `#c:budding_blocks`, the two crystal tags below, Quark's corundum blocks | The targets of the Crystal Tender. |
| `#cobblemon_dynamics:crystal/self_growing` | Cobblemon's nine budding tumblestones | Work only while Cobblemon's own rule lets the block grow: a heat source within 1 block, and a next stage. |
| `#cobblemon_dynamics:crystal/scheduled_tick` | Unusual End's budding citrine | Such a block gets its scheduled tick on 1 extra tick in 5, and only air counts as an open face. |
| `#cobblemon_dynamics:crystal/buds` | Unusual End's citrine buds | Such a block counts as a bud of `#c:buds`. |
| `#cobblemon_dynamics:muse_targets` | The enchanting table, both infusers of Enchanting Infuser, CEI's Infuser | The targets of the Muse. Easy Magic copies the tags of the enchanting table onto its own table at each tag load, so Easy Magic's table is a target too. Do not add Easy Magic's table to this tag: Easy Magic stops the data load when its table already has a tag. |
| `#cobblemon_dynamics:logistics/no_fallback` | The Ender Chest, the containers of Lootr, the pedestals of Legendary Monuments | Telekinesis and the Magnet never reach such a block as a plain container. The Ender Chest entry guards nothing, because its block entity is no `Container`. |
| `#cobblemon_dynamics:harvest/cave_vines` | Allthemodium's ancient cave vines, Deep Aether's golden vines, the body of Deeper and Darker's glowing vines | See [Farming roles](#farming-roles). |
| Data map `cobblemon_dynamics:harvest_loot` (blocks) | The 12 bushes of the Twilight Forest | See [Farming roles](#farming-roles). |

**Plain containers.** Telekinesis and the Magnet reach a block entity that has no item capability and is a vanilla
`Container` through NeoForge's `InvWrapper`, or `SidedInvWrapper` for a `WorldlyContainer`. Before each take, they ask
the container's `canTakeItem`, as a hopper does, because neither wrapper asks it. So Cobblemon's TM Machine keeps its
inputs, and Visual Workbench keeps its crafting grid. A pack keeps a container of its own out with the tag
`logistics/no_fallback`.

### Load lines of 1.2

Each server log of 1.2 holds these lines beside the roles line, the mixins line and the farming lines:

- **Skipped roles.** At each load of the role files, one line names each role file that its conditions skipped, with the
  mods that it needs: `Roles not loaded, their mods are absent: 2 [cobblemon_dynamics:heater_mekanism (mekanism),
  cobblemon_dynamics:stoker_ironfurnaces (ironfurnaces)]`. Such a file counts neither as loaded nor as disabled, so the
  roles line keeps naming real problems only.
- **Compat lines.** At each data load, one line for each loaded mod with a compat package names each of its adapters as
  found or missing, with what is missing: `class` when its compat package did not register it, `block` when a block id
  does not resolve, and `mixin` when a mixin did not apply. For example:
  `Compat: immersiveengineering 12.4.2-194 [ie_furnace found, ie_watermill found, ie_windmill missing (mixin)]`. A mod
  that tags and data maps alone support has no adapter, so it logs no compat line: the Aether, the Twilight Forest and the
  crop mods. A missing adapter turns off only its own target.
- **The XP fluid.** After each tag load, one line names the fluid of the Experience Collector's outputs, such as
  `Experience Collector: pipes drain create_enchantment_industry:experience at 1 mB per XP`, or `Experience Collector: no
  XP fluid, so pipes drain nothing`.

| Mod | Adapters of its compat line |
| --- | --- |
| Immersive Engineering | `ie_furnace`, `ie_watermill`, `ie_windmill` |
| Mekanism | `fuelwood_heater`, `mekanism_heat` |
| Mekanism Generators | `wind_generator` |
| Railcraft Reborn | `railcraft_fuel`, `railcraft_firebox` |
| PneumaticCraft: Repressurized | `pnc_compressor`, `pnc_heat`, `pnc_air` |
| Botany Pots | `pot_cultivator`, `pot_harvester` |
| Iron Furnaces | `iron_furnace` |
| Cooking for Blockheads | `cfb_oven` |
| Forbidden & Arcanus | `clibano` |
| Cobblemon Incubator | `egg_incubator` |
| Ars Nouveau | `volcanic_sourcelink` |
| Create Crafts & Additions | `liquid_burner` |
| Create: Connected | `crank_wheel` |
| Create: Enchantment Industry | `muse_infuser` |
| Iron's Spells 'n Spellbooks | `alchemist_cauldron` |
| Easy Magic | `muse_table` |
| Enchanting Infuser | `muse_infusers` |
| experiencelib | `collector_xp` |
| Dynamic Trees | `lumberjack_fell` |
| Apothic Enchanting | `muse_quanta` |
| Applied Energistics 2 | `ae2_crank` |

### `quick_work: false`

Version 1.2 gives the role file key `quick_work` a second meaning. Before, `true` let a Work Leash role take Quick Work
orders. Now `false` also keeps an auto-assign role out of Quick Work, as the Freezer's file does. A file without the key
follows its `auto_assign` flag, so no other built-in role changes. A third-party role file that already sets `quick_work:
false` on an auto-assign role loses Quick Work in 1.2.

### The Experience Collector's outputs

The back face and the bottom face of an Experience Collector are drain-only outputs for pipes. The other faces and a
null side offer nothing. Pipes see the stored XP as one fluid, which the collector picks again after each tag load:

1. Create: Enchantment Industry's `create_enchantment_industry:experience`, at 1 mB per XP, when the fluid registry holds
   it.
2. Otherwise the first fluid of `#c:experience`, at 20 mB per XP, the ratio that NeoForge documents. Sophisticated Core's
   `sophisticatedcore:xp_still` is such a fluid.
3. Otherwise no fluid. The faces then offer no fluid handler, and the screen's button still works.

A drain takes whole points of XP only, so no part of a point is lost or made. A pipe cannot fill the collector. The XP
machines of Ender IO, Industrial Foregoing, Cyclic and Just Dire Things take a `c:experience` fluid at 20 mB per XP from
these outputs, so they work as pipe destinations. With CEI installed, the collector offers CEI's fluid, which is not in
`c:experience`, so such a machine and a Sophisticated tank take none. With experiencelib installed, the two outputs also
offer its XP capability, drain only, so an Applied Experienced import bus takes the XP into an ME network.

### What works already

These targets need no code of 1.2, because a role reaches them through a capability or a vanilla class:

- **The Generator:** the controller and the Interface of Refined Storage 2.0.9, the motors of Create: New Age, the machines
  of Mystical Agriculture 8.0.29, the Wireless Energy Heater of Iron Furnaces, the Oven of Cooking for Blockheads with a
  Heating Unit, the Farmer of Actually Additions, the Heated Centrifuge of Productive Bees and the charge network of
  Railcraft Reborn.
- **The Pump:** the oxygen loaders of Ad Astra, the Hydroponic Bed of Industrial Foregoing, the boiler addon and the
  Fertilizer of Oritech, the fluid port of Extreme Reactors, the Thermo Generator of Powah, the Water Separator of
  Stellaris, the boilers of Railcraft Reborn and the Interface of Refined Storage.
- **Telekinesis and the Magnet:** the Interface of Refined Storage.
- **The Crystal Tender:** GeOre 6.2.4, the entro budding blocks of ExtendedAE and the imbued wood of Oh The Biomes We've
  Gone.
- **The farming roles:** the fertilized farmland of Farming for Blockheads, the crops of Mystical Agriculture for the
  Harvester, and the canola, flax and rice of Actually Additions.
- **The Superheater:** the Fluid Vessel of Create: Connected.
- **The Stoker:** the Oven of Handcrafted, the Cinder Furnace of the Twilight Forest and the Altar of the Aether.
- **The Experience Collector's outputs:** the XP machines of Ender IO, Industrial Foregoing, Cyclic and Just Dire Things.

### Known limits

- **Easy Magic's `maxEnchantingPower`.** The hook on Easy Magic's table caps the power with a Muse at 15, the default of
  that server setting, so on a server that raises the setting a Muse gives less than its full share.

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
| With Easy Magic | `ModEnchantmentMenu` |
| With Enchanting Infuser | `InfuserMenu` |
| With Immersive Engineering | `WatermillBlockEntity`, `WindmillBlockEntity` |
| With Mekanism Generators | `TileEntityWindGenerator` |
| With PneumaticCraft: Repressurized | `AirCompressorBlockEntity` |
| With Forbidden & Arcanus | `ClibanoMainBlockEntity` |
| With Cobblemon Incubator | `EggIncubatorBlockEntity` |
| With Create Crafts & Additions | `LiquidBlazeBurnerBlockEntity` |
| With Iron's Spells 'n Spellbooks | `AlchemistCauldronTile` |
| Client only | Cobblemon's `PastureWidget` and `PasturePokemonScrollList` |

A config plugin skips the mixins of each optional mod when that mod is absent. It also skips a mixin whose target class
lacks a field or a method that the mixin needs, with the method's full descriptor. A mixin that does not apply turns off
only the part that it serves, with one warning: a role, a target of another mod, the Work Leash on Create's hand crank,
the worker push, a Cobblebase hook or a part of the pasture panel. So a renamed field in a later release of a supported
mod turns a target off and does not crash the game. The compressor mixin of PneumaticCraft only fills the flame gauge:
without it, the Stoker still fuels the compressor.

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
the request needs them. Rate limits apply to each player. Quick Work adds no payload. The button of the Experience
Collector sends vanilla's container button packet, which the server checks as for any menu, so 1.2 adds no payload
either. When the payloads register, the log holds one INFO line that starts with `Payloads, version 3:` and names each
payload by direction.

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
  - the Experience Collector: its XP, which the item of a broken collector keeps in the data component
    `cobblemon_dynamics:stored_experience`
  - a data attachment on an Immersive Engineering watermill: the direction of its Wheel Driver, saved only when it is
    reversed

  Section 4 of [ARCHITECTURE.md](../ARCHITECTURE.md) lists every entry. Claims, worker states and Quick Work orders are
  never saved. Quick Work saves nothing except Work Stamina. The Tailwind bonus of an IE windmill and of a Wind Generator,
  a running fell of the Lumberjack and the spots where players died are never saved either.
- **Data pack content.** The mod adds these:
  - 44 role files under `data/cobblemon_dynamics/cobblemon_dynamics/roles/`. 22 of them hold the condition of an optional
    mod, so a server without that mod skips them.
  - recipes for the Work Leash, the four beds, the Charge Cell and the Experience Collector, and loot tables for the beds,
    the Charge Cell and the Experience Collector
  - one Cobblemon behaviour, `data/cobblemon_dynamics/behaviours/pokemon/auto/work.json`, which adds the work tasks to the
    Brain of every Cobblemon
  - the block tags of the roles, the tag `minecraft:mineable/pickaxe` for the Charge Cell and the Experience Collector,
    and the data map `cobblemon_dynamics:harvest_loot`

  A data pack can override any role file. `roles.disabled` in the server config turns a role off. Each load of the role
  files logs one INFO line, such as `Roles: 23 loaded, 23 active, 0 disabled` on a server with Create and AE2 and no other
  supported mod, which names each disabled role with its reason. A config change that changes the active roles logs a new
  line.

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
- **Tested at the 1.2 gate.** The unit tests read each adapter's facts from the pinned jar of its mod. The test pack's
  case `mods` starts one dedicated server with every supported mod that it can fetch from Modrinth or copy from
  `TESTPACK_LOCAL_JARS`. Its compat lines must name every adapter as found, its roles line every role of another mod as
  active, and its mixins line no compat mixin as skipped. The GameTest run `runGameTestServerCompat` tests each
  adapter that writes another mod's private state, with that mod on the server. CI runs neither.
- **Not tested automatically:**
  - Play with two or more players. The approver ruled out multiplayer GameTests, so an audit checks the code instead,
    in [the multiplayer audit](testing/multiplayer-audit.md).
  - Client rendering, which the approver's play-test checks.
  - Mods that this page does not name.
