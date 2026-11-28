# ADR-0016: Zero-setup compatibility with optional mods

Status: Accepted (2026-11-15) by the coordinator under the approver's delegation, with the rulings (R81) on the design
decisions of the
[1.2 plan](../plans/2026-11-15-v1.2-new-roles-and-compat.md#design-decisions). Inputs R77, R78, R79 and R80 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) hold the rulings.

## Context

The approver, 2026-11-15 (R77), after a question about Mekanism's wind generator:

> does flying types also power mekanikism windmill? it should

> are there other gaps like that? we need broad compatibility

On the list of compatibility items, the approver answered: "approved all the M-compat audit recommendations". On setup
(R78):

> for the roles, it should be automatic. there should be little to no setup that the server admin needs to do to get
> everything working. we should have values for everything and I do not mind if things are a bit unbalanced between the
> mods. it's fine.

The coordinator's reading of R78 (R79):

- Every supported mod works out of the box with built-in values.
- Admins never need config or data packs. Tags and adapter lists ship complete, with `"required": false` entries, and exist
  only for overrides.
- Roles and targets for an optional mod switch on only when that mod is loaded.
- Cross-mod balance may be rough. Every new role or target still gets a value and a benchmark (ADR-0010), kept simple.
- The reading does not change the Work Leash rules: the Lumberjack and the Soul Collector stay leash only, and the Freezer
  is automatic.

The coordinator's rulings on the two compatibility audits (R80):

1. Build every "build" and "build with a caveat" row of both audits, except the new fan processing types (Dragons Plus
   freezing, sanding and ending, CEI salvaging, Aquatic Ambitions channeling), every Rancher, animal and Pollinator row, the
   Ice cooling row of PneumaticCraft, the XP Tome, the 11 solid-fuel FE generators with the Powah Furnator, every row that
   the audits mark skip, and every mod with no 1.21.1 build (Thermal, Tinkers' Construct, Botania).
2. Fix six bugs of today's release, each as its own spec row ([ADR-0020](0020-machines-and-crops-of-other-mods.md)).
3. Values: Tailwind on the Mekanism Wind Generator reaches the 480 J/t maximum at level 100 and keeps Mekanism's sky rule.
   The Heater targets the Thermal Evaporation valve, the Mekanism boiler valves and PneumaticCraft heat, and never fission
   or fusion ports, the Heat Generator or the Entangloporter. Tailwind into PneumaticCraft air stops below each machine's
   danger pressure. Iron Furnaces and other faster furnaces get free fuel at their own speed, with no extra cap. Every other
   target follows the benchmark column of its audit, kept simple.
4. The XP machines of Ender IO, Industrial Foregoing, Cyclic and Just Dire Things are pipe destinations of the Experience
   Collector, not Soul Collector targets ([ADR-0018](0018-experience-collector-and-soul-collector.md)).
5. The plan groups the compatibility tasks by role or by mod, lists the rows of each task, gives the fixes their own
   task, and pins each mod's version.
6. The design proposes a practical compatibility test approach, and checks whether the test pack may download each mod
   outside COHO.

### The engine today

- Create's handlers register only behind `ModList.isLoaded("create")`. A role file whose handler is not registered is
  disabled with one INFO line (`RoleSet`), and the roles line counts it as disabled (CD-CFG-10, CD-CMP-10).
- `MixinPlugin` skips the mixin package of an absent mod (`create`, `cobblebase`, `apothic_enchanting`). It also skips a
  mixin whose target lacks a member that the mixin shadows. `MixinGate` turns off the roles of a handler whose mixin did not
  inject, with one warning.
- `RoleRegistry.disableHandler` turns off every role of a handler whose hook threw, until the role files load again.
- Only `compat/ftbchunks/`, `compat/opac/`, `compat/minecolonies/` and `compat/apothic/` name a class of a claim mod or of
  Apothic Enchanting. Each loads behind `ModList.isLoaded`. `CompatImportsTest` checks the package prefixes, and `verifyJar`
  refuses the classes of those mods in the jar.
- `neoforge.mods.toml` declares Create and AE2 with version ranges, and the other optional mods with the range `*`.

### Research (RES9, RES13)

- NeoForge 21.1.265 does not patch `SimpleJsonResourceReloadListener.scanDirectory`. So a `neoforge:conditions` key in a
  role file has no effect today, and `RoleCodec` ignores the unknown key, so the file still loads. NeoForge patches
  `SimplePreparableReloadListener` to extend `ContextAwareReloadListener`, which offers `makeConditionalOps()`.
  `ICondition.conditionsMatched(ops, json)` tests the key `neoforge:conditions`, and `ModLoadedCondition` exists (RES9).
- The table below lists each mod that 1.2 supports with code or data. The versions of the COHO mods come from the jar names
  of COHO's frozen cache of 2026-11-02, and their licenses from the `license` field of each jar's `neoforge.mods.toml`. The
  versions and licenses of the other mods come from the Modrinth API on 2026-11-15: the newest NeoForge 1.21.1 file of each
  project. The mod ids of the COHO mods come from the jars. The mod ids marked "unverified" come from the class packages
  and block ids of the audits.

| Mod | Mod id | Version | Source of the version | License | Test pack source |
| --- | --- | --- | --- | --- | --- |
| Immersive Engineering | `immersiveengineering` | 12.4.2-194 | COHO jar | Blu's License of Common Sense (text not read) | Modrinth |
| Mekanism | `mekanism` | 10.7.19.85 | COHO jar | MIT | Modrinth |
| Mekanism Generators | `mekanismgenerators` | 10.7.19.85 | COHO jar | MIT | Modrinth |
| Railcraft Reborn | `railcraft` | 1.2.17 | COHO jar | its own license (text not read) | Modrinth |
| Botany Pots, with Bookshelf 21.1.81 and Prickle 21.1.11 | `botanypots` | 21.1.44 | COHO jar | LGPL 2.1 | Modrinth |
| Create: Connected | `create_connected` | 1.3.3 | COHO jar | AGPL 3.0 | Modrinth |
| Applied Energistics 2 | `ae2` | 19.2.19 | COHO jar, the build's pin | see the AE2 repository | the build's pin |
| ExtendedAE | `extendedae` | 2.2.43 | COHO jar | LGPL 3.0 | Modrinth |
| Create Crafts & Additions | `createaddition` | 1.7.2 | COHO jar | MIT | Modrinth |
| Cobblemon Incubator, with Cobbreeding 2.4.1 | `cobblemon_incubator` | 1.1.1 | COHO jar | MIT. Cobbreeding: MIT in its jar. On Modrinth, MIT plus two terms ([text](https://fuzuki.dev/~fuzuki/mit-no-ai-no-reupload.txt)): no use to train or improve machine-learning models, and no re-upload without substantial changes to monetized platforms without consent. A mod pack may bundle it. An optional integration and a local test-pack download are neither, and the mod never bundles the jar. | Modrinth |
| Ars Nouveau | `ars_nouveau` | 5.13.5 | COHO jar | GPL | Modrinth |
| Ars Elemental | `ars_elemental` | 0.7.10.4 | COHO jar | LGPL 3.0 | local jar: no Modrinth project found |
| Iron's Spells 'n Spellbooks, with Iron's Lib 2.3.0 | `irons_spellbooks` | 3.16.3 | COHO jar | All Rights Reserved | Modrinth |
| Create: Enchantment Industry | `create_enchantment_industry` | 2.5.4b | COHO jar | LGPL 3.0 or later | Modrinth |
| Apothic Enchanting | `apothic_enchanting` | 1.21.1-1.6.4 | COHO jar, the build's pin | MIT | the build's pin |
| experiencelib | `experiencelib` | 1.2.1 | COHO jar | LGPL 3.0 | Modrinth |
| Applied Experienced | `appex` | 1.3.2 | COHO jar | LGPL 3.0 | Modrinth |
| Sophisticated Core | `sophisticatedcore` | 1.5.33.2470 | COHO jar | All Rights Reserved | Modrinth |
| The Aether | `aether` | 1.5.10 | COHO jar | code LGPL 3.0, assets All Rights Reserved | Modrinth |
| Deep Aether | `deep_aether` | 1.1.5.1 | COHO jar | code GPL 3.0, assets All Rights Reserved | Modrinth |
| The Twilight Forest | `twilightforest` | 4.8.3345 | COHO jar | LGPL 2.1 | local jar: no Modrinth project found |
| Oh The Biomes We've Gone | `biomeswevegone` | 2.6.3 | COHO jar | All Rights Reserved | Modrinth |
| The Undergarden | `undergarden` | 0.9.6 | COHO jar | MIT | Modrinth |
| Unusual End | `unusualend` | 2.3.1b | COHO jar | All Rights Reserved | Modrinth (`unusual_end`) |
| Cobblemon: Mega Showdown | `mega_showdown` | 1.3.3 | COHO jar | Mega Showdown License v2.1 (text not read) | Modrinth |
| Allthemodium | `allthemodium` | 3.2.1 | COHO jar | All Rights Reserved | Modrinth |
| Deeper and Darker | `deeperdarker` | 1.4.1 | COHO jar | GPL 3.0 | Modrinth |
| YUNG's Cave Biomes | `yungscavebiomes` | 3.1.1 | COHO jar | All Rights Reserved | Modrinth |
| Handcrafted | `handcrafted` | 4.0.3 | COHO jar | Terrarium Licence (text not read) | Modrinth |
| CobbleFurnies | `cobblefurnies` | 1.2 | COHO jar | MIT | Modrinth |
| Valhelsia Furniture, with Valhelsia Core 1.1.5 | `valhelsia_furniture` | 1.1.1 | COHO jar | All Rights Reserved | Modrinth |
| Better Archeology | `betterarcheology` | 1.3.9 | COHO jar | MIT | Modrinth |
| Farmer's Delight | `farmersdelight` | 1.3.4 | COHO jar | MIT | Modrinth |
| Create: Central Kitchen | `create_central_kitchen` | 2.6.2b | COHO jar | LGPL 3.0 or later | Modrinth |
| Iron Furnaces | `ironfurnaces` (unverified) | 4.3.2 | Modrinth API | Apache 2.0 | Modrinth |
| PneumaticCraft: Repressurized | `pneumaticcraft` (unverified) | 8.2.24 | Modrinth API | LGPL 2.1 | Modrinth |
| Cooking for Blockheads, with Balm | `cookingforblockheads` (unverified) | 21.1.24 | Modrinth API | All Rights Reserved | Modrinth |
| Forbidden & Arcanus, with Valhelsia Core | `forbidden_arcanus` (unverified) | 2.6.1 | Modrinth API | All Rights Reserved | Modrinth |
| Easy Magic, with Puzzles Lib | `easymagic` | v21.1.4-1.21.1-NeoForge | Modrinth API | MPL 2.0 | Modrinth |
| Enchanting Infuser, with Puzzles Lib | `enchantinginfuser` (unverified) | v21.1.4-1.21.1-NeoForge | Modrinth API | MPL 2.0 | Modrinth |
| Quark, with Zeta | `quark` | 4.1-488 | Modrinth API | CC BY-NC-SA 3.0 | Modrinth |
| GeOre | `geore` (unverified) | 6.2.4 | Modrinth API | MIT | Modrinth |
| Just Dire Things | `justdirethings` (unverified) | 1.5.7 (unverified: no Modrinth project found) | the broad audit | unverified | none: the case leaves it out |
| Dynamic Trees | `dynamictrees` | 1.7.2 | Modrinth API | MIT | Modrinth |
| Create: Diesel Generators | `createdieselgenerators` (unverified) | 1.21.1-1.3.15 | Modrinth API | MIT | Modrinth |
| Create: New Age | `create_new_age` (unverified) | 1.2.0+mc1.21.1 | Modrinth API | BSD 3-Clause | Modrinth |
| Mystical Agriculture, with Cucumber | `mysticalagriculture` (unverified) | 8.0.29 | Modrinth API | MIT | Modrinth |
| Actually Additions | `actuallyadditions` (unverified) | 1.3.26 | Modrinth API | MIT | Modrinth |
| Jade | `jade` | 15.10.6 | Modrinth API | CC BY-NC-SA 4.0 | Modrinth |

Downloads by the test pack. The test pack fetches each pin from its official Modrinth CDN address, checks its SHA-512, and
keeps it under `run/testpack/`, which git and CI artifacts never hold. That is a local test install, not a redistribution.
So the open-source licenses of the table allow it. For an All Rights Reserved or custom license, a download from the
author's own Modrinth page for a local test is the use that the author offers. The texts of the custom licenses were not
read, so the plan's test pack task reads each one before it adds the pin. A mod with no Modrinth project (the Twilight
Forest, Ars Elemental) comes from a local jar in the folder of `TESTPACK_LOCAL_JARS`, as the `crops` case does. Just Dire
Things is in neither source, so the case leaves it out.

## Decision

1. **One handler per mod family.** The code that names a class of an optional mod lives in `compat/<modid>/` and registers
   only behind `ModList.isLoaded("<modid>")`. A new role or target of that mod gets its own handler there, with its own
   role file `cobblemon_dynamics:<role>_<modid>`, for example `stoker_immersiveengineering.json`. ADR-0020 lists them.
2. **Role files with conditions.** Each such role file holds `"neoforge:conditions": [{"type": "neoforge:mod_loaded",
   "modid": "<modid>"}]`. `RoleRegistry.apply` tests the conditions with `ICondition.conditionsMatched(makeConditionalOps(),
   json)` before it parses a file. A file whose conditions fail is not loaded and not disabled. One INFO line per role load
   names those files and their mods, beside the roles line of CD-CMP-10, so the roles line of a server keeps naming only
   real problems. Create's role files keep their handler gate in 1.2, with no condition, so CD-CFG-10 and the test pack's
   case without Create do not change.
3. **Extensions of a role stay in that role.** Where a mod adds targets to an existing role whose rules must stay one role,
   the existing handler reads a tag, a data map or a hook that a compat package registers: the Cranker on Create:
   Connected's crank wheels, the Crystal Tender, the Muse, the Pump, Telekinesis and the Magnet, the farming tags and data
   map, the Superheater's heat users and the Lumberjack on Dynamic Trees.
4. **Complete defaults.** Every built-in tag and data map lists the blocks of the supported mods. Each entry of another mod
   has `"required": false`, or the file holds a condition on that mod. A pack overrides a default with its own data pack,
   and needs none to make a supported mod work.
5. **Mixins.** The mixins of a mod live in `mixin/compat/<modid>/`. `MixinPlugin` maps that package to the mod id and skips
   it without the mod. `MixinGate` requires each mixin for its handler, or for its target inside a shared handler, so a
   mixin that does not apply turns off only what it serves, with one warning.
6. **Fail safe.** A handler of an optional mod runs behind the existing guard: an `Exception` or a `LinkageError` turns off
   its roles until the role files load again, with one warning per handler. A later version of a mod that renames a member
   therefore turns off one target and never crashes the game.
7. **Build.** Each mod of the table is a compile-only dependency with `transitive = false` at the pinned version.
   `verifyJar` refuses its packages in the jar. `neoforge.mods.toml` declares each mod as optional with the range `*`, as
   for the claim mods, so NeoForge checks no version and a failed hook refuses.
8. **Observability.** After each tag load, one INFO line per loaded supported mod names each of its adapters as found or
   missing: a block id that resolves or not, a mixin that applied or not, a class that loaded or not. A pack maintainer
   reads in one line why a target does not work.
9. **Values.** Each role file holds its numbers. Each new role or target has a BAL row with a cap and a benchmark
   (ADR-0010), kept simple. Iron Furnaces and other faster furnaces get no extra cap (R80).
10. **Testing.** The plan tests the compatibility in four layers, from the cheapest:
    1. Unit tests for each adapter's mapping. Each adapter is a pure rule over plain facts of a block or a block entity,
       which the build reads once from the pinned jar with `javap` and `unzip`, as ADR-0015 did for crops.
    2. One test-pack boot, `py scripts/testpack/testpack.py mods`, with every supported mod that the test pack can fetch
       or copy, and their libraries. Its compat lines must name every adapter as found, the roles line every conditional
       role as active, and the mixins line every compat mixin as applied. If two mods conflict in one server, the case
       splits them into the groups that the plan names.
    3. A compat GameTest run, `runGameTestServerCompat`, outside CI, with one GameTest for each adapter that writes a
       mod's private state through a mixin, an accessor or reflection.
    4. One Demo on the COHO server for each COHO mod family, where the approver plays.
11. **Downloads.** The test pack downloads only from Modrinth by SHA-512, or copies a local jar, and never commits a jar.
    The plan's test pack task records the license of each pin.

## Consequences

- A server with none of the supported mods logs one line of skipped role files and no disabled role. A pack with some of
  them gets each mod's targets with no step of the admin.
- The jar ships about 20 role files that most servers never load. They cost nothing at run time.
- Each compat package binds to a mod's internal classes where the mod has no API. A later version of that mod can turn
  a target off, and the compat line names it. The plan pins the tested versions, and `docs/modpack-integration.md` lists
  them.
- The test pack needs more memory for the boot of `mods`. The machine runs it at the gate, with the JVM lock, not in CI.
- CI keeps its mod set (Cobblemon, Create and AE2). The real mods of 1.2 run only in the test pack, the compat GameTest
  run and the Demos.
- Balance between mods is rough by the approver's ruling. A pack that wants another number changes a role file.

## Alternatives considered

- **Handler gates only, with no conditions.** Simple, but every server would log about 20 disabled roles, which reads as
  a setup problem to an admin.
- **Adapters inside the existing handlers for every mod**, for example one Stoker handler with a fuel adapter per mod.
  Rejected as the default: a pack could not turn off one mod's free fuel, and one role file could not hold each mod's
  numbers. It stays the form for the extensions of decision 3.
- **A config switch per mod.** Rejected by R78: no setup.
- **A GameTest run for each mod.** Rejected as too heavy for the shared machine. Only the adapters that write private state
  get GameTests, in one run.
- **CurseForge downloads in the test pack.** Rejected: they need an API key, and some authors forbid third-party downloads.

## Amendment (2026-11-17, Task 23): no compat line for a mod of tags alone

The coordinator's audit of the merged 1.2 code found that the compat line of decision 8 read `[]` for each mod that tags and
data maps alone support, such as the Aether and the Twilight Forest. Such a mod has no adapter to name. Its tag entries are
optional and its data map entries carry a condition, so they load with the mod and drop without it. The mods of the table
that `OptionalMods` does not list logged no line at all.

Now only a mod with a compat package logs a compat line, and each such line names at least one adapter. Apothic Enchanting
names its stats hook as `muse_quanta`. The mixins line still names each compat mixin, and the farming lines still count the
tagged crops of each adapter (CD-CRP-05). `CompatLinesTest` holds the rule.
