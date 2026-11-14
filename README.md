# Cobblemon Dynamics

Cobblemon Dynamics puts your pastured Cobblemon to work. Each Cobblemon runs a machine
or tends a crop that matches its type. For example, it turns a Create water wheel,
generates Forge Energy, fuels a furnace or harvests wheat. The design follows the base workers of Palworld: you place
Cobblemon around your base, and each one powers a specific machine.

Cobblemon Dynamics is an addon for [Cobblemon](https://cobblemon.com) on NeoForge
1.21.1. It also works with [Create](https://github.com/Creators-of-Create/Create) and
[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2),
and with any mod that uses NeoForge energy, fluid or item capabilities.

## Status

**Version 1.1.0 is released.** Download it from the
[GitHub releases](https://github.com/HonorCodes/cobblemon-dynamics/releases) or CurseForge. Version 1.1 adds two
farming roles, the Cultivator and the Harvester (see [Farming](#farming)). The design is in the
[spec sheet](docs/plans/2026-11-09-v1-spec-sheet.md). The release plans are the
[1.0.0 plan](docs/plans/2026-11-11-phase-3-release.md) and the [1.1 plan](docs/plans/2026-11-12-v1.1-farming.md).

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
  `logistics.maxWorkersPerContainer` changes the 5. A crop takes one Cultivator and one
  Harvester at once.
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
| Right-click your pastured Cobblemon, then right-click a machine or a crop | Binds the Cobblemon to that machine or crop |
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
| Water | Wheel Driver | Create water wheel, large water wheel | Water Gun | Auto | Phase 1 |
| Water | Pump | Fluid tanks, boilers, basins, cauldrons | Water Gun | Leash only | Phase 1 |
| Electric | Generator | Any block that accepts Forge Energy, AE2 machines included | Thunder Shock | Auto | Phase 1 |
| Fire | Stoker | Furnace, smoker, blast furnace | Ember | Auto | Phase 1 |
| Fire | Brew Stoker | Brewing stand (fuel) | Ember | Auto | Phase 1 |
| Dragon | Superheater | Create Blaze Burner (kindled below level 50, superheated from level 50) | Dragon Breath | Auto | Phase 1 |
| Fighting, Normal | Cranker | Create hand crank | Pound | Auto | Phase 1 |
| Rock | Crystal Tender | Budding amethyst, AE2 budding certus quartz | Rock Throw | Auto | Phase 1 |
| Poison | Brewer | Brewing stand (speed) | Poison Gas | Auto | Phase 1 |
| Fairy | Healer | Tired or low allies in the same pasture | Special pose, heal circles | Auto | Phase 1 |
| Fairy | Muse | Enchanting table: bookshelf power, or Quanta with Apothic Enchanting | Fairy Wind | Leash or Quick Work | Phase 1 |
| Psychic | Telekinesis | A source container and a destination, AE2 ME Interface included | Kinesis | Leash only | Phase 2 |
| Steel | Magnet | A container that collects dropped items from the work area | Metal sparks | Leash only | Phase 2 |
| Fire | Fan Catalyst Fire | Create encased fan: blasting or smoking | Fire Spin | Leash only | Phase 2 |
| Water | Fan Catalyst Water | Create encased fan: washing | Bubble | Leash only | Phase 2 |
| Ghost | Fan Catalyst Ghost | Create encased fan: haunting | Will-O-Wisp | Leash only | Phase 2 |
| Flying | Tailwind | Create windmill bearing | Gust | Leash only | Phase 2 |
| Ground | Cultivator | A growing crop, which gets the effect of bone meal | Mud Sport | Auto | 1.1 |
| Grass | Harvester | A fully grown crop, which it harvests and replants | Razor Leaf | Auto | 1.1 |

A worker of an **Auto** role takes the nearest free target on its own. It also takes a
Work Leash binding or a Quick Work order, except the Healer. A **Leash only** role needs
a Work Leash binding. Ice, Bug and Dark have no role.

The [spec sheet](docs/plans/2026-11-09-v1-spec-sheet.md) holds the numbers for each
role. The [role catalog](docs/roles.md) covers all 18 types, including later ideas.

## Balance

Cobblemon are a strong early-game and midgame power source, and a fleet of them can
compete. No single Cobblemon replaces an engineering problem that you are meant to
solve. Top-tier engineering, such as a large reactor or AE2 automation, always does
better. For example, a level-100 legendary Generator makes 10,000 FE/t, and a small
Extreme Reactors reactor makes about 15,000 FE/t. Roles on machines that cap their own
output, such as water wheels and windmills, are exempt. See
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
- **Farmland.** A worker that the work engine directs never tramples farmland.

The spec sheet areas CUL, HRV and CRP hold the exact rules. See
[the player guide](docs/player-guide.md#farming) and
[ADR 0015](docs/adr/0015-farming-roles-and-crop-adapters.md).

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
| Applied Energistics 2 | 19.2 or a later 19.x release | No. The Generator powers AE2 machines through FE, the Crystal Tender grows AE2 budding certus quartz, and Telekinesis feeds ME Interfaces. |

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
  block that has NeoForge's item capability.
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
  The [modpack integration page](docs/modpack-integration.md#farming-roles) lists the
  crops of each supported mod.

The spec sheet areas CMP, QWK, LND and CRP, and the row CD-MUS-07, hold the exact
compatibility rules.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/player-guide.md](docs/player-guide.md) | How to play: Work Mode, the Work Leash, Quick Work, land claims, Work Stamina, beds, healers, farming, every role, config and commands |
| [docs/modpack-integration.md](docs/modpack-integration.md) | For pack and server maintainers: dependencies, server defaults, claim mods, farming roles, other mods, mixins, payloads, saved data and what is tested |
| [docs/testing/manual-checklist.md](docs/testing/manual-checklist.md) | The manual play-test checklist for the Demo rows |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Components, data flow, integration points, art and milestones |
| [docs/roles.md](docs/roles.md) | Role catalog for all 18 types: Phase 1, Phase 2, the 1.1 roles and later ideas |
| [docs/plans/2026-11-09-v1-spec-sheet.md](docs/plans/2026-11-09-v1-spec-sheet.md) | Requirements for 1.0 and 1.1, each with an acceptance test |
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
