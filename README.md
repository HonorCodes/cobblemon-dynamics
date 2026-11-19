# Cobblemon Dynamics

Cobblemon Dynamics puts your pastured Cobblemon to work. Each Cobblemon runs a machine
or tends a crop that matches its type. For example, it turns a Create water wheel,
generates Forge Energy, fuels a furnace or harvests wheat. The design follows the base workers of Palworld: you place
Cobblemon around your base, and each one powers a specific machine.

Cobblemon Dynamics is an addon for [Cobblemon](https://cobblemon.com) on NeoForge
1.21.1. It also works with [Create](https://github.com/Creators-of-Create/Create) and
[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2),
with any mod that uses NeoForge energy, fluid or item capabilities, and with the machines
and crops of many other mods, such as Immersive Engineering, Mekanism and Botany Pots.

## Status

**Version 1.2.0 is the latest release.** Download it from the
[GitHub releases](https://github.com/HonorCodes/cobblemon-dynamics/releases) or CurseForge. Version 1.2 adds four
roles: the [Lumberjack](#lumberjack), the Soul Collector with its [Experience Collector](#experience-collector), the
[Freezer](#freezer) and the Heater. The existing roles also work the machines and crops of many other mods, with no
setup (see [Compatibility](#compatibility)). Version 1.1 added two farming roles, the Cultivator and the Harvester (see
[Farming](#farming)).

The design is in the [spec sheet](docs/plans/2026-11-09-v1-spec-sheet.md). The release plans are the
[1.0.0 plan](docs/plans/2026-11-11-phase-3-release.md), the [1.1 plan](docs/plans/2026-11-12-v1.1-farming.md) and the
[1.2 plan](docs/plans/2026-11-15-v1.2-new-roles-and-compat.md).

Version 1.0 shipped in three phases:

| Phase | Scope | Gate |
| --- | --- | --- |
| 1, private | The 11 v1 roles, Work Stamina, beds and healers (milestones M0 to M5) | Every v1 requirement passes, and the author tests it in a private world |
| 2, private | The 4 v2 roles and the final art (M6 to M8) | Every v2 requirement passes, and the author tests it again |
| 3, public | The public release (M9) | — |

## How it works

1. Put Cobblemon in a Cobblemon Pasture Block.
2. Open the pasture and turn on **Work Mode**.
3. Each Cobblemon looks for a machine or a crop in the pasture's **work area** that
   matches its type. It walks to its target, faces it, and loops a move effect while it
   works.

Rules that always apply:

- **One worker per block, except for logistics and crops.** Only one Cobblemon can work a
  given machine, across all pastures. The logistics roles share their containers: up to 5
  Cobblemon of the Magnet and Telekinesis roles together can use one chest at once, for
  example a Magnet that fills it and a Telekinesis worker that empties it. They never block
  the machine's own worker on the same block. The server config key
  `logistics.maxWorkersPerContainer` changes the 5. Up to 5 Soul Collectors share one
  Experience Collector in the same way. A crop takes one Cultivator and one Harvester at
  once, and a sapling takes one Cultivator and one Lumberjack.
- **Only pastured Cobblemon work, with one exception.** A Cobblemon works only while it is in
  a pasture with Work Mode on. The exception is Quick Work: a party Cobblemon that you send
  out with R while you look at a block works that block. The
  [player guide](docs/player-guide.md#quick-work) explains when it stops. Wild Cobblemon
  never work.
- **The pasture owner controls Work Mode.** Only the player who placed the pasture, or
  an operator, can turn Work Mode on or off.

### The Work Leash

Workers pick the nearest free machine or crop on their own. Use the **Work Leash** to
give a specific Cobblemon a specific target. Some roles work only through the leash.

| Action | Result |
| --- | --- |
| Right-click your pastured Cobblemon, then right-click a machine, a crop or a tree | Binds the Cobblemon to that machine, crop or tree |
| Right-click a Psychic-type, then a source container, then a destination | Binds a Telekinesis worker. The faces that you click are the faces it uses. |
| Sneak + right-click the Cobblemon | Clears its binding |
| Sneak + right-click a driven water wheel or hand crank | Flips the direction of that wheel or crank |
| Sneak + right-click an encased fan with a Fire-type catalyst | Switches that fan between blasting and smoking |

Only the player who bound the worker, the pasture's owner or an operator can flip a
target. A leash binding takes the machine from a Cobblemon that the mod assigned
automatically. Bindings stay through server restarts. A binding clears when the
Cobblemon leaves its pasture (recall, another pasture, release, trade, or a broken
pasture block), or when the machine is removed (broken, replaced, picked up with a
wrench, or moved into a contraption). A leashed Cobblemon stays on its machine. A
Cobblemon without a leash moves to another machine after it waits 10 seconds with
nothing to do and another machine needs it.

A Telekinesis source must sit in the pasture's work area. Its destination can sit
anywhere within 32 blocks of the pasture block.

## Roles

A role is a job that a Cobblemon of a given type does on a specific block. A Cobblemon
with two types can do the roles of either type, one role at a time.

| Type | Role | Works on | Move effect | Assignment | Added in |
| --- | --- | --- | --- | --- | --- |
| Water | Wheel Driver | Create water wheel, large water wheel, and the Immersive Engineering watermill since 1.2 | Water Gun | Auto | Phase 1 |
| Water | Pump | Fluid tanks, boilers, basins, cauldrons, and farmland since 1.2 | Water Gun | Leash only | Phase 1 |
| Electric | Generator | Any block that accepts Forge Energy, AE2 machines included, and Cobblemon's Healing Machine since 1.2 | Thunder Shock | Auto | Phase 1 |
| Fire | Stoker | Furnace, smoker, blast furnace, and the furnaces of other mods since 1.2 | Ember | Auto | Phase 1 |
| Fire | Brew Stoker | Brewing stand (fuel) | Ember | Auto | Phase 1 |
| Dragon | Superheater | Create Blaze Burner (kindled below level 50, superheated from level 50), and the Liquid Blaze Burner since 1.2 | Dragon Breath | Auto | Phase 1 |
| Fighting, Normal | Cranker | Create hand crank, and the crank wheels of Create: Connected and the AE2 crank since 1.2 | Pound | Auto | Phase 1 |
| Rock | Crystal Tender | Budding amethyst, AE2 budding certus quartz, and budding tumblestones and the crystals of other mods since 1.2 | Rock Throw | Auto | Phase 1 |
| Poison | Brewer | Brewing stand (speed), and the Alchemist Cauldron since 1.2 | Poison Gas | Auto | Phase 1 |
| Fairy | Healer | Tired or low allies in the same pasture | Special pose, heal circles | Auto | Phase 1 |
| Fairy | Muse | Enchanting table: bookshelf power, or Quanta with Apothic Enchanting, and the tables and infusers of other mods since 1.2 | Fairy Wind | Leash or Quick Work | Phase 1 |
| Psychic | Telekinesis | A source container and a destination, AE2 ME Interface included, and plain containers since 1.2 | Kinesis | Leash only | Phase 2 |
| Steel | Magnet | A container that collects dropped items from the work area | Metal sparks | Leash only | Phase 2 |
| Fire | Fan Catalyst Fire | Create encased fan: blasting or smoking | Fire Spin | Leash only | Phase 2 |
| Water | Fan Catalyst Water | Create encased fan: washing | Bubble | Leash only | Phase 2 |
| Ghost | Fan Catalyst Ghost | Create encased fan: haunting | Will-O-Wisp | Leash only | Phase 2 |
| Flying | Tailwind | Create windmill bearing, and the windmills of Immersive Engineering and Mekanism and the air of PneumaticCraft since 1.2 | Gust | Leash only | Phase 2 |
| Ground | Cultivator | A growing crop, which gets the effect of bone meal, and saplings since 1.2 | Mud Sport | Auto | 1.1 |
| Grass | Harvester | A fully grown crop, which it harvests and replants | Razor Leaf | Auto | 1.1 |
| Bug and Flying, both | Picker | A ripe apricorn, which it picks off the tree to fall to the ground | Bug Bite | Auto only, no leash | 1.2.1 |
| Bug | Lumberjack | A natural tree, which it fells with its leaves and replants from the drops | Aerial Ace | Leash only | 1.2 |
| Ghost | Soul Collector | An Experience Collector, which it fills with the XP orbs of the work area | Night Shade | Leash only | 1.2 |
| Ice | Freezer | Surface water, which it ices over on top within 5 blocks of itself | Ice Beam | Auto, no Quick Work | 1.2 |
| Fire | Heater | The heat valves of Mekanism, and the heat machines of PneumaticCraft | Ember | Auto on Mekanism, leash only on PneumaticCraft | 1.2 |

A worker of an **Auto** role takes the nearest free target on its own. It also takes a
Work Leash binding or a Quick Work order, except the Healer and the Freezer. A **Leash
only** role needs a Work Leash binding. Dark has no role.

The [spec sheet](docs/plans/2026-11-09-v1-spec-sheet.md) holds the numbers for each
role. The [role catalog](docs/roles.md) covers all 18 types, including later ideas.

## Balance

Cobblemon are a strong early-game and midgame power source, and a fleet of them can
compete. No single Cobblemon replaces an engineering problem that you are meant to
solve. Top-tier engineering, such as a large reactor or AE2 automation, always does
better. For example, a level-100 legendary Generator makes 10,000 FE/t, and a small
Extreme Reactors reactor makes about 15,000 FE/t. Roles on machines that cap their own
output, such as water wheels and windmills, are exempt. Since 1.2, the Wind Generator of
Mekanism and the windmill and the watermill of Immersive Engineering are exempt too: a
worker raises each only up to the machine's own maximum. See
[ADR 0010](docs/adr/0010-balance-rule.md).

## Work Stamina, beds and healers

- **Work Stamina.** Work drains a worker's Work Stamina, and rest restores it.
  Higher-level Cobblemon work longer before they get **Tired**. A Tired Cobblemon stops
  work until it fully recovers.
- **Beds.** A Tired Cobblemon claims the nearest free bed in its work area, walks to it
  and sleeps. Four bed tiers cut the recovery time: the Hay Bed, Wool Bed, Plush Bed
  and Starlit Bed. You craft each tier from the tier below it.
- **Healers.** A Fairy-type with no machine binding acts as a healer. It walks to a
  Tired ally in the same pasture and restores that ally's Work Stamina. Each heal
  costs the healer some of its own Work Stamina. Fairy-types cannot heal other
  Fairy-types.

The spec sheet areas STA, BED and HEAL hold the exact rules and numbers.

## Charge Cell

The **Charge Cell** is a block that stores up to 50,000 FE. A Generator charges it
as it charges any block that takes FE, and each tick the cell pushes its FE into the
blocks around it. A comparator reads how full it is, and a broken cell keeps its FE
on the item. The spec sheet area GEN holds the exact rules.

## Experience Collector

Version 1.2 adds the **Experience Collector**, a block that stores up to 100,000 XP by
default (the server config key `experienceCollector.capacity`). A
Ghost-type **Soul Collector** bound to it with the Work Leash takes the XP orbs of its
pasture's work area into it, at a pace that grows with level. It never takes an orb within
8 blocks of a player, so your XP and your Mending repairs stay yours, and it leaves the
orbs of a player's death for 5 minutes.

- Right-click the collector to open its screen, which shows the stored XP in points and
  levels. Press **Collect experience** to take all of it.
- The back and the bottom are outputs for pipes. Pipes drain the XP as the XP fluid of
  your pack: Create: Enchantment Industry's liquid experience, or else the first fluid of
  `c:experience`. The mod adds no fluid of its own, and pipes cannot fill the collector.
- With experiencelib installed, an Applied Experienced import bus takes the XP into an ME
  network.
- A comparator reads how full it is, and a broken collector keeps its XP on the item.

The spec sheet areas XPC and SOU hold the exact rules. See
[the player guide](docs/player-guide.md#experience-collector) and
[ADR 0018](docs/adr/0018-experience-collector-and-soul-collector.md).

## Farming

Version 1.1 adds two roles that work crops. Both work vanilla crops, Cobblemon's crops
and berry trees, and the crops of many other mods.

- **The Cultivator (Ground).** It gives a growing crop the effect of bone meal, with no
  bone meal item. Each use costs extra Work Stamina, so a Cultivator tires faster than
  other workers. It stops when the crop is fully grown.
- **The Harvester (Grass).** It harvests a fully grown crop and drops the harvest on the
  ground beside the crop. A Magnet bound to a chest of the same pasture collects the
  drops. A crop that the harvest breaks is replanted from its own drops.
- **Crop to crop, or one crop.** A worker with no binding goes crop to crop. Bind it with
  the Work Leash to keep it on one crop. A bound Cultivator grows its crop again after
  each harvest, and a bound Harvester waits until the crop is fully grown again.
- **Apricorns.** By default, the Cultivator skips apricorns, and a data pack can add
  them. The Harvester never takes an apricorn.
- **The Picker (Bug and Flying).** Since 1.2.1, a Cobblemon of both types picks ripe
  apricorns off their trees on its own, as a right click does. The apricorns fall to the
  ground for a player or a Magnet. The Work Leash never binds a Picker.
- **Farmland.** A worker that the work engine directs never tramples farmland.

The spec sheet areas CUL, HRV and CRP hold the exact rules. See
[the player guide](docs/player-guide.md#farming) and
[ADR 0015](docs/adr/0015-farming-roles-and-crop-adapters.md).

## Lumberjack

Version 1.2 adds the **Lumberjack**. Bind a Bug-type to a tree or a sapling with the Work
Leash. The worker fells the whole tree with its own natural leaves and with what hangs on
it, silently, and drops the wood beside the trunk for a Magnet. It then plants a sapling
from the drops, and it waits until a tree stands there again. A Ground-type Cultivator
grows the sapling, so the two run a wood line. Leaves that a player placed stay, and the
Lumberjack leaves mangroves, huge fungi, apricorn trees, fruit trees and trees with a bee
nest. With Dynamic Trees installed, a tree of that mod falls through its own fell. The
spec sheet area LUM holds the exact rules. See
[the player guide](docs/player-guide.md#lumberjack) and
[ADR 0017](docs/adr/0017-lumberjack-and-saplings.md).

## Freezer

Version 1.2 adds the **Freezer**. An Ice-type in a Work Mode pasture turns the top layer of
the water in the work area into ice, as a lake freezes, within 5 blocks of itself, and it
walks out over the new ice. The pace grows with level. It leaves the water of a farm, a
bubble column or a stream, the water beside sugar cane, a coral or a conduit, lit water,
and water that holds a swimmer or a boat. It never melts ice: vanilla melts it where the light is bright.
The Freezer takes no Quick Work order. The spec sheet area FRZ holds the exact rules. See
[the player guide](docs/player-guide.md#freezer) and
[ADR 0019](docs/adr/0019-freezer-area-job.md).

## Art

All block models, textures, sprites and icons are original and drawn for this mod in
the vanilla 16×16 style. The mod uses no art from Mojang, Cobblemon, Create or AE2.
The one exception is the mod logo, which is the project owner's own image. Seven
state icons show what each worker does: Idle, Traveling, Working, Standby, Tired,
Sleeping and Healing. Source files live in `art/`. See
[ADR 0012](docs/adr/0012-original-art-assets.md).

## Requirements

Install Cobblemon Dynamics on both the client and the server.

| Dependency | Version | Required |
| --- | --- | --- |
| Minecraft | 1.21.1 | Yes |
| NeoForge | 21.1.219 or later | Yes |
| Java | 21 | Yes |
| Cobblemon | 1.8.4 or a later 1.8.x release | Yes |
| KotlinForForge | 5.10 or later | Yes |
| Create | 6.0.10 or a later 6.0.x release | No. It turns on the Create roles. |
| Applied Energistics 2 | 19.2 or a later 19.x release | No. The Generator powers AE2 machines through FE, the Crystal Tender grows AE2 budding certus quartz, Telekinesis feeds ME Interfaces, and the Cranker works the AE2 crank. |
| The mods of [the integration page](docs/modpack-integration.md#mods-that-the-roles-support) | Any. The page names the tested version of each. | No. Each one turns on its own targets, with no setup. |

There is no Fabric build. Create 6 and AE2 for Minecraft 1.21.1 are NeoForge-only. See
[ADR 0002](docs/adr/0002-neoforge-1.21.1-only.md).

## Building

You need JDK 21. Set `JAVA_HOME` to it.

| Task | Command |
| --- | --- |
| Build the jar, run the unit tests and the jar checks | `./gradlew build` |
| Run the GameTests | `./gradlew runGameTestServer` |
| Start a development client | `./gradlew runClient` |
| Start a clean test server with the built jar | `py scripts/testpack/testpack.py smoke` |

The jar is written to `build/libs/`.

## Art sources

Every texture and model of the mod ships from a source under `art/`. A texture source
(`.pixels`) is text: its size, a palette of one character per color, and one row of
characters per pixel row. Edit a source, then run `py scripts/art.py build`, which
writes the shipped files under `src/main/resources/assets/`. `py scripts/art.py check`
checks the sizes, the sources, the models and the license, and `--references auto`
also compares every texture with the textures of Minecraft, Cobblemon, Create and
AE2, tile by tile. `py scripts/art.py review` writes the review spritesheet
`build/art-review/spritesheet.png`. `py scripts/art.py import PNG SOURCE` turns a PNG
into a source. The art is under the repository's MPL-2.0 license.

## Compatibility

- **Cobbleworkers and Cobblebase.** These mods also give jobs to pastured Cobblemon. In a
  pasture with Work Mode on, Cobblemon Dynamics turns their jobs off for that pasture.
  Pastures with Work Mode off keep their normal Cobbleworkers or Cobblebase jobs. See
  [ADR 0009](docs/adr/0009-coexistence-with-pasture-job-mods.md).
- **Modded furnaces.** The Stoker works with any furnace that extends the vanilla
  furnace.
- **Apothic Enchanting.** Its enchanting table reads no bookshelf count, so there a Muse
  adds Quanta to the table's stats instead of bookshelves. See
  [the player guide](docs/player-guide.md#muse).
- **Modded machines.** The Generator works with any block that accepts Forge Energy
  through NeoForge's energy capability. The Pump works with any block that accepts
  water through NeoForge's fluid capability. Telekinesis and the Magnet work with any
  block that has NeoForge's item capability, and since 1.2 with a plain vanilla container
  that has none, such as Cobblemon's Gilded Chest and the containers of furniture mods.
- **The machines of other mods (1.2).** The roles work these mods with no setup: no config
  key, no data pack and no command. Each target works only while its mod is installed.
  - Immersive Engineering: the Stoker on the alloy kiln and the blast furnaces, the Wheel
    Driver on the watermill, and Tailwind on the windmill.
  - Mekanism and Mekanism Generators: the Stoker on the Fuelwood Heater, the Heater on the
    Thermal Evaporation Plant and the Thermoelectric Boiler, and Tailwind on the Wind
    Generator.
  - Railcraft Reborn: the Stoker on the blast furnace and the solid-fueled boilers.
  - PneumaticCraft: Repressurized: the Stoker on the compressors, the Heater on its heat
    machines, and Tailwind on its air machines.
  - Iron Furnaces, Cooking for Blockheads, Forbidden & Arcanus, Cobblemon Incubator and
    Ars Nouveau: the Stoker on their furnaces, the Oven, the Clibano, the Egg Incubator and
    the Volcanic Sourcelink.
  - Create Crafts & Additions, Create: Connected and AE2: the Superheater on the Liquid
    Blaze Burner, and the Cranker on the crank wheels and the AE2 crank.
  - Botany Pots: the Cultivator and the Harvester on its pots.
  - Iron's Spells 'n Spellbooks: the Brewer on the Alchemist Cauldron.
  - Easy Magic, Enchanting Infuser and Create: Enchantment Industry: the Muse on their
    tables and infusers.
  - Dynamic Trees: the Lumberjack through its own fell.
  - Quark, Unusual End and GeOre: the Crystal Tender on their crystals.

  The [modpack integration page](docs/modpack-integration.md#mods-that-the-roles-support)
  lists each mod with its tested version, its role files and its targets, and the
  [player guide](docs/player-guide.md#other-mods) tells what each role does there. See
  [ADR 0016](docs/adr/0016-zero-setup-optional-mod-compatibility.md) and
  [ADR 0020](docs/adr/0020-machines-and-crops-of-other-mods.md).
- **Land claims.** With FTB Chunks, Open Parties and Claims or MineColonies on the server,
  Quick Work and the workers of Work Mode pastures follow their land claims. Quick Work
  asks about the player who sends the Cobblemon out. A pasture asks about its owner, also
  while that player is offline. Each claim mod decides by its own rules, which include
  its rules for teams, parties and allies. By default, an ally can use the machines in a
  land claim of FTB Chunks, but not in a land claim of Open Parties and Claims. A
  harvest that breaks a block also asks each claim mod whether the player may break
  that block. See [the player guide](docs/player-guide.md#land-claims) and
  [ADR 0014](docs/adr/0014-land-claims-for-work-mode-pastures.md).
- **Modded crops.** The Cultivator grows the blocks of the tag
  `#cobblemon_dynamics:cultivator_targets`, and the Harvester harvests the blocks of
  `#cobblemon_dynamics:harvester_targets`. Both tags hold `#minecraft:crops`, so they
  include the crops of each mod that tags its crops. A data pack can add blocks to them.
  Version 1.2 adds the crops of more mods, such as the berry bushes of the Twilight
  Forest and the pods of Ars Nouveau. The
  [modpack integration page](docs/modpack-integration.md#farming-roles) lists the crops of
  each supported mod.

The spec sheet areas CMP, QWK, LND, CRP, OPT, FIX, FUEL, AIR, KIN, HTR, FRM and MCH, and
the row CD-MUS-07, hold the exact compatibility rules.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/player-guide.md](docs/player-guide.md) | How to play: Work Mode, the Work Leash, Quick Work, land claims, Work Stamina, beds, healers, the Charge Cell, the Experience Collector, farming, every role, config, commands, other mods and known limits |
| [docs/modpack-integration.md](docs/modpack-integration.md) | For pack and server maintainers: dependencies, server defaults, claim mods, farming roles, the mods that the roles support with their tags and load lines, mixins, payloads, saved data and what is tested |
| [docs/testing/manual-checklist.md](docs/testing/manual-checklist.md) | The manual play-test checklist for the Demo rows |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Components, data flow, integration points, art and milestones |
| [docs/roles.md](docs/roles.md) | Role catalog for all 18 types: Phase 1, Phase 2, the 1.1 and 1.2 roles, the machines of other mods and later ideas |
| [docs/plans/2026-11-09-v1-spec-sheet.md](docs/plans/2026-11-09-v1-spec-sheet.md) | Requirements for 1.0, 1.1 and 1.2, each with an acceptance test |
| [docs/research/2026-11-09-integration-points.md](docs/research/2026-11-09-integration-points.md) | Cobblemon, Create, AE2 and NeoForge hooks, with pinned versions |

Architecture decision records:

| ADR | Decision |
| --- | --- |
| [0001](docs/adr/0001-pasture-work-mode-instead-of-work-stand.md) | Pasture Work Mode instead of a Work Stand block |
| [0002](docs/adr/0002-neoforge-1.21.1-only.md) | NeoForge 1.21.1 only |
| [0003](docs/adr/0003-standalone-mod-not-cobbleworkers-addon.md) | A standalone mod, not a Cobbleworkers addon |
| [0004](docs/adr/0004-brain-activity-for-worker-control.md) | A Brain activity for worker control |
| [0005](docs/adr/0005-global-claim-registry.md) | A global claim registry |
| [0006](docs/adr/0006-data-driven-roles.md) | Data-driven roles |
| [0007](docs/adr/0007-effects-through-cobblemon-packets.md) | Effects through Cobblemon packets |
| [0008](docs/adr/0008-work-stamina-model.md) | The Work Stamina model |
| [0009](docs/adr/0009-coexistence-with-pasture-job-mods.md) | Coexistence with pasture job mods |
| [0010](docs/adr/0010-balance-rule.md) | The balance rule |
| [0011](docs/adr/0011-v2-roles-in-first-release.md) | The v2 roles in the first release, in phases |
| [0012](docs/adr/0012-original-art-assets.md) | Original art assets |
| [0013](docs/adr/0013-quick-work-for-party-pokemon.md) | Quick Work for party Cobblemon |
| [0014](docs/adr/0014-land-claims-for-work-mode-pastures.md) | Land claims for Work Mode pastures |
| [0015](docs/adr/0015-farming-roles-and-crop-adapters.md) | Farming roles and crop adapters |
| [0016](docs/adr/0016-zero-setup-optional-mod-compatibility.md) | Zero-setup compatibility with optional mods |
| [0017](docs/adr/0017-lumberjack-and-saplings.md) | The Lumberjack, and saplings for the Cultivator |
| [0018](docs/adr/0018-experience-collector-and-soul-collector.md) | The Experience Collector and the Soul Collector |
| [0019](docs/adr/0019-freezer-area-job.md) | The Freezer, an area job |
| [0020](docs/adr/0020-machines-and-crops-of-other-mods.md) | Machines and crops of other mods, the Heater, and fixes |

## Contributing

Open an [issue](https://github.com/HonorCodes/cobblemon-dynamics/issues) to discuss a
change before you open a pull request. A design change starts as a change to the spec sheet.

## License

The code and the art are licensed under the [Mozilla Public License 2.0](LICENSE).

## Disclaimer

Cobblemon Dynamics is an unofficial addon. It is not affiliated with or endorsed by
the Cobblemon team, The Pokémon Company, Nintendo, Game Freak, Creatures, the Create
team or the Applied Energistics 2 team. Pokémon is a trademark of Nintendo, Creatures
and Game Freak.
