# Cobblemon Dynamics

Cobblemon Dynamics puts your pastured Cobblemon to work. Each Cobblemon runs a machine
that matches its type. For example, it turns a Create water wheel, generates Forge
Energy, or fuels a furnace. The design follows the base workers of Palworld: you place
Cobblemon around your base, and each one powers a specific machine.

Cobblemon Dynamics is an addon for [Cobblemon](https://cobblemon.com) on NeoForge
1.21.1. It also works with [Create](https://github.com/Creators-of-Create/Create) and
[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2),
and with any mod that uses NeoForge energy, fluid or item capabilities.

## Status

**Phase 1 in progress.** There is no release yet. The design is in the
[spec sheet](docs/plans/2026-11-09-v1-spec-sheet.md), and the build follows the
[Phase 1 plan](docs/plans/2026-11-09-phase-1-plan.md).

Version 1.0 ships in three phases:

| Phase | Scope | Gate |
| --- | --- | --- |
| 1, private | The 11 v1 roles, Work Stamina, beds and healers (milestones M0 to M5) | Every v1 requirement passes, and the author tests it in a private world |
| 2, private | The 4 v2 roles and the final art (M6 to M8) | Every v2 requirement passes, and the author tests it again |
| 3, public | Release on Modrinth and CurseForge (M9) | — |

## How it works

1. Put Cobblemon in a Cobblemon Pasture Block.
2. Open the pasture and turn on **Work Mode**.
3. Each Cobblemon looks for a machine in the pasture's **work area** that matches its
   type. It walks to the machine, faces it, and loops a move effect while it works.

Rules that always apply:

- **One worker per block, except for logistics.** Only one Cobblemon can work a given
  machine, across all pastures. The logistics roles share their containers: up to 5
  Cobblemon of the Magnet and Telekinesis roles can use one chest at once, for example a
  Magnet that fills it and a Telekinesis worker that empties it. They never block the
  machine's own worker on the same block. The server config key
  `logistics.maxWorkersPerContainer` changes the 5.
- **Only pastured Cobblemon work, with one exception.** A Cobblemon works only while it is in
  a pasture with Work Mode on. The exception is Quick Work: a party Cobblemon that you send
  out with R while you look at a machine works that machine. The
  [player guide](docs/player-guide.md#quick-work) explains when it stops. Wild Cobblemon
  never work.
- **The pasture owner controls Work Mode.** Only the player who placed the pasture, or
  an operator, can turn Work Mode on or off.

### The Work Leash

Workers pick the nearest free machine on their own. Use the **Work Leash** to give a
specific Cobblemon a specific machine. Some roles work only through the leash.

| Action | Result |
| --- | --- |
| Right-click your pastured Cobblemon, then right-click a machine | Binds the Cobblemon to that machine |
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

| Type | Role | Works on | Move effect | Phase |
| --- | --- | --- | --- | --- |
| Water | Wheel Driver | Create water wheel, large water wheel | Water Gun | 1 |
| Water | Pump (leash only) | Fluid tanks, boilers, basins, cauldrons | Water Gun | 1 |
| Electric | Generator | Any block that accepts Forge Energy, AE2 machines included | Thunder Shock | 1 |
| Fire | Stoker | Furnace, smoker, blast furnace | Ember | 1 |
| Fire | Brew Stoker | Brewing stand (fuel) | Ember | 1 |
| Dragon | Superheater | Create Blaze Burner (kindled below level 50, superheated from level 50) | Dragon Breath | 1 |
| Fighting, Normal | Cranker | Create hand crank | Pound | 1 |
| Rock | Crystal Tender | Budding amethyst, AE2 budding certus quartz | Rock Throw | 1 |
| Poison | Brewer | Brewing stand (speed) | Poison Gas | 1 |
| Fairy | Healer | Tired allies in the same pasture | Special pose, heal circles | 1 |
| Fairy | Muse (leash only) | Enchanting table | Fairy Wind | 1 |
| Psychic | Telekinesis (leash only) | A source container and a destination, AE2 ME Interface included | Kinesis | 2 |
| Steel | Magnet (leash only) | A container that collects dropped items from the work area | Metal sparks | 2 |
| Fire, Water, Ghost | Fan Catalyst (leash only) | Create encased fan: blasting or smoking, washing, haunting | Fire Spin, Bubble, Will-O-Wisp | 2 |
| Flying | Tailwind (leash only) | Create windmill bearing | Gust | 2 |

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
- **Modded machines.** The Generator works with any block that accepts Forge Energy
  through NeoForge's energy capability. The Pump works with any block that accepts
  water through NeoForge's fluid capability. Telekinesis and the Magnet work with any
  block that has NeoForge's item capability.

The spec sheet area CMP holds the exact compatibility rules.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/player-guide.md](docs/player-guide.md) | How to play: Work Mode, the Work Leash, Quick Work, Work Stamina, beds, healers, every role, config and commands |
| [docs/modpack-integration.md](docs/modpack-integration.md) | For pack and server maintainers: dependencies, server defaults, other mods, mixins, payloads, saved data and what is tested |
| [docs/testing/manual-checklist.md](docs/testing/manual-checklist.md) | The manual play-test checklist for the Demo rows |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Components, data flow, integration points, art and milestones |
| [docs/roles.md](docs/roles.md) | Role catalog for all 18 types: Phase 1, Phase 2 and later ideas |
| [docs/plans/2026-11-09-v1-spec-sheet.md](docs/plans/2026-11-09-v1-spec-sheet.md) | Requirements for 1.0, each with an acceptance test |
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

## Contributing

The project is in the design phase. Open an
[issue](https://github.com/HonorCodes/cobblemon-dynamics/issues) to discuss a change
before you open a pull request. A design change starts as a change to the spec sheet.

## License

The code and the art are licensed under the [Mozilla Public License 2.0](LICENSE).

## Disclaimer

Cobblemon Dynamics is an unofficial addon. It is not affiliated with or endorsed by
the Cobblemon team, The Pokémon Company, Nintendo, Game Freak, Creatures, the Create
team or the Applied Energistics 2 team. Pokémon is a trademark of Nintendo, Creatures
and Game Freak.
