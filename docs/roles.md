# Role catalog

A role is a job that a Pokémon of a given type does on a specific block. This page
lists the roles for all 18 types: the 15 roles of version 1.0 and the later ideas.

Version 1.0 ships in phases ([ADR 0011](adr/0011-v2-roles-in-first-release.md)).
Phase 1 builds the 11 v1 roles and ends with a private test. Phase 2 builds the 4 v2
roles and the final art and ends with a second private test. Phase 3 is the public
release.

The [spec sheet](plans/2026-11-09-v1-spec-sheet.md) holds the exact rules and numbers
for each role. This page names the spec sheet area of each role, for example WHL for
the Wheel Driver, and does not repeat its numbers.

## Design rules

- **Balance rule.** No single Pokémon solves an engineering problem that the player is
  meant to manage. Pokémon are an early-game and midgame option, and a fleet of them
  can compete. Top-tier engineering always does better. Roles on machines that cap
  their own output are exempt: the Wheel Driver, the Cranker, Tailwind and the Fan
  Catalyst. See [ADR 0010](adr/0010-balance-rule.md) and the spec sheet area BAL.
- **One claimable block per role.** Every role works on one specific block that a
  worker can claim. Only one worker can claim a block, across all pastures. See
  [ADR 0005](adr/0005-global-claim-registry.md).
- **No loot from nothing.** No role makes items or loot out of nothing. Cobbleworkers,
  Cobblebase and Cobblemon Pasture Loot already cover produce and loot.
- **Level.** Level changes the output of the Generator, the Pump, the Muse,
  Telekinesis, the Magnet and Tailwind. The Superheater superheats only from level 50.
  Other roles copy the numbers of the source that they replace, for example the speed
  of a coal-fueled furnace.
- **One role at a time.** A Pokémon with two types can do the roles of either type,
  but it does one role at a time.
- **Data-driven.** Each role is a datapack JSON file. A pack maker can change a role's
  eligible types, effect and parameters, or turn the role off. See
  [ADR 0006](adr/0006-data-driven-roles.md).

## Type index

Thirteen types have roles in version 1.0.

| Type | Phase 1 | Phase 2 | Later idea |
| --- | --- | --- | --- |
| Normal | Cranker | | |
| Fire | Stoker, Brew Stoker | Fan Catalyst (blasting or smoking) | |
| Water | Wheel Driver, Pump | Fan Catalyst (washing) | |
| Grass | | | Grower |
| Electric | Generator | | |
| Ice | | | Freezer |
| Fighting | Cranker | | |
| Poison | Brewer | | |
| Ground | | | |
| Flying | | Tailwind | |
| Psychic | | Telekinesis | |
| Bug | | | Pollinator |
| Rock | Crystal Tender | | |
| Ghost | | Fan Catalyst (haunting) | |
| Dragon | Superheater | | |
| Dark | | | |
| Steel | | Magnet | |
| Fairy | Healer, Muse | | |

## Phase 1 roles

| Role | Type | Targets | What it does | Move effect | Assignment | Spec area |
| --- | --- | --- | --- | --- | --- | --- |
| Wheel Driver | Water | Create water wheel, large water wheel | Turns the wheel as if water flowed through it. Each wheel stores its own direction. | Water Gun | Auto | WHL |
| Pump | Water | Fluid tanks, boilers, basins and cauldrons, and any block that accepts water through NeoForge's fluid capability | Fills the target with water. The rate scales with level. | Water Gun | Leash only | PMP |
| Generator | Electric | Any block that accepts Forge Energy, including AE2 energy acceptors, chargers and inscribers | Pushes Forge Energy into the target. The output scales with level. Legendary and mythical Pokémon produce more. | Thunder Shock | Auto | GEN |
| Stoker | Fire | Furnace, smoker, blast furnace, and modded furnaces that extend the vanilla furnace | Keeps the furnace lit as if it burned coal, while a recipe can run. The fuel slot stays untouched. | Ember | Auto | STK |
| Superheater | Dragon | A Create Blaze Burner that holds a blaze | From level 50, keeps the burner superheated, as a Blaze Cake does, while a basin or fluid tank sits on top. Below level 50, it keeps the burner kindled, as coal does. | Dragon Breath | Auto | SUP, BRN |
| Cranker | Fighting, Normal | Create hand crank | Turns the crank without a stop. Each crank stores its own direction. | Pound | Auto | CRK |
| Crystal Tender | Rock | Blocks in `#c:budding_blocks`: budding amethyst and AE2 budding certus quartz | Gives extra growth ticks, as one AE2 Growth Accelerator does, while a face of the block can still grow. | Rock Throw | Auto | CRY |
| Brew Stoker | Fire | Brewing stand | Keeps the stand fueled as if it held blaze powder, while a brew can start. | Ember | Auto | BRF |
| Brewer | Poison | Brewing stand | Makes a running brew finish faster. | Poison Gas | Auto | BRW |
| Muse | Fairy | Enchanting table | Adds bookshelf power while a player uses the table. The power scales with level. | Fairy Wind | Leash only | MUS |
| Healer | Fairy | Tired or low allies in the same pasture | Restores an ally's Work Stamina. Each heal costs the healer some of its own Work Stamina. | Special pose, heal circles on the ally | Auto | HEAL |

The Valve Handle is not a Cranker target. Create lets a valve handle turn only a set
angle, so it cannot supply continuous power.

## Phase 2 roles

All four roles are leash-only, because they move items or change how a machine
behaves.

| Role | Type | Targets | What it does | Move effect | Spec area |
| --- | --- | --- | --- | --- | --- |
| Telekinesis | Psychic | A source container and a destination container, including Create vaults and the AE2 ME Interface | Moves items from the source to the destination. The rate scales with level. It uses the faces that the player clicked. It claims the source only, so many workers can feed one destination. The source sits in the work area, and the destination can sit anywhere within 32 blocks of the pasture block. | Kinesis | TLK |
| Magnet | Steel | A container | Collects dropped items from the pasture's work area into the container. The rate scales with level. It skips items that still have a pickup delay. | Special pose, metal sparks | MAG |
| Fan Catalyst | Fire, Water, Ghost | A Create encased fan | Stands in the block in front of the fan and acts as its catalyst. Fire gives blasting, or smoking after a flip with the Work Leash. Water gives washing. Ghost gives haunting. The fan never pushes or harms its own worker. | Fire Spin, Bubble, Will-O-Wisp | FAN |
| Tailwind | Flying | A Create windmill bearing | Adds virtual sails, based on the windmill's real sails and the worker's level, up to the windmill's own cap. A windmill still needs its minimum real sails to assemble. | Gust | TLW |

### Assignment

- **Auto.** A worker takes the nearest unclaimed target of any auto-assign role that it
  can do. After 10 seconds in Standby, it moves to an unclaimed target that needs work,
  for example from a wheel that real water already turns to a dry wheel.
- **Leashed workers stay put.** A leashed worker never moves off its bound target. Its
  binding clears when the target is removed or the Pokémon leaves its pasture.
- **Leash only.** A worker takes the target only through a Work Leash binding. The Pump
  is leash-only so that it does not fill a tank that holds another fluid. The Muse is
  leash-only because Fairy-types heal by default. The Phase 2 roles are leash-only so
  that Pokémon never reroute items or change a machine on their own.
- **Fairy-types.** A Fairy-type never claims a machine on its own. A Fairy-type with no
  machine binding acts as a healer. A leash binding to a machine makes it a worker for
  that role. For example, you can bind a Water/Fairy Pokémon to a water wheel, or any
  Fairy-type to an enchanting table as a Muse.
- **Wild Pokémon** never work.

### One worker per block

- A brewing stand takes a Brew Stoker or a Brewer, never both.
- A Blaze Burner takes only a Superheater. A Fire-type never works a burner (the
  approver's ruling of 2026-11-10).
- When one worker qualifies for two roles on the same target, the role with the higher
  priority wins. For example, a Poison/Fire Pokémon on a brewing stand works as a Brew
  Stoker.
- A large water wheel is one target. The claim sits on the wheel's controller, and a
  click on any block of the wheel's ring maps to the controller.
- A Telekinesis binding holds its source only. Its destination stays open to other
  workers.

A Blaze Burner that a Superheater heats also counts as a blasting
catalyst for a Create encased fan. Create applies this rule itself, so it needs no
extra role.

## Later ideas

| Role | Type | Target | Idea |
| --- | --- | --- | --- |
| Pollinator | Bug | Beehive, bee nest | The honey level rises faster. Cobblemon has no Pollen Puff effect yet, so the move effect is open. |
| Grower | Grass | Crops and saplings near the target | Gives extra growth ticks. It overlaps the Cobblebase growth aura job. The move effect is open. |
| Freezer | Ice | A water source block | Turns the water into ice, packed ice or blue ice, by level. The move effect is Ice Beam. |

## Types with no role

Ground and Dark have no role and no planned role. These unplanned ideas are on record:

- **Ground.** A tiller that turns dirt into farmland around a target. CobbleMiners
  already gives Ground-types a mining job through Cobbleworkers.
- **Dark.** A "night shift" modifier: Dark-types drain Work Stamina more slowly at
  night. This idea is a Work Stamina modifier, not a role.
