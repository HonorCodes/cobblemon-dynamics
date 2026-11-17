# Role catalog

A role is a job that a Cobblemon of a given type does on a specific block. This page lists the roles for all 18 types: the
15 roles of version 1.0, the 2 roles of 1.1, the 4 roles of 1.2, and the later ideas.

Version 1.0 ships in phases ([ADR 0011](adr/0011-v2-roles-in-first-release.md)).
Phase 1 builds the 11 v1 roles and ends with a private test. Phase 2 builds the 4 v2
roles and the final art and ends with a second private test. Phase 3 is the public
release.

The [spec sheet](plans/2026-11-09-v1-spec-sheet.md) holds the exact rules and numbers
for each role. This page names the spec sheet area of each role, for example WHL for
the Wheel Driver, and does not repeat its numbers.

## Design rules

- **Balance rule.** No single Cobblemon solves an engineering problem that the player is
  meant to manage. Cobblemon are an early-game and midgame option, and a fleet of them
  can compete. Top-tier engineering always does better. Roles on machines that cap
  their own output are exempt: the Wheel Driver, the Cranker, Tailwind and the Fan
  Catalyst, and since 1.2 Tailwind on the Mekanism Wind Generator and the Immersive
  Engineering Windmill and the Wheel Driver on its Watermill. Each new role or target
  still names a cap and a benchmark. See [ADR 0010](adr/0010-balance-rule.md) and the
  spec sheet area BAL.
- **One claimable block per role.** Every role works on one specific block that a
  worker can claim. Only one worker can claim a machine, across all pastures. The
  logistics roles, the Magnet and Telekinesis, share their containers instead: up to 5
  of their workers together on one container. The Soul Collector shares its Experience
  Collector in the same way. See [ADR 0005](adr/0005-global-claim-registry.md) and its
  Amendments 1 and 3.
- **An area job claims one cell.** A role that works an area, such as the Freezer,
  claims the one cell that its worker walks to and works the cells around the worker.
  Two workers never claim one cell, and an overlap of their reach costs nothing. See
  [ADR 0019](adr/0019-freezer-area-job.md).
- **No loot from nothing.** No role makes items or loot out of nothing. Cobbleworkers,
  Cobblebase and Cobblemon Pasture Loot already cover produce and loot. The Lumberjack
  and the Harvester replant only from the drops, and the Soul Collector only moves XP
  that already exists.
- **Level.** Level changes the output of the Generator, the Pump, the Muse,
  Telekinesis, the Magnet, Tailwind and the Cultivator, and since 1.2 of the Lumberjack,
  the Soul Collector, the Freezer and the Heater. On the targets of other mods, level
  also changes the Generator's charge of a Healing Machine, the Cultivator's pace at a
  Botany Pots pot, and Tailwind's wind on the Immersive Engineering Windmill, the
  Mekanism Wind Generator and the air of PneumaticCraft.
  The Superheater superheats only from level 50, and the Stoker works the blast furnaces
  of Immersive Engineering and Railcraft Reborn only from level 50.
  Other roles copy the numbers of the source that they replace, for example the speed
  of a coal-fueled furnace.
- **One role at a time.** A Cobblemon with two types can do the roles of either type,
  but it does one role at a time.
- **Data-driven.** Each role is a datapack JSON file. A pack maker can change a role's
  eligible types, effect and parameters, or turn the role off. See
  [ADR 0006](adr/0006-data-driven-roles.md).
- **No setup.** Every supported mod works with the built-in role files, tags and data
  maps. The role file of a mod's target loads only when that mod is loaded, and its
  tags and data maps exist only for overrides. See
  [ADR 0016](adr/0016-zero-setup-optional-mod-compatibility.md).

## Type index

Thirteen types have roles in version 1.0, fifteen in version 1.1, and seventeen in version 1.2.

| Type | Phase 1 | Phase 2 | 1.1 | 1.2 | Later idea |
| --- | --- | --- | --- | --- | --- |
| Normal | Cranker | | | | |
| Fire | Stoker, Brew Stoker | Fan Catalyst (blasting or smoking) | | Heater | |
| Water | Wheel Driver, Pump | Fan Catalyst (washing) | | | |
| Grass | | | Harvester | | |
| Electric | Generator | | | | |
| Ice | | | | Freezer | |
| Fighting | Cranker | | | | |
| Poison | Brewer | | | | |
| Ground | | | Cultivator | | |
| Flying | | Tailwind | | | |
| Psychic | | Telekinesis | | | |
| Bug | | | | Lumberjack | Pollinator |
| Rock | Crystal Tender | | | | |
| Ghost | | Fan Catalyst (haunting) | | Soul Collector | |
| Dragon | Superheater | | | | |
| Dark | | | | | |
| Steel | | Magnet | | | |
| Fairy | Healer, Muse | | | | |

## Phase 1 roles

| Role | Type | Targets | What it does | Move effect | Assignment | Spec area |
| --- | --- | --- | --- | --- | --- | --- |
| Wheel Driver | Water | Create water wheel, large water wheel | Turns the wheel as if water flowed through it. Each wheel stores its own direction. | Water Gun | Auto | WHL |
| Pump | Water | Fluid tanks, boilers, basins and cauldrons, and any block that accepts water through NeoForge's fluid capability | Fills the target with water. The rate scales with level. | Water Gun | Leash only | PMP |
| Generator | Electric | Any block that accepts Forge Energy, including AE2 energy acceptors, chargers and inscribers | Pushes Forge Energy into the target. The output scales with level. Legendary and mythical Cobblemon produce more. | Thunder Shock | Auto | GEN |
| Stoker | Fire | Furnace, smoker, blast furnace, and modded furnaces that extend the vanilla furnace | Keeps the furnace lit as if it burned coal, while a recipe can run. The fuel slot stays untouched. | Ember | Auto | STK |
| Superheater | Dragon | A Create Blaze Burner that holds a blaze | From level 50, keeps the burner superheated, as a Blaze Cake does, while a basin or fluid tank sits on top. Below level 50, it keeps the burner kindled, as coal does. | Dragon Breath | Auto | SUP, BRN |
| Cranker | Fighting, Normal | Create hand crank | Turns the crank without a stop. Each crank stores its own direction. | Pound | Auto | CRK |
| Crystal Tender | Rock | Blocks in `#c:budding_blocks`: budding amethyst and AE2 budding certus quartz | Gives extra growth ticks, as one AE2 Growth Accelerator does, while a face of the block can still grow. | Rock Throw | Auto | CRY |
| Brew Stoker | Fire | Brewing stand | Keeps the stand fueled as if it held blaze powder, while a brew can start. | Ember | Auto | BRF |
| Brewer | Poison | Brewing stand | Makes a running brew finish faster. | Poison Gas | Auto | BRW |
| Muse | Fairy | Enchanting table | Adds bookshelf power while a player uses the table. The power scales with level. With Apothic Enchanting, it adds Quanta instead. | Fairy Wind | Leash only | MUS |
| Healer | Fairy | Tired or low allies in the same pasture | Restores an ally's Work Stamina. Each heal costs the healer some of its own Work Stamina. | Special pose, heal circles on the ally | Auto | HEAL |

The Valve Handle is not a Cranker target. Create lets a valve handle turn only a set
angle, so it cannot supply continuous power.

## Phase 2 roles

All four roles are leash-only, because they move items or change how a machine
behaves.

| Role | Type | Targets | What it does | Move effect | Spec area |
| --- | --- | --- | --- | --- | --- |
| Telekinesis | Psychic | A source container and a destination container, including Create vaults and the AE2 ME Interface | Moves items from the source to the destination. The rate scales with level. It uses the faces that the player clicked. It claims the source only, with a shared claim. Up to 5 workers of the logistics roles share each source or destination, so several can feed one ME Interface. The source sits in the work area, and the destination can sit anywhere within 32 blocks of the pasture block. | Kinesis | TLK |
| Magnet | Steel | A container | Collects dropped items from the pasture's work area into the container. The rate scales with level. It skips items that still have a pickup delay. It shares its container with other Magnets and Telekinesis workers, up to 5 together. | Special pose, metal sparks | MAG |
| Fan Catalyst | Fire, Water, Ghost | A Create encased fan that faces sideways | Stands in the block in front of the fan and acts as its catalyst. Fire gives blasting, or smoking after a flip with the Work Leash. Water gives washing. Ghost gives haunting. The fan never pushes or harms its own worker. | Fire Spin, Bubble, Will-O-Wisp | FAN |
| Tailwind | Flying | A Create windmill bearing | Adds virtual sails, based on the windmill's real sails and the worker's level, up to the windmill's own cap. A windmill still needs its minimum real sails to assemble. | Gust | TLW |

### Assignment

- **Auto.** A worker takes the nearest unclaimed target of any auto-assign role that it
  can do. After 10 seconds in Standby, it moves to an unclaimed target that needs work,
  for example from a wheel that real water already turns to a dry wheel.
- **Leashed workers stay put.** A leashed worker never moves off its bound target. Its
  binding clears when the target is removed or the Cobblemon leaves its pasture.
- **Leash only.** A worker takes the target only through a Work Leash binding. The Pump
  is leash-only so that it does not fill a tank that holds another fluid. The Muse is
  leash-only because Fairy-types heal by default. The Phase 2 roles are leash-only so
  that Cobblemon never reroute items or change a machine on their own.
- **Fairy-types.** A Fairy-type never claims a machine on its own. A Fairy-type with no
  machine binding acts as a healer. A leash binding to a machine makes it a worker for
  that role. For example, you can bind a Water/Fairy Cobblemon to a water wheel, or any
  Fairy-type to an enchanting table as a Muse.
- **Wild Cobblemon** never work.

### One worker per block

- A brewing stand takes a Brew Stoker or a Brewer, never both.
- A Blaze Burner takes only a Superheater. A Fire-type never works a burner (the
  approver's ruling of 2026-11-10).
- When one worker qualifies for two roles on the same target, the role with the higher
  priority wins. For example, a Poison/Fire Cobblemon on a brewing stand works as a Brew
  Stoker.
- A large water wheel is one target. The claim sits on the wheel's controller, and a
  click on any block of the wheel's ring maps to the controller.
- The Magnet and Telekinesis share their containers. Up to
  `logistics.maxWorkersPerContainer` of their workers (default 5) work one container at
  once, in any mix: Magnet containers, Telekinesis sources and Telekinesis destinations.
  They never block the one worker of a machine on the same block, so a Stoker heats a
  furnace while a Telekinesis worker empties it.
- A sapling takes a Cultivator and a Lumberjack at once, because they claim in
  different lanes (1.2).

A Blaze Burner that a Superheater heats also counts as a blasting
catalyst for a Create encased fan. Create applies this rule itself, so it needs no
extra role.

## 1.1 roles

Both roles take auto-assignment, Quick Work and the Work Leash. Data tags decide which blocks they work, and crop adapters
decide how ([ADR-0015](adr/0015-farming-roles-and-crop-adapters.md)). A crop takes one worker of each role at the same
time.

| Role | Type | Targets | What it does | Move effect | Spec area |
| --- | --- | --- | --- | --- | --- |
| Cultivator | Ground | A crop of `#cobblemon_dynamics:cultivator_targets` that bone meal grows | Gives the crop the effect of bone meal, with no item, ceil(30 × L ÷ 100) times per minute. Each use costs 100 ticks of Work Stamina. It stops when the crop is fully grown. Since 1.2, it also grows saplings. | Mud Sport | CUL |
| Harvester | Grass | A fully grown crop of `#cobblemon_dynamics:harvester_targets` | Harvests the crop 20 ticks after its work starts, drops the harvest beside the crop, and replants the crop from the drops. It never takes an apricorn. | Razor Leaf | HRV |

## 1.2 roles

The Lumberjack and the Soul Collector are leash-only, and the Freezer is automatic with no Quick Work. The Heater is
automatic on Mekanism and leash-only on PneumaticCraft. The design is in the spec sheet's areas and in
[ADR-0017](adr/0017-lumberjack-and-saplings.md), [ADR-0018](adr/0018-experience-collector-and-soul-collector.md),
[ADR-0019](adr/0019-freezer-area-job.md) and [ADR-0020](adr/0020-machines-and-crops-of-other-mods.md). The coordinator
ruled on the design decisions of the [1.2 plan](plans/2026-11-15-v1.2-new-roles-and-compat.md#design-decisions) on
2026-11-15 (R81), and on the reviews of the build in R82 to R90.

| Role | Type | Targets | What it does | Move effect | Assignment | Spec area |
| --- | --- | --- | --- | --- | --- | --- |
| Lumberjack | Bug | A natural tree, bound at its trunk base cell, or a sapling | Fells the whole tree with its own natural leaves, also above the work area, drops the wood beside the trunk for a Magnet, and replants a sapling from the drops. It never breaks a placed leaf, and it skips Pam's fruit trees, apricorn trees, the Twilight Forest's magic trees, huge fungi, mangroves, trees with a bee nest and trees of more than 256 logs. | Aerial Ace | Leash only | LUM |
| Soul Collector | Ghost | An Experience Collector | Takes the XP orbs of its pasture's work area into the collector, but no orb within 8 blocks of a player, or for 5 minutes of the spot where a player died. The pace scales with level. Up to 5 workers share one collector. | Night Shade | Leash only | SOU |
| Freezer | Ice | The water sources under air of the work area | Turns the top layer of water within 5 blocks of itself into ice, and walks out on the new ice. It leaves water that hydrates farmland, bubble columns, sources that feed a flow and lit water. The pace scales with level. | Ice Beam | Auto, no Quick Work | FRZ |
| Heater | Fire | The heat valves of Mekanism, and the heat machines of PneumaticCraft | Pushes heat into the target. The heat scales with level, below what a full Thermal Evaporation Plant uses on Mekanism, and up to one lava block on PneumaticCraft. It never heats a reactor port, the Heat Generator or the Entangloporter. | Ember | Auto on Mekanism, leash only on PneumaticCraft | HTR |

The **Experience Collector** is a block of 1.2. It stores the XP that Soul Collectors bring, and its "Collect experience"
button gives all of it to a player. Its back and bottom faces offer the XP to pipes as the XP fluid of the pack, and the mod
adds no fluid of its own (spec area XPC).

## Machines and crops of other mods (1.2)

The existing roles also work the machines and crops of the supported mods, each through its own role file or tag, with no
setup. [ADR-0020](adr/0020-machines-and-crops-of-other-mods.md) lists every target with its value and benchmark.

| Role | New targets | Spec area |
| --- | --- | --- |
| Stoker | The alloy kiln of Immersive Engineering, and its blast furnaces from level 50, the blast furnace of Railcraft Reborn from level 50, and its solid-fueled firebox with the Work Leash only, one worker for each firebox block, the Fuelwood Heater of Mekanism, the furnaces of Iron Furnaces, the compressors of PneumaticCraft, the oven of Cooking for Blockheads, the Clibano of Forbidden & Arcanus, the Egg Incubator of Cobblemon Incubator and the Volcanic Sourcelink of Ars Nouveau. Never the Aether's Freezer. | FUEL, FIX |
| Tailwind | The Wind Generator of Mekanism, the Windmill of Immersive Engineering, and the air machines of PneumaticCraft, below their danger pressure. | AIR |
| Wheel Driver, Cranker | The Watermill of Immersive Engineering, while a Kinetic Dynamo takes its power, the crank wheels of Create: Connected, and the AE2 Crank, which charges its machine without turning on screen. | KIN |
| Cultivator, Harvester | Botany Pots, and the crops of the crop table of ADR-0020. Never the crops of Mystical Agriculture for the Cultivator, and never a Twilight Forest bush itself or a growth-only look-alike of cave vines for the Harvester. | FRM, FIX |
| Superheater | Any heat user above the burner of its tag, such as cooking pots and distillation towers, and the Liquid Blaze Burner of Create Crafts & Additions. | FIX, MCH |
| Crystal Tender | Cobblemon's budding tumblestones, Unusual End's citrine, Quark's corundum, with full type gem clusters no longer counted as buds. | FIX, MCH |
| Muse | Easy Magic's table, Enchanting Infuser's infusers, and the Infuser of Create: Enchantment Industry with Apothic Enchanting. | FIX, MCH |
| Brewer, Generator, Pump, Telekinesis, Magnet | The Alchemist Cauldron of Iron's Spells 'n Spellbooks, Cobblemon's Healing Machine, farmland, and the plain containers of Cobblemon and furniture mods. | MCH |

## Later ideas

| Role | Type | Target | Idea |
| --- | --- | --- | --- |
| Pollinator | Bug | Beehive, bee nest | The honey level rises faster. Cobblemon has no Pollen Puff effect yet, so the move effect is open. Bug-types have the Lumberjack since 1.2. |

The Freezer of this list became the 1.2 Freezer, which ices water over on top and makes no packed ice or blue ice.

## Cut or not added

The approver and the coordinator ruled these out of 1.2 (inputs R76 and R80 of the spec sheet). Each can come back as a
later idea with a new ruling.

- **Cut:** the Rancher (Normal), which bred animals, and every animal or Pollinator target of the audits, so no claim
  question about entities. The Ice Quencher. Every fan catalyst of Create: Dragons Plus (freezing, sanding and ending), and
  the fan types of Create: Enchantment Industry (salvaging) and Create: Aquatic Ambitions (channeling).
- **Not added:** sanding by Ground-types, coke ovens, which need no fuel, the AE2 Vibration Chamber, PneumaticCraft cooling
  for Ice, the XP Tome, a bottle mode or the tanks of other mods for the Soul Collector, and free fuel for the solid-fuel FE
  generators of other mods, such as the Powah Furnator, because the Generator already prices FE by level.
- **No 1.21.1 build:** Thermal Expansion, Thermal Dynamics, Tinkers' Construct and Botania.

## Types with no role

Dark has no role and no planned role. This unplanned idea is on record:

- **Dark.** A "night shift" modifier: Dark-types drain Work Stamina more slowly at
  night. This idea is a Work Stamina modifier, not a role.
