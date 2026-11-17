# ADR-0020: Machines and crops of other mods, the Heater, and fixes

Status: Accepted (2026-11-15) by the coordinator under the approver's delegation, with the rulings (R81) on the design
decisions of the
[1.2 plan](../plans/2026-11-15-v1.2-new-roles-and-compat.md#design-decisions). Inputs R77, R79 and R80 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) hold the rulings, RES10 to RES13 the research.
[ADR-0016](0016-zero-setup-optional-mod-compatibility.md) holds the rules that every target of this record follows: its own
compat package, a role file with a condition, complete defaults, a mixin gate and a fail-safe guard. The coordinator's
rulings on the reviews of the compat tasks (R85, R87, R88 and R90, 2026-11-16 and 2026-11-17) amend the rows that cite them.

## Context

The approver asked for broad compatibility (R77): "does flying types also power mekanikism windmill? it should", "are there
other gaps like that? we need broad compatibility", and then "approved all the M-compat audit recommendations". The
approved list held eight items, M1 to M8:

- M1. Tailwind on the Mekanism Wind Generator.
- M2. The Stoker on the Immersive Engineering alloy kiln.
- M3. The Stoker on the Mekanism Fuelwood Heater.
- M4. The Stoker on the crude and improved blast furnaces of Immersive Engineering and the blast furnace of Railcraft Reborn,
  from worker level 50 only, as the Superheater.
- M5. The Stoker on the solid-fueled boiler of Railcraft Reborn, with a cap per worker.
- M6. A Heater role (Fire) on Mekanism heat, whose heat grows with level and stays below what a full plant can use.
- M7. The Cultivator and the Harvester on Botany Pots. The Harvester works only pots with no hopper.
- M8. The Wheel Driver on the Immersive Engineering Watermill, and Tailwind on its Windmill, as a weather bonus only,
  because Immersive Engineering caps a windmill at 8 sails.

Two audits followed (RES11, RES12). The coordinator's rulings on them (R80) add every "build" row and every "build with a
caveat" row, with the exceptions of [ADR-0016](0016-zero-setup-optional-mod-compatibility.md#context), six fixes of
today's behaviour, and the values of ruling 3.

### Research (RES10 to RES13)

The four notes read the jars of COHO's frozen cache of 2026-11-02 with `javap` and `zipfile`, the upstream source at the tag
of each jar, and the Modrinth API, on 2026-11-14 and 2026-11-15. The facts that each decision uses sit in the row of its
table below. The plan's checked references list the signatures. RES13 adds the checks of this design:

- `TileEntityWindGenerator` (Mekanism Generators 10.7.19.85): the private `getMultiplier()` returns 0 when the cell
  `pos.above(4)` holds a fluid or sees no sky. Otherwise it returns (min + (max − min) ÷ (maxY − minY) × (clamped y − minY))
  ÷ min, with `windGenerationMin`, `windGenerationMax`, `windGenerationMinY` and `windGenerationMaxY` of Mekanism's
  config. `onUpdateServer` stores it in the private `currentMultiplier` every 20 ticks. `getCurrentGeneration()` gives
  `windGenerationMin` × `currentMultiplier` J per tick.
- `TileEntityBoundingBlock.getMainPos()` (Mekanism 10.7.19.85) is public. `TileEntityFuelwoodHeater` has the public fields
  `burnTime` and `maxBurnTime`.
- Twilight Forest 4.8.3345: `TFBushBlock.useWithoutItem` at `AGE` 3 rolls the private `berryLoot` table through
  `dropFromBlockInteractLootTable`, plays `SWEET_BERRY_BUSH_PICK_BERRIES` and sets `AGE` 2 with flag 2. The berry tables are
  `twilightforest:blocks/<bush>_berries` for the eight bushes, `<metal>_oreberry_bush_berries` for the copper, iron and gold
  oreberries, and `essence_berry_bush_berries` for the essence oreberry. The block's own loot table drops the bush.
- Cobblemon 1.8.4: `#c:buds` holds the nine budding tumblestones and `#cobblemon:type_gem_clusters`. `#c:clusters` holds
  the three tumblestone clusters and `#cobblemon:type_gem_clusters`. Each type gem cluster has a `stage` property, so a full
  cluster is a bud of `#c:buds` at its last stage. `#c:budding_blocks` holds `cobblemon:deepslate_crystal_core`, and
  `#cobblemon:tumblestone_heat_source` holds lava and the magma block.
- The block ids of the tables below come from the blockstate files of the COHO jars, except where a row says
  "unverified".

## Decision

### Fixes of today's behaviour

Each fix is its own spec row (R80, ruling 2):

1. **The Aether's Freezer.** It extends the vanilla furnace and burns Icestone, so a Fire-type would fuel an ice machine.
   The Stoker never claims a block of `#cobblemon_dynamics:stoker/never`, whose default holds `aether:freezer`
   (CD-FIX-01). The Aether's Altar and Incubator stay Stoker targets.
2. **Type gem cores.** Cobblemon puts every stage of its type gem clusters in `#c:buds`, so the Crystal Tender counted a
   full cluster as a bud that can grow, and its demand on `cobblemon:deepslate_crystal_core` never cleared. A bud of
   `#c:clusters` now counts only while its integer `stage` or `age` property is below its highest value (CD-FIX-02).
3. **Twilight Forest bushes.** A harvest of a berry bush or an oreberry rolls the bush's berry table and never its block loot
   table, which drops the bush itself (CD-FIX-03, through decision 9).
4. **Crops that bone meal never grows.** Mystical Agriculture's `MysticalCropBlock.isBonemealSuccess` always returns false,
   and its crops reach `#minecraft:crops`. The Cultivator spent Work Stamina there for 20 uses, then stalled. The
   Cultivator never claims a block of `#cobblemon_dynamics:cultivator/never`, whose default holds
   `#mysticalagriculture:crops` (CD-FIX-04). That respects the mod's design: no bone meal growth there.
5. **Easy Magic.** It replaces the vanilla table with `easymagic:enchanting_table`, whose `ModEnchantmentMenu` counts power in
   its private `getEnchantingPower`, so the Muse found no target. A mixin on that method adds the Muse's bookshelves, within
   the menu's maximum of 15 (CD-FIX-05).
6. **Enchanting Infuser.** Its `InfuserMenu.getAvailablePower(Level, BlockPos)` is private and reads no vanilla hook. A
   mixin at its return adds the Muse's bookshelves, within the infuser's maximum of 15. With Apothic Enchanting, the infuser
   counts that power as Eterna, and the Muse gives only Quanta there (CD-MUS-07), so it adds nothing (CD-FIX-06).
7. **The Superheater's demand.** CD-BRN-03 read Create's `isValidBlockAbove`, which accepts only a basin or a fluid tank.
   Other blocks also read a burner's heat. The Superheater now also has demand while a block of
   `#cobblemon_dynamics:superheater/heat_users` sits above the burner. The default holds `create:basin`,
   `create:fluid_tank`, Farmer's Delight's `cooking_pot` and `skillet` (through Create: Central Kitchen), the distillation
   tank of Create: Diesel Generators and the heat pipe of Create: New Age (their ids unverified) (CD-FIX-07).

### New targets of the existing roles, and the Heater

Each row names its role file and handler. A row with no condition loads on every server. "Leash" means leash only. Every
number is a parameter of its role file.

| Role | Mod and version | Target | How the handler works it | Default value | Benchmark |
| --- | --- | --- | --- | --- | --- |
| Stoker (`stoker_immersiveengineering`, `ie_furnace`, auto) | Immersive Engineering 12.4.2-194 | Alloy kiln (`alloy_smelter` multiblock) | Through IE's API to the multiblock state, then its public `getStateView()`: while a recipe fits, `BURN_TIME` and `LAST_BURN_TIME` stay at the furnace's highest process speed + 1. A copy of IE's private output-room check decides the recipe. | Any level | 8 operations per coal, 200 ticks each |
| Stoker (same file) | Immersive Engineering 12.4.2-194 | Crude and improved blast furnaces (`blast_furnace`, `advanced_blast_furnace`) | As the kiln. Preheaters stay the player's cost. | From level 50 (`blast_furnace_min_level`) | 1 coke per steel, 1,200 ticks |
| Stoker (`stoker_railcraft`, `railcraft_fuel`, auto) | Railcraft Reborn 1.2.17 | Blast furnace (`blast_furnace_bricks`, at its master) | The public `setBurnTime` keeps `burnTime` at 16 or more after each tick while a recipe fits. It keeps the recipe of its input item until that item changes (R85). | From level 50 | Coke 3,200 ticks, 400 ticks per steel |
| Stoker (`stoker_railcraft_boiler`, `railcraft_firebox`, leash) | Railcraft Reborn 1.2.17 | Solid-fueled firebox (`solid_fueled_firebox`), each block its own target | The workers of one boiler pool their supply (R85). They fuel it only while every firebox block of the boiler has a bound Working worker. Then they keep the master boiler's `burnTime` at the need of a fuel step at the boiler's top temperature, so no step takes a fuel item, and F workers cover need ÷ F each. With fewer workers, they wait in Standby and the boiler burns its own fuel. A firebox block has demand only while the boiler's water fills at least half of its tank, because `SteamBoiler.checkFill` explodes a superheated boiler that takes water into an empty tank. The burn time that the workers leave lasts at most one fuel step, 16 ticks at low pressure and 8 at high pressure. | The need of each fuel step, shared by the workers | One lit vanilla furnace burns 1 per tick, and 36 high-pressure tanks need about 41 |
| Stoker (`stoker_mekanism`, `fuelwood_heater`, auto) | Mekanism 10.7.19.85 | Fuelwood Heater | Keeps the public `burnTime` at 2 or more and `maxBurnTime` equal, while a neighbour exposes `mekanism:heat_handler`. It reads the neighbours through one capability cache for each face (R85). | Any level | 400 J/t of heat, the heater's own rate |
| Stoker (`stoker_ironfurnaces`, `iron_furnace`, auto) | Iron Furnaces 4.3.2 | Every furnace of `BlockIronFurnaceTileBase` | Keeps the public `furnaceBurnTime` at 2 or more while the furnace's own recipe check passes. It never fuels the generator augment. | Its own speed, no cap (R80) | Copper 180 ticks per item, netherite 5 |
| Stoker (`stoker_pneumaticcraft`, `pnc_compressor`, auto) | PneumaticCraft: Repressurized 8.2.24 | Air Compressor, Advanced Air Compressor | Adds up to the machine's base burn rate to the public `burnTime` while the air of the next tick keeps the pressure below the lowest danger pressure of the compressor and its connected handlers. A mixin shadows the private `maxBurnTime` and `airBuffer` and opens them through an interface, so the handler fills the gauge and reads the buffer. Without that mixin the buffer counts at its bound of 1 mL, and the fuel still works (R88). | 1 burn tick per tick, 5 for the Advanced | 10 mL of air per tick, 50 for the Advanced |
| Stoker (`stoker_cookingforblockheads`, `cfb_oven`, auto) | Cooking for Blockheads 21.1.24 | Oven | Keeps the public `furnaceBurnTime` at 2 or more and `currentItemBurnTime` equal while a slot cooks. | The oven's own rate | 9 slots, 200 ticks each |
| Stoker (`stoker_forbidden_arcanus`, `clibano`, auto) | Forbidden & Arcanus 2.6.1 | Clibano | A mixin at the head of the Clibano's server tick keeps the private `burnTime` at 2 or more and `burnDuration` equal while a Fire-type's grant holds and it smelts, with normal fire. It reads the Clibano through shadows and an interface, which the mixin plugin checks, so the role gate sees whether it applied (R88). | Normal fire only | 100 ticks per smelt |
| Stoker (`stoker_cobblemon_incubator`, `egg_incubator`, auto) | Cobblemon Incubator 1.1.1 | Egg Incubator | A mixin at the return of `incubationSpeed()` gives `upgradedSpeed` while a Fire-type works an incubator with no Speed Upgrade, where the incubator's config turns Speed Upgrades on. An Analyse Upgrade or a filter that rejects the egg leaves no demand (R88). | One Speed Upgrade at most | One Speed Upgrade |
| Stoker (`stoker_ars_nouveau`, `volcanic_sourcelink`, auto) | Ars Nouveau 5.13.5 | Volcanic Sourcelink | Every 1,600 ticks of Working, the sourcelink gets the Source of one coal: the value of its public `getSourceValue` for a coal, through its public `addSource`. | One coal per 1,600 ticks | A furnace's coal rate |
| Heater, new role (`heater_mekanism`, `mekanism_heat`, auto) | Mekanism 10.7.19.85 | `#cobblemon_dynamics:heater_targets`: `mekanism:thermal_evaporation_valve`, `mekanism:boiler_valve` | Pushes heat through `IHeatHandler.handleHeat(double)` of `mekanism:heat_handler` on the face nearest the worker, while the handler's temperature is below 3,000 K. | ceil(1,500 × L ÷ 100) J per tick. `heat_per_tick_at_level_100` is clamped to 0 to 1,870, the heat that a full plant uses (R85). | A full evaporation plant (height 18) uses about 1,870 J/t, and a level 100 Generator through a Resistive Heater gives 7,500 J/t |
| Heater (`heater_pneumaticcraft`, `pnc_heat`, leash) | PneumaticCraft: Repressurized 8.2.24 | Any block entity with `PNCCapabilities.HEAT_EXCHANGER_BLOCK` on a horizontal face | Adds heat through `IHeatExchangerLogic.addHeat(double)` on the horizontal face nearest the worker, never on the null side, which returns a dummy. | max(0, (1,300 − T) ÷ 100) × L ÷ 100 per tick, T in kelvin | One lava block, about 10 heat per tick into a 300 K machine |
| Tailwind (`tailwind_mekanism`, `wind_generator`, leash) | Mekanism Generators 10.7.19.85 | Wind Generator, whose bounding blocks map to `getMainPos()` | A mixin at the return of `getMultiplier()`: a nonzero multiplier m becomes m + (M − m) × L ÷ 100, with M = `windGenerationMax` ÷ `windGenerationMin`. Zero stays zero, so the sky rule holds. | Level 100 gives `windGenerationMax` | 480 J/t at the config maximum, and about 122 J/t at y 320 on COHO with no worker |
| Tailwind (`tailwind_immersiveengineering`, `ie_windmill`, leash) | Immersive Engineering 12.4.2-194 | Windmill | A mixin at the return of `getActualTurnSpeed()` scales the weather factor w = (0.75 unless raining) × (0.66 unless thundering) to w + (1 − w) × L ÷ 100. Sails stay IE's. Every end of the worker's claim ends its bonus (R85). | Level 100 turns as in a thunderstorm | 8 sails: 35.6 FE/t clear, 71.8 FE/t thunder |
| Tailwind (`tailwind_pneumaticcraft`, `pnc_air`, leash) | PneumaticCraft: Repressurized 8.2.24 | Any block entity with `PNCCapabilities.AIR_HANDLER_MACHINE` | Adds air through `addAir(int)` while the pressure after the addition stays below the machine's danger pressure. | ceil(50 × L ÷ 100) mL per tick | An Advanced Air Compressor, 50 mL per tick |
| Wheel Driver (`wheel_driver_immersiveengineering`, `ie_watermill`, auto) | Immersive Engineering 12.4.2-194 | Watermill, at its master | A mixin at the return of `getIFScaledTorque()` gives `virtual_torque` × the stored direction while the real torque is 0 and a worker drives the wheel. The direction lives in a saved attachment, and the handler calls `setShouldUpdate()` at each start, stop and flip. IE gives power only through the wheel beside a Kinetic Dynamo, so the wheel has demand only while a block that exposes `IRotationAcceptor.CAPABILITY` sits in its row of at most 3 wheels along its axis. Every end of the worker's claim ends the drive, and a drive that its worker no longer renews lapses (R85). | `virtual_torque`: the torque of an optimized overshot wheel, which the build measures (about 29, unverified) | About 29 FE/t per optimized wheel |
| Cranker (existing `cranker`) | Create: Connected 1.3.3 | Crank Wheel and Large Crank Wheel (`create_connected:crank_wheel` block entity type) | `CrankWheelBlockEntity` extends `HandCrankBlockEntity`, so the hand crank handler already matches. The compat hook attaches `DriveBehaviour` to that block entity type. | As a hand crank | A hand crank's speed, stress capacity 8 each |
| Cranker (`cranker_ae2`, `ae2_crank`, auto) | Applied Energistics 2 19.2.19 | Crank (`ae2:crank`) on a crankable machine, with ExtendedAE's machines | Calls `ICrankable.applyTurn()` of `AECapabilities.CRANKABLE` once every 18 ticks while `canTurn()`. It keeps `applyTurn()`, because `power()` would break the crank, so the crank charges its machine without turning on screen (R88). | One turn per 18 ticks | A player at the crank, 160 AE per turn |
| Superheater (`superheater_createaddition`, `liquid_burner`, auto) | Create Crafts & Additions 1.7.2 | Liquid Blaze Burner (`createaddition:liquid_blaze_burner`) | A mixin at the return of `getHeatLevelFromFuelType` holds `KINDLED` below level 50 and `SEETHING` from level 50 while a heat user of fix 7 sits above. Its own fuel stays, and the burner has demand only while the worker's heat is above the heat of that fuel (R88). | As the Superheater | Create's burner heat |
| Brewer (`brewer_irons_spellbooks`, `alchemist_cauldron`, auto) | Iron's Spells 'n Spellbooks 3.16.3 | Alchemist Cauldron | A mixin at the end of the cauldron's server tick advances each running cook time of the private `int[] cooktimes`, which it shadows, by one extra step per tick while a Poison-type's grant holds, never past its end. It reads the cauldron only through that shadow and an interface (R88). | Twice the speed | 100 ticks per input item |
| Generator (`generator_healing_machine`, `healing_charge`, auto) | Cobblemon 1.8.4 | Healing Machine | Adds charge through `setHealingCharge(float)`, up to `getMaxCharge()`, while it holds less. | 4 × L ÷ 100 times the machine's own rate (`recharge_multiplier_at_level_100`) | Cobblemon's own recharge: 6 charges in 900 s on COHO |
| Pump (existing `pump`, leash) | Minecraft 1.21.1 | Farmland | A Pump bound to farmland keeps each farmland block of the work area within 4 blocks of it horizontally, at its height, at moisture 7, checked every 20 ticks. | Moisture 7 | One water source hydrates a 9 × 9 area |
| Telekinesis, Magnet (existing, leash) | Cobblemon 1.8.4, CobbleFurnies 1.2, Handcrafted 4.0.3, Valhelsia Furniture 1.1.1 | A block entity with no item capability that is a vanilla `Container`: the Gilded Chest, Display Case and Disc Shelf, the furniture containers | `InvWrapper`, or `SidedInvWrapper` for a `WorldlyContainer`. Before each take, the fallback asks the container's `canTakeItem`, which neither wrapper asks, so it asks what a hopper asks (R90): Cobblemon's TM Machine keeps its inputs, and Visual Workbench keeps its crafting grid. Never a block of `#cobblemon_dynamics:logistics/no_fallback`, whose default holds Lootr's containers and Legendary Monuments' pedestals (ids unverified). | The role's own rate | The role's own rate |
| Muse (existing `muse`, leash and Quick Work) | Create: Enchantment Industry 2.5.4b, with Apothic Enchanting | Infuser (`create_enchantment_industry:infuser`) | The existing stats mixin adds the Muse's Quanta at the position below the Infuser, which `updateInfusionStats` gathers. CEI gathers the stats only while the Infuser is idle, so the Muse has demand while the Infuser stands over its basin, whether or not an infusion runs, and the next infusion starts with its Quanta (R87). | As at a table | Quanta up to 45 at level 100 |
| Cultivator, Harvester (`cultivator_botanypots`, `harvester_botanypots`, auto) | Botany Pots 21.1.44, with Bookshelf 21.1.81 and Prickle 21.1.11 | Every block entity of `botanypots:botany_pot`, the 36 untagged brick pots too | Growth: `updateGrowthTime` adds a bone meal's growth, a random 400 to 600 ticks, never past the required ticks − 20, and sets the bone meal cooldown of 20 ticks. Harvest: only a pot of type `BASIC` that `canHarvest()`: the handler resets the growth and pops the crop's harvest out of the pot's face toward the worker. The seed stays in the pot. | One bone meal per growth use | 1 bone meal: 400 to 600 ticks of growth |

The pot handlers claim in the farming lanes, so a pot takes one Cultivator and one Harvester, a Grass/Ground Cobblemon picks
as on a crop (CD-CRP-21, CD-CRP-22), and an auto-assigned worker goes pot to pot. A pot harvest breaks no block, so it asks
the use question only. These calls name Botany Pots' implementation classes. A later Botany Pots can change them, which is a
known risk.

The Heater never targets a fission or fusion reactor port, the Heat Generator, the Quantum Entangloporter or any other
Mekanism heat block outside its tag (R80). The PneumaticCraft Heater is leash only, because some PneumaticCraft machines need
a cold side.

### Crops of other mods

The adapter table of [ADR-0015](0015-farming-roles-and-crop-adapters.md#the-crop-families-of-coho-250) gains these families.
Each entry of another mod is optional.

| Mod and version | Family: block ids | Cultivator | Harvester |
| --- | --- | --- | --- |
| Cobblemon: Mega Showdown 1.3.3 | `mega_showdown:max_mushroom` | growth | `harvest/pick_to_age_1` |
| Ars Nouveau 5.13.5, Ars Elemental 0.7.10.4 | `ars_nouveau:bastion_pod`, `bombegranate_pod`, `frostaya_pod`, `mendosteen_pod`, `ars_elemental:flashpine_pod` (cocoa-like pods) | growth | `harvest/break_and_replant` |
| The Twilight Forest 4.8.3345 | The 8 berry bushes, `twilightforest:<berry>_bush` | growth | pick by loot table, `AGE` 2 after |
| The Twilight Forest 4.8.3345 | `copper_oreberry`, `iron_oreberry`, `gold_oreberry`, `essence_oreberry` | none (no bone meal) | pick by loot table, `AGE` 2 after |
| Oh The Biomes We've Gone 2.6.3 | `apple_fruit`, `green_apple_fruit`, `baobab_fruit`, `yucca_fruit`, `soul_fruit` | growth | `harvest/pick_to_age_0` |
| Oh The Biomes We've Gone 2.6.3 | `white_puffball` | growth | `harvest/pick_to_age_1` |
| Allthemodium 3.2.1, Deep Aether 1.1.5.1, Deeper and Darker 1.4.1 | `allthemodium:ancient_cavevines`, `deep_aether:golden_vines` and `_plant`, `deeperdarker:glowing_vines_plant` (cave-vine look-alikes with vanilla's `berries` property, whose loot holds the berries) | growth | `harvest/cave_vines` |
| Allthemodium 3.2.1, Ars Elemental 0.7.10.4, YUNG's Cave Biomes 3.1.1, Deeper and Darker 1.4.1 | `allthemodium:ancient_cavevines_plant`, `ars_elemental:source_vines` and `_plant`, `yungscavebiomes:prickly_peach_cactus`, `deeperdarker:glowing_vines` (cave-vine look-alikes, growth only by R90) | growth | none: `harvest/never` holds the first three |
| Unusual End 2.3.1b | `gloopilon_bulb` (a `CropBlock`) | growth | crop |
| The Undergarden 0.9.6 | `ditchbulb_plant` (its harvest unverified) | growth | `harvest/pick_to_age_0` |
| Actually Additions 1.3.26 | `coffee` (not in `#minecraft:crops`) | growth | crop |
| Dynamic Trees 1.7.2 | `#dynamictrees:saplings`, `#dynamictrees:rooty_soil` | growth | none |

Five cave-vine look-alikes are growth only (R90). The loot of Allthemodium's vine body and of Ars Elemental's source vines is
empty, the loot of YUNG's prickly peach cactus is the cactus, and Deeper Darker's vine tip has no `berries` property, so a pick
by their loot would drop nothing or the plant. Allthemodium's vines implement its `ACaveVines`, which extends vanilla's
`CaveVines`, and Ars Elemental's source vines extend `CaveVinesBlock` and `CaveVinesPlantBlock`. The cave-vines adapter of
ADR-0015 takes such a block by its class, with no tag, so `harvest/never` holds the three growth-only blocks of a `CaveVines`
class. The vines of Deep Aether and Deeper Darker and YUNG's cactus are no `CaveVines`.

8. **The cave-vines tag.** `#cobblemon_dynamics:harvest/cave_vines` gives the cave-vines adapter of ADR-0015 to a block
   with a `berries` property that is no `CaveVines`. It comes after `harvest/upper_half` and before
   `harvest/break_and_replant`.
9. **Pick by loot table.** The data map `cobblemon_dynamics:harvest_loot` (blocks) gives a block a loot table and the `age`
   value after a pick. Such a block is fully grown at its highest `age`. A pick drops that table's loot, rolled with the
   block's state, and sets `age`, with no block broken. The data map comes before every adapter tag. Its default file needs
   the Twilight Forest by its condition, and lists the 12 blocks of the table with their berry tables.
10. **Rooty soil.** A Dynamic Trees tree grows from bone meal on its rooty soil, but the change lands in the branches above,
    so the stall of CD-CUL-09 can stop a Cultivator after 20 uses while the tree still grows. The 6,000-tick pause limits
    the waste. That stays a known limit.

### Other existing roles

11. **The Crystal Tender's targets.** `#cobblemon_dynamics:crystal_tender_targets` holds `#c:budding_blocks`,
    `#cobblemon_dynamics:crystal/self_growing` (Cobblemon's nine budding tumblestones),
    `#cobblemon_dynamics:crystal/scheduled_tick` (`unusualend:budding_citrine`) and the unwaxed corundum blocks of Quark
    4.1-488 (ids unverified). A self-growing bud has demand while Cobblemon's own rule lets it grow: a block of
    `#cobblemon:tumblestone_heat_source` within 1 block, and a next stage. A block of `crystal/scheduled_tick` gets its
    scheduled `tick` in place of a random tick, on 1 extra tick in 5 (`scheduled_tick_one_in`), and only air counts as an
    open face of it. Unusual End's budding citrine grows only under a worker: its random tick does nothing in Minecraft
    1.21.1, and its scheduled tick grows a face into air with no roll of its own, so the gate gives it the pace of budding
    amethyst (R88). The buds of `#cobblemon_dynamics:crystal/buds` (`unusualend:citrine_bud`, `large_citrine_bud`) count as
    buds of `#c:buds`.
12. **The Crystal Tender's stall.** If 200 extra ticks in a row change no block of the target and of its faces, the target has
    no demand for that worker for 6,000 ticks. That covers a budding block that grows only in some places, such as Just Dire
    Things' time crystal outside its dimension or Quark's corundum above y 24.

### What works already

These targets need no code, because a role reaches them through a capability or a vanilla class today. The integration page
lists them.

- **The Generator:** the controller and the Interface of Refined Storage 2.0.9, the motors of Create: New Age, the machines of
  Mystical Agriculture 8.0.29, the Wireless Energy Heater of Iron Furnaces, the oven of Cooking for Blockheads with a Heating
  Unit, the Farmer of Actually Additions, the Heated Centrifuge of Productive Bees and the charge network of Railcraft Reborn.
- **The Pump:** the oxygen loaders of Ad Astra, the Hydroponic Bed of Industrial Foregoing, the boiler addon and the
  Fertilizer of Oritech, the fluid port of Extreme Reactors, the Thermo Generator of Powah, the Water Separator of Stellaris,
  the boilers of Railcraft Reborn and the Interface of Refined Storage.
- **Telekinesis and the Magnet:** the Interface of Refined Storage.
- **The Crystal Tender:** GeOre 6.2.4, the entro budding blocks of ExtendedAE and the imbued wood of Oh The Biomes We've Gone.
- **The farming roles:** the fertilized farmland of Farming for Blockheads, the crops of Mystical Agriculture for the
  Harvester, and the canola, flax and rice of Actually Additions.
- **The Superheater:** the Fluid Vessel of Create: Connected.
- **The Stoker:** the Oven of Handcrafted, the Cinder Furnace of the Twilight Forest and the Altar of the Aether.
- **The Experience Collector's output:** the XP machines of Ender IO, Industrial Foregoing, Cyclic and Just Dire Things.

### Left out (R80)

The new fan processing types, every Rancher, animal and Pollinator row, PneumaticCraft cooling for Ice, the XP Tome, the
solid-fuel FE generators (Powah, Ender IO, Create: Diesel Generators, Iron Furnaces' generator augment, RFTools Power,
Actually Additions, Just Dire Things, Industrial Foregoing, Cyclic, Ad Astra, Stellaris and Oritech), every row that the audits
mark skip, and the mods with no NeoForge 1.21.1 build (Thermal Expansion, Thermal Dynamics, Tinkers' Construct, Botania).

## Consequences

- A pack with Immersive Engineering, Mekanism, Railcraft Reborn, PneumaticCraft, Botany Pots or the other mods of the tables
  gets their targets with no step of the admin.
- Most adapters bind to internal classes or private members. The pinned versions are tested. A later version can turn one
  target off with one warning, and the compat line names it.
- Iron Furnaces' netherite furnace with a Stoker saves about 40 times the fuel of a vanilla furnace. The approver accepts
  such imbalance between mods (R80).
- A level 50 Superheater gives Seething heat to the PneumaticCraft machines beside its burner, about 27 heat per tick, and the
  motors of Create: New Age turn a level 100 Generator into about 170,000 SU. Both work today, and the BAL rows record them.
- The Egg Incubator needs Cobbreeding, whose Modrinth license adds two terms to MIT (ADR-0016). Neither term covers an
  optional integration or a local test-pack download, and the mod never bundles the jar.
- Extra Quanta can push an infusion of Create: Enchantment Industry out of its stat window. The player unbinds the Muse
  there.

## Alternatives considered

- **One Stoker handler with a fuel adapter per mod.** Rejected for decision 1 of ADR-0016: no per-mod role file and numbers.
- **A Stoker free-fuel cap for faster furnaces.** Rejected by R80.
- **The Heater on every Mekanism heat block.** Rejected by R80: fission and fusion ports, the Heat Generator and the
  Entangloporter stay out.
- **Virtual sails on the IE Windmill.** Rejected: Immersive Engineering caps a windmill at 8 sails, and 8 sails are cheap.
- **A rotation push into IE's Kinetic Dynamo with no mixin.** Rejected: the wheel would not turn on screen, and the role would
  act as a weaker Generator, not as a machine-capped role.
- **A fake player for Botany Pots' fertilizer.** Rejected: its `BasicFertilizer.apply` needs a real player and throws without
  one.

## Notes

Code that a mixin runs inside another mod's tick reads that mod only through shadows, or inside `try/catch (LinkageError)` (R88).
