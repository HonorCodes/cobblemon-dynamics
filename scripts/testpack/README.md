# Isolated test pack

`testpack.py` builds clean NeoForge 1.21.1 dedicated servers under `run/testpack/`. Each server
gets only the mods that its scenario needs. The script never reads or changes the COHO modpack or
another existing Minecraft instance.

## Requirements

- Python 3.12. The script uses only the standard library. Run it with `py` on Windows and with
  `python3` on Linux.
- A JDK 21 in `JAVA_HOME`. The script never starts the `java` on the PATH, because that one can be
  older.
- A built mod jar. Run `./gradlew build` (`gradlew.bat build` on Windows) before any command except
  `resolve`.
- For `crops` and `mods` only: the jars of the local pins in the folders of `TESTPACK_LOCAL_JARS` (see
  [Crop mods case](#crop-mods-case) and [Mods case](#mods-case)).

## Commands

Run each command from the repository root, for example `py scripts/testpack/testpack.py smoke`.

| Command | Result |
|---|---|
| `resolve` | Finds each pin of `mods.json` on Modrinth or on the NeoForge maven, then writes its file name, URL and SHA-512 back to `mods.json`. A version without a NeoForge 1.21.1 release gets `null`. A pin with `"source": "maven"` (FTB Chunks, FTB Teams, FTB Library and the mods of LDTTeam) stays as written. The `crops` pins also stay as written. |
| `smoke` | Starts a server with the pinned mods and the built jar. Checks that the mod loads, that the log holds each load line once (the mixins with the 8 mixins of Minecraft and Create applied, and the 4 Cobblebase mixins and the 11 mixins of the other optional mods skipped because their mods are absent; the 6 payloads of version 3; and `Roles: 23 loaded, 23 active, 0 disabled`, the 22 role files with no condition and the AE2 Crank's; CD-CMP-08, CD-CMP-09, CD-CMP-10), and that RCON answers `list`, then stops the server. Prints `SMOKE PASS` or `SMOKE FAIL: <reason>`. |
| `reload` | Starts the same server and runs `cobblemondynamics claims` over RCON (it must answer that there are no claims). Then it writes the world data pack `cd_reload_probe`, whose Stoker role file names a handler that nobody registers, runs `reload` and waits for the reload to end. The log must say that the Stoker is disabled for that handler (CD-CFG-08) and have no `Skipping role file` line. Prints `RELOAD PASS` or `RELOAD FAIL: <reason>`. |
| `restart` | Starts the same server and checks that `config/cobblemon_dynamics-server.toml` holds every key of the CD-CFG-01 list in `ConfigTest` (CD-CFG-01). It force-loads chunk 0, 0, places a Create water wheel and a hand crank, and sets the turn direction that they save (`cd_direction`) to -1 with `data merge block`. Then it stops and starts the server: both blocks must still read -1 (CD-WHL-07, CD-CRK-08), `cobblemondynamics claims` must report none (CD-CLM-07), and the new log must have no error of ours. Prints `RESTART PASS` or `RESTART FAIL: <reason>`. |
| `deps` | Runs the dependency-range cases below, among them the roles line without Create (CD-CMP-10). Prints one `CASE <name>: PASS\|FAIL\|SKIPPED <detail>` line per case, then `DEPS PASS` or `DEPS FAIL`. |
| `compat` | Runs the compatibility cases below. Prints one `CASE <name>: PASS\|FAIL\|SKIPPED <detail>` line per case, then `COMPAT PASS` or `COMPAT FAIL`. |
| `crops` | Starts a server with the pinned mods, the built jar, the crop mods of COHO 2.5.0 and their libraries. Checks the two farming lines of CD-CRP-05, and that the Cultivator's line counts the saplings of `#minecraft:saplings` (CD-LUM-23, CD-CRP-01). See [Crop mods case](#crop-mods-case). Prints `CROPS PASS` or `CROPS FAIL: <reason>`. |
| `mods` | Starts one server with the default config, the pinned mods, the built jar and every mod of the table of ADR-0016 that Modrinth or `TESTPACK_LOCAL_JARS` holds, with their libraries (CD-OPT-01, CD-OPT-15). Checks the compat lines (CD-OPT-11), among them the hook of Create: Connected's crank wheels (CD-KIN-05), the roles lines, the mixins line and the farming lines (CD-FRM-08). See [Mods case](#mods-case). Prints `MODS PASS` or `MODS FAIL: <reason>`. |
| `client` | Fills `run/testpack/client/mods/` with the pinned mods and the built jar, for a manual play-test in a clean NeoForge 21.1.265 launcher instance. |

The exit code is 0 for a pass and 1 for a failure.

## Dependency-range cases

Each case starts from the pinned set. A rejection case passes when FML logs the line
`Mod ID: '<mod>', Requested by: 'cobblemon_dynamics', Expected range: '<range>'` and the server stops
before it loads a world. FML puts that line under `Missing or unsupported mandatory dependencies:` for
a required dependency, and under `Unsupported installed optional dependencies:` for an optional one.
After a rejection, the server exits with code 0. So the script checks the log, not the exit code.

| Case | Change to the pinned set | Expected result |
|---|---|---|
| `neoforge-floor` | NeoForge 21.1.219, the floor of `[21.1.219,)` | The server loads |
| `neoforge-below-floor` | NeoForge 21.1.218 | FML rejects `neoforge` `[21.1.219,)` |
| `cobblemon-below-floor` | Cobblemon 1.8.3 | FML rejects `cobblemon` `[1.8.4,1.9)` |
| `create-below-floor` | Create 6.0.9 | FML rejects `create` `[6.0.10,6.1)` |
| `ae2-below-floor` | The latest AE2 19.1 release | FML rejects `ae2` `[19.2,20)` |
| `without-create-ae2` | No Create, AE2 or GuideME | The server loads, and logs `Roles: 22 loaded, 15 active, 7 disabled` once, with the Wheel Driver, the Cranker, the Superheater, the three Fan Catalysts and Tailwind, each with its reason (CD-CMP-10). The AE2 Crank's role file needs AE2, so it does not load |
| `kff-floor` | None, because the pin is Kotlin for Forge 5.10.0, the floor of `[5.10,)` | The server loads |

A case is `SKIPPED` when `resolve` found no release for its version.

## Compatibility cases

Each case adds a neighbour mod from the `compat` pins to the pinned set. The server writes every class
that it loads to `classload.log` in the profile (`-Xlog:class+load`). A case passes when the server starts
and stops, the named class of ours loads, no class with a forbidden prefix loads, no error line names one,
and the log has no `Cobblebase hooks ... did not apply` warning and no mixin failure.

| Case | Added mods | Must load | Must never load |
|---|---|---|---|
| `compat_cobblebase` | Cobblebase 2.0.1 and Cloth Config 15.0.140, which Cobblebase needs at run time | `compat.CobblebaseHooks` | `compat.Cobbleworkers*` (CD-CMP-06), and the three claim packages (CD-QWK-33, CD-LND-13) |
| `compat_cobbleworkers` | Cobbleworkers 2.0.7 | `compat.CobbleworkersCompat` | `compat.Cobblebase*` and `mixin.compat.cobblebase.*` (CD-CMP-07), and the three claim packages (CD-QWK-33, CD-LND-13) |
| `compat_ftbchunks` | FTB Chunks 2101.1.23, FTB Teams 2101.1.12, FTB Library 2101.1.39 and Architectury API 13.0.11 | `compat.ftbchunks.FtbChunksCompat` | `compat.opac.*` and `compat.minecolonies.*` (CD-QWK-33, CD-LND-13) |
| `compat_opac` | Open Parties and Claims 0.33.0 | `compat.opac.OpacCompat` | `compat.ftbchunks.*` and `compat.minecolonies.*` (CD-QWK-33, CD-LND-13) |
| `compat_minecolonies` | MineColonies 1.1.1420, Structurize 1.0.839, BlockUI 1.0.212, Domum Ornamentum 1.0.237 and Multi-Piston 1.2.58, the jars of COHO 2.5.0 | `compat.minecolonies.MineColoniesCompat` | `compat.ftbchunks.*` and `compat.opac.*` (CD-QWK-33, CD-LND-13) |

Class names are under `codes.honor.cobblemondynamics.`. The three claim packages are `compat.ftbchunks.*`, `compat.opac.*` and `compat.minecolonies.*`.

## Crop mods case

`crops` checks the farming load lines of CD-CRP-05 with real crop mods. The command was written for the
1.1 gate and first ran on 2026-11-17.

The server gets the pinned set and the `crops` pins of `mods.json`: the crop mods of COHO 2.5.0 and the
libraries that they need. Each pin is a jar of the frozen COHO cache of 2026-11-02. `resolve` leaves
these pins as written.

| Crop mod | Libraries that it needs, beyond the pinned set |
|---|---|
| Farmer's Delight 1.3.4 | None |
| Expanded Delight 0.1.4 | Farmer's Delight |
| Miner's Delight 1.4.5 | Farmer's Delight, Lodestone 1.8.2 |
| Supplementaries 4.0.2 | Moonlight 3.8.3 |
| Oh The Biomes We've Gone 2.6.3 | Corgilib 5.0.0.10, Oh The Trees You'll Grow 5.3.6, TerraBlender 4.1.0.8, GeckoLib 4.9.3 |
| The Aether 1.5.10 | owo-lib 0.12.15.6-beta.1, which the Accessories 1.1.0-beta.48 that The Aether bundles requires |
| Deep Aether 1.1.5.1 | The Aether |
| The Undergarden 0.9.6 | None |
| Ars Nouveau 5.13.5 | Curios 9.5.1, GeckoLib |
| Occultism 1.227.0 | Modonomicon 1.123.0, SmartBrainLib 1.16.11, Curios, GeckoLib |
| Immersive Engineering 12.4.2-194 | None |
| Regions Unexplored 0.6.3 | Lithostitched 1.8.1 |
| Pam's HarvestCraft 2 Crops 1.0.9 | None |
| Pam's HarvestCraft 2 Trees 1.0.9 | None |

Modrinth does not serve four of these jars: The Aether, the two Pam's HarvestCraft 2 mods and
TerraBlender. Their pins have `"source": "local"` and no URL.

The `owo` pin is the NeoForge file of owo-lib 0.12.15.6-beta.1, Modrinth version `wbdHEItB`, which has the
SHA-1 of the frozen cache's jar. Its license is MIT, in its Modrinth project and in its `neoforge.mods.toml`,
read on 2026-11-17 before the pin entered `mods.json`.

Set `TESTPACK_LOCAL_JARS` to a folder that holds these four jars, such as `E:/rch/jars/2026-11-02/`. Do
not use the mods folder of a COHO instance. The script only reads that folder. It copies each jar into
`run/testpack/cache/` and checks its SHA-512. After the first copy, the variable is not necessary. If the
variable is not set or a jar is not in the folder, the command fails with that reason.

The case passes when each farming line names no block in its `no adapter` list, and the count of each
adapter equals `crops_expected` in `mods.json`. It fails when a line is missing or appears twice. It also
fails when the log says that a tag of the farming lines did not load: `minecraft:crops`,
`minecraft:saplings`, `cobblemon:berries`, `cobblemon:mints`, `cobblemon:apricorns` or a tag of ours. The
case expects these lines:

```text
Farming: cultivator 448 blocks [growth 448], no adapter []
Farming: harvester 288 blocks [berry_tree 70, break_and_replant 7, cave_vines 2, cave_vines_tag 2, crop 121, gourd 5, never 10, pick_to_age_0 59, pick_to_age_1 10, upper_half 2], no adapter []
```

The expected counts come from the tag files and the block classes of the pinned jars. The target tags
hold `#minecraft:crops` of Minecraft and 11 crop mods, `#cobblemon:berries`, `#cobblemon:mints` and the
explicit entries of our tags. A blockstate file in its jar shows that each block exists. Each block gets
the first adapter in the order of CD-CRP-03, with the class from the table of crop families in ADR-0015.
`CROP_FAMILIES` in `testpack.py` gives the count and the two adapters of each family. The case fails when
its sums differ from `crops_expected`.

The Cultivator's tag also holds `#minecraft:saplings` (CD-LUM-23). In the case, that tag holds 165 blocks:
Minecraft's 10 saplings, Cobblemon's 8 and 147 of 9 crop mods, from the saplings tag files of the jars. A
sapling is a `BonemealableBlock`, so the growth adapter reads it. The last rows of `CROP_FAMILIES` count
the saplings of each mod. The Harvester's tag holds no sapling.

## Mods case

`mods` checks the load lines of 1.2 with every mod that the roles support (CD-OPT-15). The server gets the pinned set and
the `optional` pins of `mods.json`: each mod of the table of ADR-0016 that Modrinth or `TESTPACK_LOCAL_JARS` holds, and
the libraries that those mods require. Just Dire Things is in neither source, so the case leaves it out. The server runs
with the default config, as an admin would (CD-OPT-01), a 4 GB heap and a boot timeout of 900 seconds.

Each mod that COHO pack 2.7.0 has is its 2.7.0 file. Each other mod is the version of ADR-0016. The `kotlinforforge`
pin replaces the pinned Kotlin for Forge 5.10.0 with the pack's 5.13.0. `resolve` leaves these pins as written.

Modrinth serves no exact copy of 11 of these jars, so their pins are local: The Aether, TerraBlender, the Twilight
Forest, Ars Elemental, Iron's Spells 'n Spellbooks, Create: Connected, ExtendedAE, Allthemodium, Sophisticated Core, The
Undergarden 0.9.7 and Valhelsia Core 1.1.6. `TESTPACK_LOCAL_JARS` can hold several folders, separated by `;` on Windows
and `:` on Linux. The script copies each jar from the first folder that holds it. For example, the frozen COHO cache
`E:/rch/jars/2026-11-02/` and the download folders of pack 2.7.0 hold all 11.

The case passes when these checks pass:

- Each mod of `COMPAT_ADAPTERS` in `testpack.py`, the mods with a compat package, is installed and logs one compat line,
  which names each of its adapters as found (CD-OPT-11). A mod that tags and data maps alone support logs no line (R92),
  so a compat line for a mod outside the table fails the case. `TestpackModsCaseTest` keeps that table equal to the mods
  of `compat/OptionalMods.kt` that log a line.
- The roles line names each role file of the built jar as loaded and active, with none disabled (CD-CMP-10), and the
  line of the skipped role files reads `Roles not loaded, their mods are absent: 0 []` (CD-OPT-04).
- The mixins line names each mixin of the jar's config as applied, except the 4 Cobblebase mixins, which skip because
  Cobblebase is no mod of the table (CD-CMP-08).
- Each farming line names no block without an adapter, so each crop family of the mods has one (CD-FRM-08). The
  exception is the crops of Mystical Agriculture in the Cultivator's line, which the default
  `#cobblemon_dynamics:cultivator/never` keeps from the Cultivator (CD-FIX-04).
- The log has no error that names our mod, no mixin failure, no tag that failed to load, and no loot table of the data
  map `harvest_loot` that did not load (CD-FRM-03).

The license of each pin, from its Modrinth project and its `neoforge.mods.toml`, read on 2026-11-17 before the pin
entered `mods.json`:

| Pin | Version | Source | License | Library of |
|---|---|---|---|---|
| `immersiveengineering` | 12.4.2-194 | Modrinth `immersiveengineering` | Blu's License of Common Sense |  |
| `mekanism` | 10.7.19.85 | Modrinth `mekanism` | MIT |  |
| `mekanismgenerators` | 10.7.19.85 | Modrinth `mekanism-generators` | MIT |  |
| `railcraft` | 1.2.17 | Modrinth `railcraft-reborn` | Railcraft Reborn License |  |
| `botanypots` | 21.1.44 | Modrinth `botany-pots` | LGPL-2.1 |  |
| `create_connected` | 1.3.3 | local | AGPL-3.0 |  |
| `extendedae` | 2.2.43 | local | LGPL-3.0 |  |
| `createaddition` | 1.7.2 | Modrinth `createaddition` | MIT |  |
| `cobblemon_incubator` | 1.1.1 | Modrinth `cobblemon-incubator` | MIT |  |
| `ars_nouveau` | 5.13.5 | Modrinth `ars-nouveau` | GPL (jar), All Rights Reserved (Modrinth) |  |
| `ars_elemental` | 0.7.11.0 | local | LGPL-3.0 |  |
| `irons_spellbooks` | 3.16.3 | local | All Rights Reserved |  |
| `create_enchantment_industry` | 2.5.4b | Modrinth `create-enchantment-industry` | LGPL-3.0-or-later |  |
| `apothic_enchanting` | 1.6.4 | Modrinth `apothic-enchanting` | MIT |  |
| `experiencelib` | 1.2.1 | Modrinth `experiencelib` | LGPL-3.0 (jar), GPL-3.0 (Modrinth) |  |
| `appex` | 1.4.0-build-15 | Modrinth `applied-experienced` | LGPL-3.0 (jar), GPL-3.0 (Modrinth) |  |
| `sophisticatedcore` | 1.5.38.2484 | local | All Rights Reserved |  |
| `aether` | 1.5.10 | local | LGPL-3.0 code, All Rights Reserved assets |  |
| `deep_aether` | 1.1.5.1 | Modrinth `deep-aether` | GPL-3.0 code, All Rights Reserved assets (jar); LGPL-3.0 (Modrinth) |  |
| `twilightforest` | 4.8.3345 | local | LGPL-2.1 |  |
| `biomeswevegone` | 2.6.3 | Modrinth `oh-the-biomes-weve-gone` | All Rights Reserved |  |
| `undergarden` | 0.9.7 | local | MIT (jar), All Rights Reserved (Modrinth) |  |
| `unusualend` | 2.3.1b | Modrinth `unusual_end` | All Rights Reserved (jar), GPL-3.0-or-later (Modrinth) |  |
| `mega_showdown` | 1.3.3 | Modrinth `cobblemon-mega-showdown` | Mega Showdown License v2.1 |  |
| `allthemodium` | 3.2.1 | local | All Rights Reserved |  |
| `deeperdarker` | 1.4.1 | Modrinth `deeperdarker` | GPL-3.0 (jar), AGPL-3.0 (Modrinth) |  |
| `yungscavebiomes` | 3.1.1 | Modrinth `yungs-cave-biomes` | All Rights Reserved |  |
| `handcrafted` | 4.0.3 | Modrinth `handcrafted` | Terrarium Licence |  |
| `cobblefurnies` | 1.2 | Modrinth `cobblefurnies` | MIT |  |
| `valhelsia_furniture` | 1.1.1 | Modrinth `valhelsia-furniture` | All Rights Reserved |  |
| `betterarcheology` | 1.3.9 | Modrinth `better-archeology` | MIT |  |
| `farmersdelight` | 1.3.4 | Modrinth `farmers-delight` | MIT |  |
| `create_central_kitchen` | 2.6.2b | Modrinth `create-central-kitchen` | LGPL-3.0-or-later |  |
| `ironfurnaces` | 4.3.2 | Modrinth `iron-furnaces` | Apache-2.0 |  |
| `pneumaticcraft` | 8.2.24 | Modrinth `pneumaticcraft-repressurized` | GPL-3.0 (jar), LGPL-2.1 (Modrinth) |  |
| `cookingforblockheads` | 21.1.24 | Modrinth `cooking-for-blockheads` | All Rights Reserved |  |
| `forbidden_arcanus` | 2.6.1 | Modrinth `forbidden-arcanus` | All Rights Reserved |  |
| `easymagic` | 21.1.4 | Modrinth `easy-magic` | MPL-2.0 |  |
| `enchantinginfuser` | 21.1.4 | Modrinth `enchanting-infuser` | MPL-2.0 |  |
| `quark` | 4.1-488 | Modrinth `quark` | CC BY-NC-SA 3.0 |  |
| `geore` | 6.2.4 | Modrinth `geore` | MIT |  |
| `dynamictrees` | 1.7.2 | Modrinth `dynamictrees` | MIT |  |
| `createdieselgenerators` | 1.3.15 | Modrinth `create-diesel-generators` | MIT |  |
| `create_new_age` | 1.2.0 | Modrinth `create-new-age` | BSD-3-Clause |  |
| `mysticalagriculture` | 8.0.29 | Modrinth `mystical-agriculture` | MIT |  |
| `actuallyadditions` | 1.3.26 | Modrinth `actually-additions` | MIT |  |
| `kotlinforforge` | 5.13.0 | Modrinth `kotlin-for-forge` | LGPL-2.1 | `cobblemon_incubator` |
| `bookshelf` | 21.1.81 | Modrinth `bookshelf-lib` | LGPL-2.1 | `botanypots` |
| `prickle` | 21.1.11 | Modrinth `prickle` | LGPL-2.1 | `botanypots` |
| `cobbreeding` | 2.4.1 | Modrinth `cobbreeding` | MIT (jar), MIT with no-AI and no-reupload terms (Modrinth) | `cobblemon_incubator` |
| `architectury` | 13.0.12 | Modrinth `architectury-api` | LGPL-3.0 | `cobblemon_incubator`, `mega_showdown`, `cobblefurnies`, `betterarcheology` |
| `curios` | 9.5.1 | Modrinth `curios` | LGPL-3.0-or-later | `ars_nouveau`, `irons_spellbooks` |
| `geckolib` | 4.9.3 | Modrinth `geckolib` | MIT | `ars_nouveau`, `irons_spellbooks`, `biomeswevegone`, `allthemodium`, `yungscavebiomes` |
| `playeranimator` | 2.0.4 | Modrinth `playeranimator` | MIT | `irons_spellbooks` |
| `irons_lib` | 2.3.0 | Modrinth `irons-lib` | All Rights Reserved | `irons_spellbooks` |
| `create_dragons_plus` | 1.11.9 | Modrinth `create-dragons-plus` | LGPL-3.0-or-later | `create_enchantment_industry`, `create_central_kitchen` |
| `placebo` | 9.9.4 | Modrinth `placebo` | MIT | `apothic_enchanting` |
| `apothic_attributes` | 2.11.0 | Modrinth `apothic-attributes` | MIT | `apothic_enchanting` |
| `glodium` | 2.2 | Modrinth `glodium` | MIT | `extendedae` |
| `ae2wtlib` | 19.5.1 | Modrinth `applied-energistics-2-wireless-terminals` | MIT | `appex` |
| `blueprint` | 8.2.0 | Modrinth `blueprint` | Abnormals License | `unusualend` |
| `accessories` | 1.1.0-beta.54 | Modrinth `accessories` | MIT | `mega_showdown`, `aether` |
| `owo` | 0.12.15.6-beta.1 | Modrinth `owo-lib` | MIT | `mega_showdown`, `aether` |
| `yungsapi` | 5.1.9 | Modrinth `yungs-api` | LGPL-3.0 | `yungscavebiomes` |
| `terrablender` | 4.1.0.8 | local | LGPL-3.0 | `biomeswevegone`, `yungscavebiomes`, `deep_aether` |
| `corgilib` | 5.0.0.10 | Modrinth `corgilib` | LGPL-3.0 | `biomeswevegone` |
| `ohthetreesyoullgrow` | 5.3.6 | Modrinth `oh-the-trees-youll-grow` | LGPL-3.0 | `biomeswevegone` |
| `resourcefullib` | 3.0.12 | Modrinth `resourceful-lib` | MIT | `handcrafted` |
| `resourcefulconfig` | 3.0.11 | Modrinth `resourceful-config` | MIT | `betterarcheology` |
| `valhelsia_core` | 1.1.6 | local | All Rights Reserved | `valhelsia_furniture`, `forbidden_arcanus` |
| `balm` | 21.0.67 | Modrinth `balm` | All Rights Reserved | `cookingforblockheads` |
| `puzzleslib` | 21.1.66 | Modrinth `puzzles-lib` | MPL-2.0 | `easymagic`, `enchantinginfuser` |
| `zeta` | 1.1-40 | Modrinth `zeta` | CC BY-NC-SA 3.0 | `quark` |
| `cucumber` | 8.0.16 | Modrinth `cucumber` | MIT | `mysticalagriculture` |

The texts of the custom licenses were read in full. Each one allows a private, unmodified download for a local test
server that is never redistributed:

- Blu's License of Common Sense (Immersive Engineering) forbids redistribution, monetization and claims of ownership,
  and does not restrict use.
- The Railcraft Reborn Mod License forbids the distribution of modified versions and distribution on another site, and
  does not restrict private use.
- The Mega Showdown License v2.1 forbids commercial use and asks for approval before redistribution on public
  platforms.
- The Terrarium Licence (Handcrafted) puts the code under MIT and keeps all other files All Rights Reserved.
- The Abnormals License (Blueprint) allows use as a library from the authors' own sources, and forbids publication and
  distribution.
- Cobbreeding's Modrinth terms add to MIT: no training of machine-learning models, and no re-upload to monetized
  platforms without consent.
- The terms of Twelve Iterations (Balm, Cooking for Blockheads) allow use in mod packs and forbid public re-upload.
- Iron's Spells 'n Spellbooks allows use in a mod pack and forbids the use of its assets outside the mod.

For an All Rights Reserved mod, a download from the author's own page for a local test is the use that the author offers
(ADR-0016). Each local pin is a copy of the author's own upload. The script keeps every jar under `run/testpack/`, which
git and CI artifacts never hold.

## Files

| Path | Content |
|---|---|
| `scripts/testpack/mods.json` | The pins: version, file name, URL and SHA-512 of each download. A local pin has no URL. The file also holds the expected counts of `crops`. |
| `run/testpack/cache/` | Downloads and the copies of the local pins. The script deletes a file whose SHA-512 does not match `mods.json`. |
| `run/testpack/base/neoforge-<version>/` | One NeoForge server install for each version. |
| `run/testpack/<profile>/` | One server for each run: `smoke/`, `reload/`, `restart/`, `crops/`, `mods/`, `deps-<case>/` and `compat_<mod>/`. The script makes it again from the base install on each run. |

A profile has `logs/latest.log` and `console.log` (the server stdout and stderr). After a restart, the
first run's files are `logs/before-restart.log` and `before-restart.log`. Profiles use hard
links to the large jars where the file system allows it. Each server listens on 127.0.0.1 only,
with `online-mode=false`, free ports and a random RCON password.

## Change a pin

1. Edit the `version` of the pin in `mods.json`.
2. Run `py scripts/testpack/testpack.py resolve`.
3. Run `smoke` and `deps`.
4. Commit `mods.json`.

## Add a scenario

Add a function `scenario_<name>(server)` to `testpack.py`. The function becomes the command
`<name>`. The script installs a fresh profile with the pinned mods, starts the server, calls the
function and then stops the server. A scenario in `SCENARIO_SECTIONS` also gets the pins of the
`mods.json` section that the map names for it, as `crops` gets `crops` and `mods` gets `optional`. A
pin of that section replaces the pinned mod with the same key. `SCENARIO_HEAP` gives a scenario a
larger heap. To fail the scenario, raise `TestpackError` with a one-line reason.

The `Server` object has these methods:

- `wait_log(pattern, timeout, count=1)` waits until `count` lines of `logs/latest.log` match the
  regular expression and returns the last of them. It searches from the first line of the log.
- `find_log(pattern)` returns the first matching line so far, or `None`.
- `count_log(pattern)` returns the number of matching lines so far. Pass it plus one to `wait_log` to
  wait for the next occurrence of a line.
- `rcon(command)` runs a console command over RCON and returns the reply.
- `stop()` sends `stop` over RCON and waits for the process to end.
- `restart()` stops the server and starts it again in the same folder. The log methods then read the
  new run only.

A dedicated test server has no player, so a scenario cannot pasture a Cobblemon. Checks that need a
worker stay in the GameTests.
