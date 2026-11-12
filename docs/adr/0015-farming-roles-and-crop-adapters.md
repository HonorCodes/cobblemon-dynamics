# ADR-0015: Farming roles and crop adapters

Status: Proposed (2026-11-12). The coordinator rules on it with the design decisions of the
[1.1 plan](../plans/2026-11-12-v1.1-farming.md#design-decisions-for-the-approver). Until then, the CUL, HRV and CRP rows of
the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md) stay `draft`.

## Context

The approver's rulings of 2026-11-12, inputs R72 and R73 of the spec sheet:

> was it ground or rock that has the crystal growth functionality? - I was hoping to make the other one have the function
> of Bonemeal/quick growth for applicable crops in range. it would go crop to crop, using bonemeal until the crop is fully
> grown and move on, or leash to a single crop and idle when it's fully grown, so when it's harvested, it will bonemeal
> again to full (does not use bonemeal item, just stamina and produces the bonemeal effect). - I also wanted to have
> Grass-types harvest grown crops (not apricorns, though), same leash behavior. can we include that in 1.1?

> it should work with modded crops. COHO's modpack is a good example of many different modded crops, so we can start with
> compatibility for those.

Asked where the harvest goes, the approver answered "#1": on the ground beside the crop, so that a leashed Steel-type
Magnet can collect it.

The coordinator ruled, under the approver's delegation:

1. Rock keeps the Crystal Tender.
2. Ground-types are the Cultivator. They give a crop the bonemeal growth effect with no item, costing Work Stamina per use,
   at a pace that scales with level. Unleashed (auto-assign), a Cultivator goes crop to crop until each is fully grown.
   Leashed to one crop, it is in Standby while the crop is fully grown, and grows it again after a harvest.
3. Grass-types are the Harvester. They harvest fully grown crops, drop everything on the ground, and replant from the
   drops: a seed, or the crop's own item. They never take apricorns. Leashing works as for the Cultivator.
4. Both roles are Quick Work eligible, as auto-assign roles. Work stays silent: no sounds.
5. Data tags decide which blocks each role works, so packs can add their own: `cobblemon_dynamics:cultivator_targets` and
   `cobblemon_dynamics:harvester_targets`. The defaults cover vanilla crops and the crop mods of COHO through adapters.
6. The Harvester never breaks a block that the pasture's placer may not use, through the claim gate of 1.0.0 (the LND
   rows).

On 2026-11-12 the coordinator pinned 1.1 to COHO 2.5.0 (pack `88aa156b`), whose mod list COHO-2 finalised that day.

### The engine today

- A role file names a handler. A handler with `ScanKind.BLOCK` makes the scan of a pasture visit every position of the work
  area, at most 512 positions per tick (CD-ASG-07). The Crystal Tender works this way.
- The claim registry holds one exclusive claim per target, and up to 5 shared claims for the logistics roles (CD-CLM-01,
  CD-CLM-10, CD-CLM-11). A leash binding of an exclusive role reserves its target across all pastures (CD-CLM-06).
- A worker at a target with no demand waits in Standby with its claim (CD-ASG-12). An auto-assigned worker moves to another
  target after 200 ticks of Standby (CD-ASG-19). A leashed worker never moves (CD-ASG-20).
- The leash resets clear a binding whose target is broken or replaced (CD-LSH-08). They poll each binding every 10 ticks
  through the handler's `stillValid`.
- Work Stamina drains 1 tick of capacity for each tick of Working (CD-STA-06, [ADR-0008](0008-work-stamina-model.md)).
- The land claim gate asks each claim mod about the UUID of a pasture's placer after each scan, on each poll and for beds
  (CD-LND-01 to CD-LND-05, [ADR-0014](0014-land-claims-for-work-mode-pastures.md)). Quick Work asks about the online owner
  (CD-QWK-26).
- All work is silent (CD-FX-09).
- The Magnet collects the dropped items of its pasture's work area that have no pickup delay (CD-MAG-04, CD-MAG-05).
- Dynamics switches Cobbleworkers and Cobblebase off in Work Mode pastures ([ADR-0009](0009-coexistence-with-pasture-job-mods.md)).
  Their crop jobs and Cobblebase's growth aura still run in other pastures.

### Research (RES8)

The research used `javap` on 2026-11-12. The crop mods' jars came from the frozen cache `E:/rch/jars/2026-11-02/`, read
only, copied into a new research folder: their versions equal those of COHO 2.5.0. Vanilla and NeoForge came from
`neoforge-21.1.265-merged.jar`. Tag files, loot tables and blockstates were read from the jars with `unzip -p`. The SHA-1 of
each jar:

| Jar | SHA-1 |
| --- | --- |
| `neoforge-21.1.265-merged.jar` | `2825166bb015e5d9929d59acd1eb4521081c7273` |
| `Cobblemon-neoforge-1.8.4+1.21.1.jar` | `33582bdfdf054c64fc114f81a4950bd23f937778` |
| `FarmersDelight-1.21.1-1.3.4.jar` | `27675ddfcb8cc37e6ea1ab6c15051c7949d94657` |
| `expandeddelight-0.1.4.jar` | `cf26fe18c3a9deec525fe0f0e7c2d3b9f4e9f484` |
| `minersdelight-1.21.1-1.4.5.jar` | `9361a43c3b2cfeda977d34a28e25a6e089465dcc` |
| `farmerscuttingeverything-1.21.1-1.0.1.jar` | `b7ed43a876929e9cdaf50ce149054a6aa512a961` |
| `farmers-cutting-regions-unexplored-1.21.1-1.1b-neoforge.jar` | `1c3c1f0bba76a3e7722c1e6f117ad66e21ef6fe6` |
| `arsdelight-2.2.5.jar` | `f4915ccd62442e308b2c0879c3c4ce35b9c59658` |
| `chefsdelight-1.0.6.jar` | `9e5992b895dbde9f31fd2f0590dcac925adfcc55` |
| `endersdelight-1.3.1.jar` | `a3a9f7fd621f14461d37357463dbd16c67d2badf` |
| `lendersdelight-1.21.1-1.0.10b.jar` | `dc8409a1a67bddf306fbb17de845a74e29ae7825` |
| `MyNethersDelight-1.21.1-1.10.4.1.jar` | `9c54439d65e77c7b4b711831138f81f3a849ff35` |
| `oceansdelight-neoforge-1.0.4-1.21.1.jar` | `2246edd8fc1bbf60422f5099854c1de0b2a439c9` |
| `TMTCF-NF-1.21.1-2.0.4.jar` | `5e0a33b5af457a23e0332169f7fd46aa538a4549` |
| `pamhc2crops-NEOFORGE-1.21.1-1.0.9.jar` | `2a735b24c61a0007fe98de6cc2dc5d5fff4915a2` |
| `pamhc2trees-NEOFORGE-1.21.1-1.0.9.jar` | `85d54e1d4d32f669d3f183945dc813360263ca91` |
| `pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar` | `dc2b5671914879d4731034668028f4d8cbcd775c` |
| `pamhc2foodextended-NEOFORGE-1.21.1-1.0.0.jar` | `0cf7f668fdedef93446f33215484913991185102` |
| `supplementaries-1.21.1-4.0.2-neoforge.jar` | `af25cb08a82c72b3fb67508071ce627714bd2b57` |
| `suppsquared-neoforge-1.21-1.2.18.jar` | `0b6845d2afd22f91275a69ad270a0f0ecbfaebe3` |
| `moonlight-1.21.1-3.8.3-neoforge.jar` | `046821d548a1269164c792bab46d55798085c285` |
| `ImmersiveEngineering-1.21.1-12.4.2-194.jar` | `a4e90c2df8009040f6d022433c5d76635944dd59` |
| `aether-1.21.1-1.5.10-neoforge.jar` | `5b5592989c6d2aabf80a9f100e18c258813db497` |
| `deep_aether-1.21.1-1.1.5.1.jar` | `2cffdb08d7cb068186cf0fe766fd937425889af1` |
| `Oh-The-Biomes-Weve-Gone-NeoForge-2.6.3.jar` | `693292b80590ea4cf3b4d3e180a6532eac71f546` |
| `The_Undergarden-1.21.1-0.9.6.jar` | `75f1753eefa0b7b8e3afd7377deaf9fe71392476` |
| `ars_nouveau-1.21.1-5.13.5.jar` | `cdc946c63e1eb676f0e29e4640335c0ce68d7de3` |
| `occultism-1.21.1-neoforge-1.227.0.jar` | `ac94231b96a6dcf96bfea789099a59ea11ef25db` |
| `regions-unexplored-0.6.3-neoforge-21.1.jar` | `adf717dbb032ccaf7d100c0ba71bdea0fb21755a` |
| `legendarymonuments-neoforge-1.21.1-8.2-Keep-Fighting.jar` | `8c3a6110ef5356e39b8647f6e8ddd6bc53915644` |
| `cobbleworkers-neoforge-2.0.7-1.8.3.jar` | `8d6e53d1ac2311e99a3ac533c061367738b98fd3` |
| `smarterfarmers-1.21-2.2.4-neoforge.jar` | `49e58b648cfe1e5d6623b1b2c3ce5aca0b8c08df` |
| `create-1.21.1-6.0.10.jar` | in the frozen cache, used for BM4 only |

#### Vanilla and NeoForge 21.1.265

- `BonemealableBlock` has `isValidBonemealTarget(LevelReader, BlockPos, BlockState)`,
  `isBonemealSuccess(Level, RandomSource, BlockPos, BlockState)`, `performBonemeal(ServerLevel, RandomSource, BlockPos,
  BlockState)`, and the defaults `getParticlePos(BlockPos)` and `getType()` (`NEIGHBOR_SPREADER` or `GROWER`).
- `CropBlock.isValidBonemealTarget` is `!isMaxAge(state)`, and `isBonemealSuccess` is true. `performBonemeal` calls
  `growCrops`, which adds `Mth.nextInt(random, 2, 5)` to the age, caps it at `getMaxAge()`, and sets
  `getStateForAge(age)` with flag 2. `isMaxAge` is final, and `getAgeProperty` is protected.
- NeoForge's `BoneMealItem.applyBonemeal(ItemStack, Level, BlockPos, Player)` fires `BonemealEvent` first. A canceled event
  returns `isSuccessful()`. Otherwise, for a `BonemealableBlock` that is a valid target, on a `ServerLevel`, it calls
  `performBonemeal` when `isBonemealSuccess` passes, shrinks the stack by 1 and returns true. `BoneMealItem.growCrop`, the
  dispenser's path, passes a null player.
- `BoneMealItem.useOn` posts level event 1505 after a success. The client's `LevelRenderer.levelEvent` handles 1505 with
  `addGrowthParticles`, then `playLocalSound(pos, SoundEvents.BONE_MEAL_USE, ...)`. The switch has no other growth case.
- `Level.destroyBlock` posts level event 2001, whose client case plays the block's break sound, then drops the loot with
  `ItemStack.EMPTY`.
- `SweetBerryBushBlock.useWithoutItem` drops 1 + `nextInt(2)` berries, plus 1 at age 3, plays
  `SWEET_BERRY_BUSH_PICK_BERRIES` and sets age 1. `CaveVines.use` plays `CAVE_VINES_PICK_BERRIES`.
- `Block.getDrops(BlockState, ServerLevel, BlockPos, BlockEntity, Entity, ItemStack)` computes the loot.
  `Block.popResourceFromFace(Level, BlockPos, Direction, ItemStack)` spawns an item at the centre of a face, with a jitter
  of ±0.25 and a velocity out of the face. Both pop methods respect `doTileDrops` and give the default pickup delay.
- `Block.asItem()` is `Item.byBlock(this)`, cached. Wheat seeds are `new ItemNameBlockItem(Blocks.WHEAT, ...)`, so
  `Blocks.WHEAT.asItem()` is wheat seeds. A block can have two `BlockItem`s, and `Item.byBlock` keeps the last one: each
  Pam's crop has a crop item and a seed item that both place it, `asItem()` gives the seed item, and the loot drops only the
  crop item.
- `FarmBlock.fallOn` calls `CommonHooks.onFarmlandTrample`, which posts the cancelable `BlockEvent.FarmlandTrampleEvent`
  before `turnToDirt`.
- `Minecraft.startUseItem` sets `rightClickDelay` to 4, so a player who holds the use key applies bone meal every 4 ticks
  (BM5). `DispenserBlock` schedules its tick 4 ticks after its signal.
- The vanilla tag `minecraft:crops` holds wheat, carrots, potatoes, beetroots, the melon and pumpkin stems, the torchflower
  crop and the pitcher crop. `minecraft:bee_growables` adds the sweet berry bush and cave vines.

#### The crop families of COHO 2.5.0

The adapter column names the adapter of the Cultivator, then that of the Harvester. The [Decision](#decision) defines
each adapter.

| Mod and version | Family: block ids | Class (vanilla base) | Fully grown at | Bonemeal | Harvest | Adapters |
| --- | --- | --- | --- | --- | --- | --- |
| Minecraft 1.21.1 | `wheat`, `carrots`, `potatoes`, `beetroots` | `CropBlock` | age 7 (beetroots 3) | +2 to 5 | loot, replant the seed or crop item | growth, crop |
| Minecraft | `torchflower_crop`, `pitcher_crop` | `CropBlock`, `DoublePlantBlock` | they turn into flowers | yes | none: a flower | growth, never |
| Minecraft | `melon_stem`, `pumpkin_stem` | `StemBlock` | age 7 | +2 to 5 | none: the fruit is the harvest | growth, never |
| Minecraft | `attached_melon_stem`, `attached_pumpkin_stem` | `AttachedStemBlock` | a fruit is attached | no | break the faced fruit: 3 to 7 melon slices, 1 pumpkin | none, gourd |
| Minecraft | `sweet_berry_bush` | `SweetBerryBushBlock` | age 3 | +1 | loot 2 to 3 berries, then age 1 | growth, pick to age 1 |
| Minecraft | `cave_vines`, `cave_vines_plant` | `CaveVines` | `berries` true | sets berries | loot 1 glow berry, then berries false | growth, cave vines |
| Minecraft | `cocoa` | `CocoaBlock` | age 2 | +1 | loot 3 beans, replant on the same face | growth, break and replant |
| Minecraft | `nether_wart` | `NetherWartBlock` | age 3 | no | loot 2 to 4 warts, replant | none, break and replant |
| Cobblemon 1.8.4 | 70 berry trees (`#cobblemon:berries`) | `BerryBlock` with `BerryBlockEntity` | age 5 | +1 stage on a chance of 0.24 to 0.56 | `BerryBlockEntity.harvest(level, state, pos)` returns the berries and sets age 3; breaking above age 0 drops nothing | growth, berry tree |
| Cobblemon | 6 mints (`#cobblemon:mints`), `revival_herb`, `medicinal_leek`, `vivichoke_seeds` | `CropBlock` | age 7, 8, 3 and 7 | +1, or +2 to 5 for the herb | loot, replant | growth, crop |
| Cobblemon | `hearty_grains` | `CropBlock`, two blocks from age 4 | age 6 | +1 to 3 | loot of the upper half; the lower half sets itself to age 3 | growth, upper half |
| Cobblemon | `galarica_nut_bush` | `NutBushBlock` (`BushBlock`) | age 3 | +1 | loot 2 to 3 nuts, then age 1 | growth, pick to age 1 |
| Cobblemon | apricorns, terrain seeds, big and energy roots | `ApricornBlock`, `TerrainSeedBlock`, `RootBlock` | | | excluded (see the Decision) | none, never |
| Farmer's Delight 1.21.1-1.3.4 | `cabbages`, `onions` | `CropBlock` | age 7 | +2 to 5 | loot, replant; ripe cabbage drops no seed 7.9% of the time | growth, crop |
| Farmer's Delight | `tomatoes`, `tomatoes_on_rope` | `TomatoBlock`, `HangingTomatoBlock` (`CropBlock`) | age 3, each block | +1 to 2, climbs a rope | loot 1 to 2 tomatoes (5% rotten), then age 0; breaking would end the vine | growth, pick to age 0 |
| Farmer's Delight | `budding_tomatoes` | `BuddingTomatoBlock` (`BushBlock`) | becomes `tomatoes` | +1 to 4 | none: a growth stage | growth, never |
| Farmer's Delight | `rice` (lower), `rice_panicles` (upper) | `RiceBlock` (`BushBlock`), `RicePaniclesBlock` (`CropBlock`) | the panicle at age 3 | the lower block grows the panicle | loot of the panicle, 1 `rice_panicle`; no replant item, so the cell stays empty and the lower block grows a new panicle | growth, crop on the panicle |
| Farmer's Delight | mushroom colonies, wild crops, sandy shrub | `MushroomColonyBlock`, `WildCropBlock` | | spreaders | a harvest needs a tool or a player | outside the defaults |
| Expanded Delight 0.1.4 | `asparagus_crop`, `sweet_potato_crop`, `chili_pepper_crop`, `peanut_crop` | `EDCropBlock` (`CropBlock`) | age 7 | +2 to 5 | loot, replant | growth, crop |
| Expanded Delight | `cranberry_plant` | `CranberryPlantBlock` (`BushBlock`), on water | age 3 | +1 | loot 2 to 3 cranberries, then age 1 | growth, pick to age 1 |
| Miner's Delight+ 1.4.5 | `cave_carrots` | `CaveCarrotBlock` (`CropBlock`), on rich soil only | age 3 | +1 to 2 | loot 2 to 4, replant | growth, crop |
| Supplementaries 1.21.1-4.0.2 | `flax` | `FlaxBlock` (`CropBlock`), two blocks from age 4 | the lower half at age 7 | +2 to 5 | loot of the lower half (the upper has none), replant the lower half | growth, crop with two halves |
| Supplementaries, with Farmer's Delight | `rope_tomatoes`, `stick_tomatoes` | `TomatoRopeBlock`, `TomatoStickBlock` (FD `TomatoBlock`) | age 3 | as FD | no loot table in any jar, so a loot-based harvest yields nothing | growth, never |
| Immersive Engineering 12.4.2-194 | `hemp` | `HempBlock` (`CropBlock`), two blocks | the lower half at age 4 with its upper half | +2 to 5 on the lower half only | loot of the upper half (seed and fibre); the lower half stays at age 4 and grows a new top | growth, upper half |
| The Aether 1.5.10 | `berry_bush_stem`, `berry_bush` | `BerryBushStemBlock`, `BerryBushBlock` (`BushBlock`) | the block is `berry_bush` | the stem only, 45% | loot 1 to 3 blueberries and 1 stem item; replant the stem from the drops | growth on the stem, break and replant on the bush |
| Deep Aether 1.1.5.1 | `squash_stem`, `attached_squash_stem`, `blue_squash`, `green_squash` | `SquashStemBlock` (`StemBlock`), `AttachedStemBlock` | a fruit is attached | +2 to 5 on the stem | break the faced squash | growth and never on the stem, gourd on the attached stem |
| Oh The Biomes We've Gone 2.6.3 | `pale_pumpkin_stem`, `attached_pale_pumpkin_stem`, `pale_pumpkin` | `StemBlock`, `AttachedStemBlock`, `PalePumpkinBlock` | a fruit is attached | +2 to 5 on the stem | break the faced pumpkin | growth and never on the stem, gourd on the attached stem |
| Oh The Biomes We've Gone | `blueberry_bush` | `BWGBerryBush` (`SweetBerryBushBlock`) | age 3 | +1 | loot 2 to 3 blueberries, then age 1 | growth, pick to age 1 |
| Oh The Biomes We've Gone | `oddion_crop` | `OddionCrop` (`SweetBerryBushBlock`) | age 3 | +1; bonemeal at age 3 starts a hatch that destroys the crop and spawns a mob | loot 2 to 3 bulbs, then age 1 | growth that stops at age 3, pick to age 1 |
| The Undergarden 0.9.6 | `underbean_bush`, `blisterberry_bush` | `UnderbeanBushBlock`, `BlisterberryBushBlock` (`BushBlock`) | age 3 | +1 | loot 2 to 3 (blisterberries also rotten ones), then age 1; a blisterberry bush damages any item inside it | growth, pick to age 1 |
| The Undergarden | `gloomgourd_stem`, `gloomgourd_stem_attached`, `gloomgourd` | `StemBlock`, `AttachedStemBlock`, `GloomgourdBlock` | a fruit is attached | +2 to 5 on the stem | break the faced gloomgourd | growth and never on the stem, gourd on the attached stem |
| Ars Nouveau 5.13.5 | `magebloom_crop` | `MageBloomCrop` (`CropBlock`) | age 7 | +2 to 5 | loot 1 magebloom and 1 seed item, replant | growth, crop |
| Ars Nouveau | `sourceberry_bush` (in no crop tag) | `SourceBerryBush` (`BushBlock`) | age 3 | +1 | loot 2 to 3, then age 1; its own pick is in `useItemOn` | growth, pick to age 1 |
| Occultism 1.227.0 | `datura` | `ReplantableCropsBlock` (`CropBlock`) | age 7 | +2 to 5 | loot 1 datura and 1 to 4 seeds, replant; its own pick needs a player | growth, crop |
| Regions Unexplored 0.6.3 | `salmonberry_bush` | `SalmonBerryBushBlock` (`SweetBerryBushBlock`) | age 3 | +1 | loot 2 to 3, then age 1 | growth, pick to age 1 |
| Regions Unexplored | `duskmelon` | `DuskmelonBlock` (`BushBlock`) | age 2 | +1 while below 2 | loot 1 to 2 slices, then age 0 | growth, pick to age 0 |
| Pam's HarvestCraft 2 Crops 1.0.9 | 97 crops, `pamhc2crops:pam<crop>crop`, all in `minecraft:crops` | `BlockPamCrop` (`CropBlock`) | age 7 | +2 to 5 | loot: 1 crop item at any age, plus Binomial(3, 4/7) at age 7; replant from the dropped crop item, because the seed item never drops; no right-click harvest | growth, crop |
| Pam's HarvestCraft 2 Crops | 6 gardens | `GardenBlock` (`BushBlock`) | | spreads | | outside the defaults |
| Pam's HarvestCraft 2 Trees 1.0.9 | 47 hanging fruit and 3 log fruit, `pamhc2trees:pam<fruit>`, in no crop tag | `BlockPamFruit`, `BlockPamLogFruit` (`CropBlock`) | age 7 | +2 to 5 | loot 1 fruit item, then age 0, as Pam's own right-click does; breaking would end the fruit, which no item replants | growth, pick to age 0 |
| Pam's HarvestCraft 2 Trees | 50 saplings | `SaplingBlock` | | | trees are outside the defaults | outside the defaults |
| Legendary Monuments 8.2 | `iceroot_carrot`, `shaderoot_carrot` | `CropBlock` | age 7 | +2 to 5 | 1 carrot, no seed; COHO's `reverie_loot` replaces the loot | outside the defaults |

The other COHO 2.5.0 mods add no crop: Ars Nouveau's Flavors & Delight 2.2.5, Chef's Delight 1.0.6, L_Ender's Cataclysm
Delight 1.0.10b, Ocean's Delight 1.0.4, Tomtaru's Cobblemon & Farmer's Delight Tweaks 2.0.4, Supplementaries Squared
1.21-1.2.18, Farmer's Cutting Everything 1.0.1 and Farmer's Cutting: Regions Unexplored 1.1b. Ender's Delight 1.3.1 adds
End plants that are no crop: no crop tag and no bonemeal. My Nether's Delight 1.10.4.1 adds fungus colonies of Farmer's
Delight's class, and powdery canes that grow as bamboo does. Both stay outside the defaults. Smarter Farmers 1.21-2.2.4
changes how villagers replant, and SmallColonies 1.8 extends MineColonies. The farming roles touch neither.

Other findings that shape the decision:

- Cobblemon adds nothing to `minecraft:crops`. It adds `#cobblemon:crops`, `#cobblemon:apricorns`,
  `#cobblemon:saccharine_leaves` and the galarica nut bush to `minecraft:bee_growables`. `#cobblemon:crops` holds the
  terrain seeds. A terrain seed that reaches age 4 writes its plant's top half into the cell above with no check, so
  bonemeal under a chest erases the chest.
- Some blocks stay valid bonemeal targets where bonemeal does nothing: Farmer's Delight's rice under a blocked cell and
  the top of a tomato vine. A Cobblemon berry tree is a valid target at every age below 5, and grows on a chance.
- The right-click harvests of the sweet berry bush, FD tomatoes and Cobblemon berry blocks play sounds. Those of datura,
  the galarica nut bush and the Aether bush need a real player.
- Flax and hemp keep both halves under one block id in `minecraft:crops`. Bonemeal on a hemp upper half rewrites it as a
  lower half. When one half breaks the other through a shape update, the drops of the second half spawn at its own
  position. Flag 32 does not stop those drops, and flag 16 skips the shape update.
- The farmlands of the Aether, Oh The Biomes We've Gone, the Undergarden and Regions Unexplored extend `FarmBlock`.

#### Prior art and COHO's pack data

- **Create 6.0.10, Mechanical Harvester** (`HarvesterMovementBehaviour`, BM4). A `CropBlock` is ripe at `isMaxAge`. Another
  block with no collision is ripe when its `IntegerProperty` named `age` is at its maximum, with cocoa and the sweet berry
  bush handled apart. It cuts a `CropBlock` back to `getStateForAge(0)` and the sweet berry bush to age 1.
- **Cobbleworkers 2.0.7.** Its jobs pick targets by tag or block id and `"age": "max"`. It takes the loot of
  `BlockState.getDrops` with an empty tool, and berries through `BerryBlockEntity.harvest(level, state, pos, null)`. It never
  breaks a crop: it sets the age back in place (crops and mints to 0, berries to 3, the nut bush to 1), so the seed stays in
  the drops. It removes the upper half of hearty grains. It has no bonemeal job.
- **Smarter Farmers 2.2.4.** It checks ripeness through Moonlight's grow interface, tags, `CropBlock.isMaxAge`, the sweet
  berry bush and standard age properties. It breaks with `destroyBlock` and replants `block.asItem()` from the villager's
  inventory.
- **COHO's `regrow_blocks.js`** (KubeJS, read only). It regrows three `mega_showdown` resource nodes one tick after a player
  breaks one, and it cancels a break by a fake player or by no player. It listens only to `BlockEvents.broken` of those three
  ids. The farming roles post no break event and their tags hold none of those blocks, so the script never sees a harvest.
- **COHO's other pack data.** No pack file changes `minecraft:crops`. `reverie_loot` replaces the loot of the two Legendary
  Monuments carrots, which stay outside the defaults.

## Decision

1. **Two roles, two handlers.** The Cultivator (`cultivator.json`, handler `cobblemon_dynamics:cultivator`) for the Ground
   type, and the Harvester (`harvester.json`, handler `cobblemon_dynamics:harvester`) for the Grass type. Both are
   auto-assign roles with priority 0 and range 6, and both scan blocks (`ScanKind.BLOCK`), as the Crystal Tender does.
   Quick Work takes them as it takes every auto-assign role (CD-QWK-03). The effects are silent: Mud Sport for the
   Cultivator (`mudsport`, `status`, `special`, `physical`, and the target particle `cobblemon:mudsport_target`, because
   `mudsport_actor` plays a sound), and Razor Leaf for the Harvester (`razorleaf`, `physical`, `special`, with
   `cobblemon:razorleaf_actor` and `cobblemon:razorleaf_target`).
2. **The tags decide which blocks.** The two target tags of ruling 5 hold `#minecraft:crops` and explicit entries for the
   crops that no mod tags. An entry of a mod other than Minecraft and Cobblemon has `"required": false`, so the tags load
   without that mod. `#minecraft:bee_growables` stays out, because Cobblemon puts apricorns and saccharine leaves in it, and
   `#cobblemon:crops` stays out, because it holds the terrain seeds. Apricorns are in neither default tag.
3. **Adapters decide how.** An adapter reads one family of crops. The farming handlers take the first adapter that claims
   a block, in this order:
   - The adapter tags, which a pack can extend as it extends the target tags:
     - `#cobblemon_dynamics:harvest/never`: the Harvester leaves the block. The defaults hold the stems, the flower crops,
       `budding_tomatoes` and Supplementaries' rope and stick tomatoes.
     - `#cobblemon_dynamics:harvest/pick_to_age_0` and `harvest/pick_to_age_1`: a pick. The plant stays, its loot drops, and
       its `age` property goes to 0 or to 1, with its other properties kept.
     - `#cobblemon_dynamics:harvest/upper_half`: the plant is fully grown when its lower block is at its maximum age and its
       upper block exists. A harvest takes the loot of the upper block and clears it, and the lower block follows its own
       rule.
     - `#cobblemon_dynamics:harvest/break_and_replant`: for a block that is no `CropBlock`. It is fully grown when its `age`
       property is at its maximum, or at once when it has no `age` property. A harvest breaks it and replants it.
   - The adapters of classes: Cobblemon's `BerryBlock` (the berry tree), `CaveVines` (the berries flag),
     `AttachedStemBlock` (the gourd), and `CropBlock` (break and replant).
   - For growth only, any `BonemealableBlock`.

   The tags come first because some crops need another harvest than their class gives. FD tomatoes and Pam's fruit are
   `CropBlock`s that a pick harvests, and hearty grains and hemp are `CropBlock`s whose upper half is the harvest. A block of
   a target tag that no adapter claims gets no worker, and the load line names it once. The adapters read crops through
   vanilla classes, block state properties and tags only. The code names no class of a crop mod, so no crop mod needs a
   compat package, and Cobblemon's classes are the only mod classes it names.
4. **Two halves.** A block whose state has `half` = upper is read at the block below it, for growth and for the crop
   adapter. When the crop adapter breaks a two-block crop, it clears the upper block first with
   `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (flag 18), so that no shape update breaks it with its own drops and sound. The loot
   of both halves drops.
5. **The growth use.** The Cultivator calls NeoForge's `BoneMealItem.applyBonemeal(ItemStack(Items.BONE_MEAL), level, pos,
   null)`. The stack is a token that no inventory holds, so no item is used. The call runs the block's own bonemeal rule and
   `BonemealEvent`, as a dispenser does, so a mod that changes or blocks bonemeal on its crops still decides. After a use
   that returns true, the handler sends 15 `minecraft:happy_villager` particles inside the block through
   `ServerLevel.sendParticles`. That is the green of a bone meal use with no sound: level event 1505 would play
   `BONE_MEAL_USE`.
6. **Pace and Work Stamina.** A Cultivator applies one use every ceil(1,200 ÷ ceil(30 × L ÷ 100)) ticks: 1 use per minute at
   level 1, 15 at level 50 and 30 at level 100. Each use drains 100 ticks of capacity beyond the drain of Working. Both
   numbers live in the role file (`uses_per_minute_at_level_100`, `stamina_ticks_per_use`). A Cultivator has demand while
   the block is a valid bonemeal target and its harvest adapter, if any, does not find it fully grown, so it stops where the
   Harvester starts, and it never ripens an oddion into a hatch. After 20 uses in a row that change no block of the crop, the
   crop has no demand for that worker for 6,000 ticks.
7. **The harvest.** A Harvester harvests once, `harvest_ticks` (20) after its work starts. It takes the loot of
   `Block.getDrops` with an empty tool and no entity, so the pack's loot tables decide and no Fortune applies. A berry tree
   gives its berries through `BerryBlockEntity.harvest(level, state, pos)`, which sets it back to age 3 with no player and no
   sound. Each item spawns through `Block.popResourceFromFace` on the crop's face toward the worker's work spot: beside the
   crop, outside a bush that damages items, and inside the work area of the pasture, where a Magnet collects it. A crop that
   a harvest breaks is replanted from its drops:
   - The replant item is a dropped `BlockItem` whose block is the crop's own block, such as wheat seeds or a Pam's crop
     item, else a dropped `BlockItem` whose block is in the Cultivator's tag, such as the Aether's stem item. `asItem()`
     alone would miss the Pam's crops. One item leaves the drops.
   - The new state is `getStateForAge(0)` for a `CropBlock`. For another block, it is the state with the lowest `age` of
     the block that the item places, and it keeps the values of the properties that it shares with the harvested state,
     such as the facing of cocoa.
   - With no replant item, or a state that cannot survive in the cell, the cell goes to its fluid, air or water, and all
     items drop. The approver allows no seed from nothing.
8. **Silence.** The farming handlers post no level event, play no sound, and call neither `Level.destroyBlock` nor a block's
   use method. A pick sets the state itself. `SilenceTest` scans the sources.
9. **Farming claims.** The claim registry keys a farming work claim by the crop and the role: a crop holds at most one
   Cultivator claim and one Harvester claim. A leash binding of a farming role reserves its crop only against claims of its
   own role. So a Cultivator and a Harvester, auto-assigned or leashed, share a crop, and two workers of one role do not.
   Quick Work refuses an order on a crop that a claim or binding of the order's role holds. This is a third kind of sharing
   beside the exclusive claims and the shared logistics claims of [ADR-0005](0005-global-claim-registry.md).
10. **Crop to crop, leashed crops and Quick Work.** An auto-assigned farming worker releases its claim within 20 ticks once
    its crop has no demand, instead of 200 ticks of Standby, and the next scan pass gives it the nearest crop. A leashed
    farming worker waits in Standby with its claim and its binding. A binding of a farming role survives while its crop's
    cell holds air or a fluid above a block that is not air, so a player's harvest by hand keeps the leash. A Quick Work order
    of a farming role ends within 20 ticks once its crop has no demand. A Cobblemon that fits both roles gets, for Quick
    Work, the role with demand on the aimed crop, and, for the Work Leash, the role of its primary type.
11. **Land claims.** The 1.0.0 gate applies as it is: the scan, the poll and the beds ask the pasture check (CD-LND-01 to
    CD-LND-05), and Quick Work asks the owner's checks. The Harvester also asks the same checks in the tick of each harvest,
    for every block that the harvest changes, and harvests nothing on a refusal. A right that ends between two polls
    therefore never lets a harvest through, and the fruit of a gourd in another chunk gets its own check. The gate asks
    whether the placer may right-click the block, and a pick is a right-click. Design decision 7 of the plan asks whether a
    harvest that breaks a block should ask about breaking.
12. **Farmland.** A worker that the work engine directs never tramples farmland: a listener cancels
    `BlockEvent.FarmlandTrampleEvent` for it. Wild Cobblemon and players keep the vanilla rule.
13. **Apricorns.** The Harvester never matches a block of `#cobblemon:apricorns` or of `ApricornBlock`, also when a datapack
    adds one to its tag. The Cultivator's default tag leaves them out, and a pack may add them.
14. **Observability.** When the tags load or reload, one INFO line per farming role counts the tagged blocks by adapter and
    names the tagged blocks that no adapter claims. With COHO 2.5.0's crop mods, that list is empty.
15. **Testing.** The adapters are pure rules over plain facts of a block (its class flags, its tags, its `age` values and
    its loot), so unit tests check every family of the table with the facts that RES8 read. Six GameTests use vanilla
    crops only. The modded families get the test pack's load-line case on a server with COHO 2.5.0's crop mods, and one Demo
    on the COHO server, where the approver plays. This follows the approver's rulings on tests: "avoid game tests for
    high-confidence code" (2026-11-09), "Skip the client tests and any tests that you're confident about with the code"
    (2026-11-11), and "do we really need the game tests? i thought we were pretty confident in these fixes and we'll be
    testing them actively in the server" (2026-11-11).

## Consequences

- Any mod that tags its crops `minecraft:crops` gets both roles with no change, through the `CropBlock` and growth
  adapters. A crop that its class does not describe, such as a vine or a fruit, needs an adapter tag, which a datapack can
  give.
- A Harvester yields what a harvest by hand yields: no Fortune, no knife bonus of Farmer's Delight, and one seed less where
  a hand replant would use one. A pick drops the full loot of the ripe state, so the base of an FD tomato gives one tomato
  seed per pick that its own right-click does not give.
- An empty cell is possible: cabbage, the Legendary Monuments carrots and wild plants on dirt drop no replant item now and
  then. The cell stays empty, and a leashed worker waits there for the player.
- A level 100 Cultivator tires about 3.5 times as fast as a Stoker of its level. Without beds it works about 22% of the
  time, and on a Starlit Bed about 70%.
- The drops lie on the ground and despawn after 6,000 ticks when no Magnet or player takes them.
- Pam's hanging fruit grows about 4 blocks under the top of its tree's leaves, and log fruit sits in the trunk. A
  Harvester reaches it only from a work spot within its range of 6 blocks with a line of sight, so some fruit of a tall
  tree stays for the player.
- Bonemeal growth fires no `CropGrowEvent`, as for a player's bone meal, so mods that count random-tick growth, such as Ars
  Nouveau's Agronomic Sourcelink, do not see it.
- The adapters follow the bytecode of the tested versions. A later version of a crop mod that changes a family needs a new
  check, and COHO's next update runs the Demo again.
- The load line and the Demo are the only checks with real modded classes. A family whose class misbehaves at run time
  fails there, not in CI.

## Alternatives considered

- **Call each block's own right-click with a fake player.** Rejected. The picks play sounds, some need a real player, claim
  mods would see a fake player, and the drops land inside the plant.
- **`Level.destroyBlock` and replant.** Rejected for sound (level event 2001), and because breaking ends berry trees, FD
  tomato vines and Pam's fruit.
- **The generic `age` rule of Create for every tagged block.** Rejected as the default for unknown blocks: sugar cane's
  `age` is a timer, and a duskmelon's maximum is 2. It stays available through the adapter tags.
- **Adapter files in JSON**, one per family with its kind and numbers. Deferred: the adapter tags cover every family of
  COHO 2.5.0 with no new loader, and they merge across datapacks.
- **One exclusive claim per crop.** Rejected: a leashed Cultivator would keep the Harvester off its crop, and the
  approver's leash flow could not work.
- **Harvest into a container.** The approver chose the ground ("#1").
- **`#minecraft:bee_growables` as the Cultivator's default.** Rejected: it would ripen apricorns and saccharine leaves.
- **Level event 1505 for the effect.** Rejected: it plays `BONE_MEAL_USE`.
- **A GameTest run with the COHO crop mods**, as the claim runs have. Deferred to the approver: design decision 10 of the
  plan.
