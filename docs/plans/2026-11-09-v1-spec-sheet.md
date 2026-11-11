# Cobblemon Dynamics 1.0 spec sheet

Status: agreed (2026-11-09). Approver: Trevor Forbes (GitHub: HonorCodes). Rows prefix: CD.

Corrections after agreement:

- 2026-11-09: CD-CFG-01 and the term "server config" name the `config/` folder, because NeoForge 21.1 writes
  server configs there (found in M1).
- 2026-11-10, approver's play-test ruling: CD-HEAL-15 spawns only `cobblemon:heal_circles`. The file of
  `cobblemon:heal_sparkles` calls `math.random()` with no arguments, and the Cobblemon 1.8.4 client crashes on it.
- 2026-11-10, approver's play-test ruling: the Role table gives the Stoker and the Burner Keeper Ember instead of
  Flamethrower, which was too flashy for a loop. CD-FX-02 checks the role files against it.
- 2026-11-10, approver's play-test ruling: CD-FX-01 and CD-FX-03 let each work animation play to its end, then idle for
  `idle_ticks` before the next one. A loop every 40 ticks cut off animations of 3 s and started them again.
- 2026-11-10, approver's play-test ruling: all work is silent. CD-FX-03, CD-FX-09, CD-HEAL-15 and CD-CFG-06 drop the
  `cry` fallback of R46, every work sound and the `workSounds` client option of R21. The cry animation played the cry
  of each model with no attack animation on every loop.
- 2026-11-10, approver's play-test ruling: CD-ASG-09 prefers a work spot beside the target at its base height and never
  picks one that stands on the target. Before, the spot nearest to the worker won, which could be on top of the target. A
  spot that a worker cannot walk into on the level, such as a pit beside a water wheel, comes last.
- 2026-11-10, approver's play-test result: the second play-test of the Phase 1 build, on the jar of PR #12, is GREEN.
  The play-test note in `docs/testing/phase-1-gate.md` records it, so CD-REL-13 is complete. No row changes.
- 2026-11-10, approver's ruling after the play-test (R59): Fire-types do not heat Blaze Burners. The Burner Keeper role
  and its role file are gone, so the Role table, G3, the M3 scope and R15 lose it. The BRN rows keep their ids and now
  describe the Superheater: CD-BRN-01 holds `KINDLED` below level 50, and CD-BRN-05 counts 1 boiler heat from a
  Superheater below level 50. CD-SUP-02 adds that a Fire-type never claims a Blaze Burner, and the burner case of
  CD-ASG-16 now gives the Superheater as the burner's only role.
- 2026-11-10, approver's ruling after the play-test (R60): a Charge Cell block. New rows CD-GEN-08 to CD-GEN-12 cover
  its capacity and faces, its push into the blocks around it, its comparator signal, the FE that its item keeps, and its
  recipe, which the Recipes table lists. CD-REL-08 adds the Charge Cell to the player guide, and CD-ART-01 adds its art
  to the art of M8.
- 2026-11-10, approver's ruling after the play-test (R61): a creative tab. The new row CD-UI-16 lists every item of the
  mod in the tab `cobblemon_dynamics:main`, and in no vanilla tab.
- 2026-11-10, approver's ruling after the multiplayer audit (R62): an operator command for a binding whose binder does not
  come back. The new row CD-UI-17 gives `/cobblemondynamics unbind <pos>`, which clears every binding whose target or
  Telekinesis source is that block and tells each binder.
- 2026-11-10, approver's ruling after the multiplayer audit: CD-LSH-05 names a Pokémon of another player by its species,
  as "Already assigned to another player's {species}", so a refusal never shows another player's nickname.

The key words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT" and "MAY" in this document are to be
interpreted as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they appear in all
capitals, as shown here.

This sheet lists what Cobblemon Dynamics 1.0 does. 1.0 is the first public release. It ships in
three phases (see the Milestones table and the REL rows). Phase 1 builds and tests the v1 scope
privately. Phase 2 adds the v2 roles and the final art, and is also tested privately. Phase 3
publishes. Rows in the TLK, MAG, FAN, TLW and ART areas belong to Phase 2. All other rows belong to
Phase 1, and the BAL rows apply to the roles of each phase.

Each row states one behaviour and holds the test that proves it. A phase is done when every MUST
and MUST NOT row in its scope has a passing test. Nobody builds against a `draft` row. The approver
moves rows to `agreed`.

Design context lives in `ARCHITECTURE.md`, `docs/roles.md`, the ADRs in `docs/adr/` and the
research notes in `docs/research/2026-11-09-integration-points.md`.

## Terms

| Term | Meaning |
| --- | --- |
| Work Mode | A per-pasture setting. While it is on, Dynamics controls the owned Pokémon that are tethered to that pasture. |
| work area | The box around a pasture block in which its workers find targets and beds. By default it reaches 8 blocks horizontally and 6 blocks vertically from the pasture block in each direction. |
| worker | A Pokémon tethered to a pasture in Work Mode. A healer is a worker too. |
| target | The block or entity that a worker acts on: a machine, a bed or a healed ally. |
| canonical target | The one position that stands for a multiblock target, for example the controller of a large water wheel. |
| claim | A claim registry entry that gives one worker one target for now. The kinds are the work claim (on a machine), the bed claim (on a bed) and the heal claim (on an ally). The work engine makes every claim. Claims are never saved. |
| leash binding | A reservation that a player makes with the Work Leash. It is not a claim. It is saved with the Pokémon, and it reserves its target for that worker, also while the worker sleeps or its pasture's Work Mode is off. |
| auto-assigned worker | A worker with no leash binding. The work engine picks its targets. |
| binder | The owner of a worker that has a leash binding. |
| pending source | The first container of a Telekinesis binding, which the Work Leash holds while it waits for the destination click. |
| role | A job that a datapack role file defines: eligible types, an auto-assign flag, a priority, a range, a handler, handler parameters and an effect. |
| role file | The datapack JSON file of one role. It holds the role-specific numbers: rates, formula constants, level gates, ranges, effect ids and the priority. It holds no species lists. |
| server config | The NeoForge server config `cobblemon_dynamics-server.toml`. NeoForge 21.1 writes it to the instance's `config/` folder. A copy in a world's `serverconfig/` folder overrides it for that world. It holds the engine-wide numbers: work area, scan cadence and budget, stamina per level, bed factors, healer recovery and timeouts. |
| handler | The code that finds, checks and works one family of targets. |
| demand | The target can use work now, for example a furnace whose recipe can run. |
| driven target | A wheel, a hand crank or a fan that a worker is Working on. |
| Idle, Traveling, Working, Standby, ToBed, Sleeping, RestingInPlace, Healing | The worker states. A worker is in exactly one of them. Working means at the work spot with demand. Standby means the worker waits with no work: at the work spot with no demand, or, for a leashed worker, during the wait after a missed arrival. ToBed means walking to a claimed bed. Sleeping means lying on a bed. RestingInPlace means sleeping where the worker stands. Healing means a healer walks to an ally or heals it. |
| Tired | A flag, not a state. The stamina component sets it when Work Stamina reaches 0% and clears it at 100%. A Tired worker is in ToBed, Sleeping or RestingInPlace. |
| worker rule | Full recovery from 0% to 100% takes 3 min × level. |
| healer rule | Full recovery takes 4 × (300 − 270 × (L − 1) ÷ 99) min. It applies to a Pokémon with a Fairy type and no machine binding. |
| work focus entity | An invisible, unsaved entity at a target's center. Only a Working worker has one. Cobblemon particles aim at it. |
| placer | The player that Cobblemon records in the pasture block entity's `ownerId` when the pasture is placed. `ownerId` is the one source of pasture ownership. |
| operator | A player with permission level 2 or higher. |
| legendary or mythical | A Pokémon whose form carries the Cobblemon label `legendary` or `mythical`. |
| Phase 1, Phase 2, Phase 3 | The release phases: the private test of v1, the private test of v2, and the public release. The Milestones table lists their milestones. |

## Goals

- G1: A player switches a Cobblemon pasture to Work Mode, and its owned Pokémon power and operate machines in the work area, with no stand block.
- G2: Water-types turn Create water wheels with no water, Electric-types generate Forge Energy by level, and Fire-types fuel furnaces as if they burned coal.
- G3: Thirteen types get fifteen roles across vanilla Minecraft, Create and Applied Energistics 2: eleven in Phase 1 and four in Phase 2.
- G4: Only one worker acts on a target at a time, across all pastures and players, and wild Pokémon never work.
- G5: A player binds a specific pastured Pokémon to a specific machine with the Work Leash.
- G6: Work Stamina, four bed tiers and Fairy healers make worker uptime something that a player plans, as in Palworld.
- G7: Players see the state and Work Stamina of each worker in the world and in the pasture panel.
- G8: Dynamics runs in one modpack with Cobbleworkers and Cobblebase.
- G9: 1.0 ships as a public MPL-2.0 release on Modrinth and CurseForge after two private test phases, and joins the approver's modpack.
- G10: No single Pokémon solves an engineering problem that the player is meant to manage. Pokémon are an early-to-midgame option that a fleet can make competitive, and top-tier engineering always outperforms them.
- G11: Every new block, item and UI sprite uses original art in vanilla 16 × 16 style, and the approver approves each piece before the public release.

## Non-goals

- A Cobblemon Work Stand block. Work Mode on the pasture replaces it (ADR 0001).
- Fabric, Forge, and Minecraft versions other than 1.21.1 (ADR 0002).
- Jobs that make items from nothing. Cobbleworkers, Cobblebase and Cobblemon Pasture Loot cover loot.
- The later ideas: Pollinator (Bug), Grower (Grass) and Freezer (Ice). `docs/roles.md` holds them.
- A role for the Ground or Dark type. Neither type has a role.
- Create valve handles. Create turns a valve handle only by a fixed angle, so a valve handle cannot give continuous power.
- Work by party Pokémon or by Pokémon that a player sent out. Only pastured Pokémon work.
- Effects of Work Stamina on battles.
- Level scaling for roles other than the Generator, Pump, Muse, Telekinesis, Magnet and Tailwind, and the Superheater's level gate.
- An output cap beyond the values in role files and the server config. A pack that raises a rate gets that rate.
- A server-only install. The mod adds items, blocks and screens, so clients need it too.
- Work suitability tables per species. Eligibility comes from the types in role files, and role files hold no species lists.

## Inputs

Rulings R1 to R58 come from the design session with the approver on 2026-11-09. R59 to R61 come from the approver's rulings after the Phase 1 play-test, on 2026-11-10. R62 comes from the approver's rulings on the multiplayer audit, on 2026-11-10.

| Input | Link |
| --- | --- |
| R1. Ruling: workers live in pastures. A Work Mode toggle in the pasture's interface replaces the Work Stand block. | ADR 0001 |
| R2. Ruling: only one Pokémon acts on a block or entity, globally. Wild Pokémon never work. | ADR 0005 |
| R3. Ruling: the Work Leash binds a Pokémon to a target. Right-click the Pokémon, then right-click the target. Sneak + right-click on the Pokémon clears the binding. | none |
| R4. Ruling: a Water-type turns a Create water wheel with Water Gun aimed at its center, with no water source. | none |
| R5. Ruling: an Electric-type outputs FE, exponential from 10 FE/t at level 1 to 5,000 FE/t at level 100, ×2 for legendary or mythical, with Thunder Shock. | none |
| R6. Ruling: a Fire-type fuels furnaces as if they burned coal, with Flamethrower. | none |
| R7. Ruling: NeoForge 1.21.1 only. | ADR 0002 |
| R8. Ruling: the code license is MPL-2.0. | `LICENSE` |
| R9. Ruling: public release at v1, with publishing, player docs, config and compatibility rules. | none |
| R10. Ruling: Work Stamina is in v1. Capacity is 3 min of work per level, with no rarity effect. Recovery time equals drain time. A Tired Pokémon does not work until it is back at 100%. | ADR 0008 |
| R11. Ruling: four bed tiers cut recovery time to 1/2, 1/3, 1/4 and 1/8. Each tier is crafted from the tier below. Tier 4 needs a Nether Star and 4 Rare Candy. | none |
| R12. Ruling: a Tired Pokémon claims the nearest unclaimed bed, walks to it, and loops the sleep effect silently. A bed takes a Pokémon of any size. | none |
| R13. Ruling: a Fairy healer restores 100% of an ally for 25% of its own stamina, at any level. It heals in its own pasture only, never Fairy allies, Tired allies first, then the lowest ally under 25%. The heal shows sparkles on the ally. | ADR 0008 |
| R14. Ruling: healer recovery time falls linearly from 1,200 min at level 1 to 120 min at level 100. | ADR 0008 |
| R15. Ruling: the v1 roles are Wheel Driver, Pump, Generator, Stoker, Burner Keeper, Brew Stoker, Superheater, Cranker, Crystal Tender, Brewer, healer and Muse. R59 removes the Burner Keeper. | `docs/roles.md` |
| R16. Ruling: the v2 roles Telekinesis, Fan Catalyst, Tailwind and Magnet ship in 1.0. Phase 1 tests v1 privately, Phase 2 builds and tests v2 privately, and then 1.0 goes public and joins the approver's pack. | `docs/roles.md` |
| R17. Ruling: each water wheel stores its own spin direction. Sneak + right-click with the Work Leash flips it. | none |
| R18. Ruling: Muse power is ceil(15 × level ÷ 100) bookshelves, capped at 15. Unleashed Fairy-types heal. A Fairy-type is a Muse only when leashed. | none |
| R19. Ruling: the Pump is leash only. | none |
| R20. Ruling: Work Mode pastures belong to Dynamics. Dynamics switches Cobbleworkers and Cobblebase off in them. | ADR 0009 |
| R21. Ruling: work sounds are off by default, with a client toggle. | none |
| R22. Ruling: Work Stamina shows over the heads of workers while a player holds the Work Leash, and in the pasture panel. | none |
| R23. Ruling: a Brain work activity controls workers. | ADR 0004 |
| R24. Ruling: the approver approved design sections 1 to 6: architecture, worker lifecycle and claims, stamina, beds and healers, role mechanics, UI, compatibility, failures, performance, testing, CI and release. | none |
| R25. Ruling: balance. No single Pokémon solves an engineering problem that the player is meant to manage. Top-tier engineering always outperforms Pokémon, and a fleet of Pokémon can compete. Roles whose machine caps the output are exempt: Wheel Driver, Cranker, Tailwind and Fan Catalyst. | none |
| R26. Ruling: a Dragon-type superheats from level 50. Below level 50 it keeps a burner Kindled. | none |
| R27. Ruling: Telekinesis claims only its source and moves ceil(64 × L ÷ 100) items per second. | none |
| R28. Ruling: a Fire-type fan catalyst gives blasting by default, and a per-fan flip with the Work Leash switches that fan to smoking. | none |
| R29. Ruling: Telekinesis, Fan Catalyst, Tailwind and Magnet are leash only. | none |
| R30. Ruling: Tailwind adds floor(real sails × L ÷ 100) virtual sails, and the total stays within Create's 128-sail cap. | none |
| R31. Ruling: the Magnet collects from its pasture's whole work area at ceil(64 × L ÷ 100) items per second. | none |
| R32. Ruling: the first public release ships original models and textures for each bed, the Work Leash, the UI sprites and the mod icon. Claude drafts them, the approver approves each one on a screenshot review page, and the style is vanilla 16 × 16. | none |
| R33. Ruling: the approver approved the balance table and design sections 7 and 8: the v2 role mechanics, the art list and bed concepts, and the milestones M0 to M9. | none |
| R34. Ruling: the bed names are Hay Bed, Wool Bed, Plush Bed and Starlit Bed, and the Wool Bed recipe takes any wool block. | none |
| R35. Ruling (was A1): the Work Mode button sits in the pasture panel, directly above the Recall button. | none |
| R36. Ruling (was A2): head bars show for workers within 16 blocks of the player. | none |
| R37. Ruling (was A3): an open pasture panel refreshes its Work Stamina values every 20 ticks. | none |
| R38. Ruling (replaces A4): Idle and Traveling workers also show a state icon. Seven icons cover the eight states: Idle, Traveling, Working, Standby, Tired (for ToBed and RestingInPlace), Sleeping and Healing. | none |
| R39. Ruling (was A5): an operator has permission level 2 or higher. A pasture with no recorded placer accepts Work Mode requests from operators only, and a player who breaks and places it again becomes its placer. | none |
| R40. Ruling (was A6): a wheel or a hand crank with no stored direction turns in Create's positive speed direction. | none |
| R41. Ruling (was A7): the Generator pushes FE through the face nearest the worker that accepts energy. | none |
| R42. Ruling (was A8): the Brew Stoker role file has a higher priority than the Brewer role file. | none |
| R43. Ruling (was A9): while real water turns a wheel, the Wheel Driver reports no demand, so its worker waits in Standby and its Work Stamina does not drain. | none |
| R44. Ruling: an auto-assigned worker that has been in Standby for 200 ticks (10 s) or more moves to an unclaimed target with demand for one of its roles in its work area, checked on each scan. A leashed worker never moves off its bound target. | none |
| R45. Ruling (was A10): a worker in Standby checks for demand every 20 ticks. | none |
| R46. Ruling (was A11): a role falls back to the `cry` animation when the model has none of the role's listed animations. | none |
| R47. Ruling (was A13): when two unclaimed beds are the same distance away, a Tired worker takes the higher tier. | none |
| R48. Ruling (was A14): the mod id is `cobblemon_dynamics`, the Maven coordinates are `codes.honor:cobblemon-dynamics`, and the root package is `codes.honor.cobblemondynamics`. | none |
| R49. Ruling (was A15): the player guide is `docs/player-guide.md`, and the manual checklist is `docs/testing/manual-checklist.md`. | none |
| R50. Ruling (was A16): the Work Mode payload carries the requested state, not a flip. | none |
| R51. Ruling (was A17): a pending source expires 600 ticks (30 s) after its click, or when the player right-clicks air. | none |
| R52. Ruling (was A18): Telekinesis and the Magnet move their items in one batch every 20 ticks. | none |
| R53. Ruling (replaces A19): the mod logo is the approver's own image, `art/logo/logo-source-1254.png` (1254 × 1254 pixels). It ships as a 256 × 256 `logo.png` with `logoBlur=false`, and as a 512 × 512 project-page icon. | none |
| R54. Ruling (replaces A20): only the Telekinesis source must sit inside the work area. The destination may sit anywhere within 32 blocks of the pasture block on each axis, and it is never claimed. | none |
| R55. Ruling (was A21): when more than one role fits a clicked target, the Work Leash picks the role with the higher priority, then the role id that sorts first. | none |
| R56. Ruling (was A22): Phase 1 builds use tags of the form `vX.Y.Z-alpha.N`, Phase 2 builds use `vX.Y.Z-beta.N`, and only a tag with no suffix publishes. | none |
| R57. Ruling (was A23): the action-bar messages read `"Select a destination for {Pokémon}"`, `"Destination must differ from the source"`, `"No room in front of the fan"`, `"{Pokémon} cannot reach {target}"`, `"Fan mode: Blasting"` and `"Fan mode: Smoking"`. | none |
| R58. Ruling: a leash binding clears when its target is broken, replaced, picked up with a Create wrench or moved into a Create contraption. It also clears when its Pokémon is recalled to the PC or the party, moves to another pasture, is released or traded, or loses its pasture block. | none |
| R59. Ruling (2026-11-10, after the Phase 1 play-test): Fire-types do not heat Blaze Burners. Dragon-types alone heat them, `KINDLED` below level 50 and `SEETHING` from level 50. The Burner Keeper role goes. | none |
| R60. Ruling (2026-11-10, after the Phase 1 play-test): a Charge Cell block, with an item, stores up to 50,000 FE behind NeoForge's energy capability on all six faces and takes FE on each. Every tick it pushes its FE into each adjacent block that takes FE, split fairly between the faces, and it never loops FE between two cells. A comparator reads its fill from 0 to 15. A broken cell keeps its FE on the item, and the tooltip shows it. A shaped recipe from copper ingots, redstone and a Cobblemon Thunder Stone makes it. | none |
| R61. Ruling (2026-11-10, after the Phase 1 play-test): the mod's items get their own creative tab, `cobblemon_dynamics:main`, titled "Cobblemon Dynamics" with the Work Leash as its icon. It holds the Work Leash, the four beds and the Charge Cell, in that order, and the items leave the vanilla tabs. | none |
| R62. Ruling (2026-11-10, after the multiplayer audit): operators get `/cobblemondynamics unbind <pos>`, at permission level 2. It clears every leash binding whose target or source is that block, through the leash resets with the reason `cleared_by_operator`, and tells each binder. | none |
| RES1. Research: Cobblemon 1.8.4 integration points. | `docs/research/2026-11-09-integration-points.md` |
| RES2. Research: Create 6.0.10 kinetic and heat hooks. | `docs/research/2026-11-09-integration-points.md` |
| RES3. Research: AE2 19.2.19, NeoForge 21.1 and vanilla furnace facts. | `docs/research/2026-11-09-integration-points.md` |
| RES4. Research: prior art in Cobbleworkers and Cobblebase. | `docs/research/2026-11-09-integration-points.md` |
| RES5. Research: Create windmill and fan hooks, item handlers, the AE2 ME Interface, the enchanting table, cauldrons and move effects. | `docs/research/2026-11-09-integration-points.md` |
| REV1. Review: the consistency review of the design docs, with 30 findings. | none |
| ADR 0001. Pasture Work Mode instead of a Work Stand. | `docs/adr/0001-pasture-work-mode-instead-of-work-stand.md` |
| ADR 0002. NeoForge 1.21.1 only. | `docs/adr/0002-neoforge-1.21.1-only.md` |
| ADR 0003. Standalone mod, not a Cobbleworkers addon. | `docs/adr/0003-standalone-mod-not-cobbleworkers-addon.md` |
| ADR 0004. Brain activity for worker control. | `docs/adr/0004-brain-activity-for-worker-control.md` |
| ADR 0005. Global claim registry. | `docs/adr/0005-global-claim-registry.md` |
| ADR 0006. Data-driven roles. | `docs/adr/0006-data-driven-roles.md` |
| ADR 0007. Effects through Cobblemon packets. | `docs/adr/0007-effects-through-cobblemon-packets.md` |
| ADR 0008. Work Stamina model. | `docs/adr/0008-work-stamina-model.md` |
| ADR 0009. Coexistence with pasture job mods. | `docs/adr/0009-coexistence-with-pasture-job-mods.md` |
| ADR 0010. Balance rule. | `docs/adr/0010-balance-rule.md` |
| ADR 0011. v2 roles in the first release. | `docs/adr/0011-v2-roles-in-first-release.md` |
| ADR 0012. Original art assets. | `docs/adr/0012-original-art-assets.md` |
| BM1. Benchmark: Cobbleworkers 2.0.7. | https://modrinth.com/mod/cobbleworkers |
| BM2. Benchmark: Cobblebase 2.0.1. | https://modrinth.com/mod/cobblebase |
| BM3. Benchmark: a small 6 × 6 × 6 Extreme Reactors reactor gives about 15,000 FE/t at most, by the approver's figure. | none |

## Assumptions

none: the approver ruled on every assumption on 2026-11-09. Inputs R35 to R57 hold those rulings.

## Role table

The rows below cite this table. It names each role's phase, types, targets, effect, auto-assign flag
and default priority. The default role files hold these values.

| Role | Phase | Types | Targets | Effect | Auto-assign | Priority |
| --- | --- | --- | --- | --- | --- | --- |
| Wheel Driver | 1 | Water | Create water wheel, large water wheel | Water Gun | yes | 0 |
| Generator | 1 | Electric | Blocks whose NeoForge energy capability can receive | Thunder Shock | yes | 0 |
| Stoker | 1 | Fire | Block entities that extend `AbstractFurnaceBlockEntity` | Ember | yes | 0 |
| Superheater | 1 | Dragon | Filled Create Blaze Burner | Dragon Breath | yes | 10 |
| Cranker | 1 | Fighting, Normal | Create hand crank | Pound | yes | 0 |
| Crystal Tender | 1 | Rock | Blocks in `#c:budding_blocks` | Rock Throw | yes | 0 |
| Brew Stoker | 1 | Fire | Brewing stand | Ember | yes | 10 |
| Brewer | 1 | Poison | Brewing stand | Poison Gas | yes | 0 |
| Pump | 1 | Water | Blocks whose NeoForge fluid capability accepts water, and cauldrons | Water Gun | no, leash only | 0 |
| Muse | 1 | Fairy | Enchanting table | Fairy Wind | no, leash only | 0 |
| healer | 1 | Fairy | Allies in the same pasture | Floral Healing if Cobblemon has it, otherwise the `special` pose with heal particles | not a machine role | none |
| Telekinesis | 2 | Psychic | A source and a destination that expose NeoForge's item handler capability | Kinesis | no, leash only | 0 |
| Magnet | 2 | Steel | A container that exposes NeoForge's item handler capability | `special` pose with `cobblemon:metal_sparks` | no, leash only | 0 |
| Fan Catalyst | 2 | Fire, Water, Ghost | Create encased fan | Fire Spin (Fire), Bubble (Water), Will-O-Wisp (Ghost) | no, leash only | 0 |
| Tailwind | 2 | Flying | Create windmill bearing | Gust | no, leash only | 0 |

## Recipes

| Item | Recipe |
| --- | --- |
| Work Leash | Shapeless: 1 Lead and 1 Poké Ball (`cobblemon:poke_ball`). |
| Hay Bed | Shaped: 3 hay bales in a row over 3 planks of any wood. |
| Wool Bed | Shaped: a Hay Bed in the center, 4 wool blocks (`#minecraft:wool`) on the edges, 4 iron ingots in the corners. |
| Plush Bed | Shaped: a Wool Bed in the center, 4 gold ingots on the edges, 4 diamonds in the corners. |
| Starlit Bed | Shapeless: 1 Plush Bed, 1 Nether Star and 4 Rare Candy (`cobblemon:rare_candy`). |
| Charge Cell | Shaped: a Thunder Stone (`cobblemon:thunder_stone`) in the center, redstone dust above and below it, and 6 copper ingots in the side columns. |

## Milestones

| Milestone | Phase | Scope |
| --- | --- | --- |
| M0 | 1 | Scaffold and CI |
| M1 | 1 | Work engine, claims, Work Leash, Work Mode and the Stoker role |
| M2 | 1 | Work Stamina, beds and healers |
| M3 | 1 | Create roles: Wheel Driver, Superheater and Cranker |
| M4 | 1 | Generator, Crystal Tender, Brew Stoker, Brewer, Muse and Pump |
| M5 | 1 | UI bars, compatibility and polish. Ends at the Phase 1 gate. |
| M6 | 2 | Telekinesis and Magnet |
| M7 | 2 | Fan Catalyst and Tailwind |
| M8 | 2 | Final art and the approver's art review. Ends at the Phase 2 gate. |
| M9 | 3 | Public release on Modrinth and CurseForge, and the approver's pack |

## Requirements

### PLAT: platform, dependencies and versions

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-PLAT-01 | The mod MUST declare NeoForge `[21.1.219,)` as a required dependency. | MUST | R7, RES2, RES3, ADR 0002 | Create 6.0.10 needs NeoForge 21.1.219, and attachment sync needs 21.1.197. | Demo: NeoForge 21.1.219 loads the mod, and 21.1.218 shows the dependency error screen. Fails if: 21.1.219 fails to load, or 21.1.218 loads. | build | agreed |
| CD-PLAT-02 | The mod MUST declare Cobblemon `[1.8.4,1.9)` as a required dependency. | MUST | R24, RES1 | Pasture and Brain internals change between Cobblemon minor versions. | Inspect: `neoforge.mods.toml` declares `[1.8.4,1.9)`. Demo: Cobblemon 1.8.4 loads the mod, and 1.8.3 shows the dependency error screen. Fails if: the range differs, 1.8.4 fails, or 1.8.3 loads. | build | agreed |
| CD-PLAT-03 | The mod MUST declare KotlinForForge `[5.10,)` as its language loader range. | MUST | R24, RES1, RES3 | Cobblemon 1.8.4 needs 5.10, and a higher floor locks out packs that Cobblemon supports. | Inspect: `neoforge.mods.toml` has `modLoader = "kotlinforforge"` and `loaderVersion = "[5.10,)"`. Demo: KotlinForForge 5.10.0 loads the mod. Fails if: either value differs, or 5.10.0 fails. | build | agreed |
| CD-PLAT-04 | The build MUST compile every class to Java 21 bytecode. | MUST | R24, RES1 | Cobblemon runs only on Java 21. | Inspect: `javap -v` on each class in the jar reports major version 65. Fails if: a class reports another version. | build | agreed |
| CD-PLAT-05 | The mod MUST declare Create `[6.0.10,6.1)` and AE2 `[19.2,20)` as optional dependencies. | MUST | R7, R9, RES2, RES3, ADR 0002 | Packs without Create or AE2 still get the vanilla and FE roles. | Inspect: both entries have `type = "optional"` and these ranges. Demo: a pack with Create 6.0.9 shows the dependency error screen, a pack with AE2 19.1.x shows it too, and a pack with neither mod loads. Fails if: either mod is required, a range differs, 6.0.9 loads, or AE2 19.1.x loads. | build | agreed |
| CD-PLAT-06 | The mod MUST require an install on both the client and the server. | MUST | R22, R24 | The mod adds items, blocks and screens that a client without the mod cannot show. | Demo: a client without the mod joins a server with it and gets NeoForge's missing-mod message. Fails if: the client joins. | build | agreed |
| CD-PLAT-07 | The mod MUST use the mod id `cobblemon_dynamics` and the Maven coordinates `codes.honor:cobblemon-dynamics`. | MUST | R48, R9 | Datapacks, configs and other mods address the mod by these names. | Inspect: `neoforge.mods.toml` and the published POM. Fails if: either name differs. | build | agreed |
| CD-PLAT-08 | The mod MUST NOT declare Cobbleworkers or Cobblebase as a required dependency. | MUST NOT | ADR 0003, R20, RES4 | Dynamics is standalone, and both mods are optional neighbors. | Inspect: `neoforge.mods.toml` lists neither mod as required. Fails if: either appears as required. | build | agreed |

### WMD: Work Mode

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-WMD-01 | When a player places a pasture, the pasture adapter MUST set Work Mode to off. | MUST | R1, ADR 0001 | Pastures keep Cobblemon's behaviour until someone opts in. | GameTest: place a pasture and read its Work Mode attachment. Fails if: Work Mode reads on. | pasture adapter | agreed |
| CD-WMD-02 | The pasture adapter MUST save Work Mode with Cobblemon's pasture block entity. | MUST | R1, RES1, RES3 | A restart must not stop a base. | GameTest: set Work Mode on, then unload and reload the chunk. Fails if: Work Mode reads off after the reload. | pasture adapter | agreed |
| CD-WMD-03 | When the pasture's placer sends a Work Mode request, the server MUST set Work Mode to the requested state. | MUST | R1, R24, ADR 0001 | The placer controls the pasture. | GameTest: the placer requests on, then off. Fails if: Work Mode does not follow each request within 1 tick. | pasture adapter | agreed |
| CD-WMD-04 | When an operator sends a Work Mode request for any pasture, the server MUST set Work Mode to the requested state. | MUST | R24, R39 | Operators fix abandoned or misused pastures. | GameTest: a permission-level-2 player who is not the placer requests on. Fails if: Work Mode stays off. | pasture adapter | agreed |
| CD-WMD-05 | When Work Mode turns off, the claim registry MUST release every work, bed and heal claim of that pasture's workers within 1 tick. | MUST | R1, R24, REV1, state × event sweep | Workers of a pasture that is off must free machines, beds and allies for others. Leash bindings stay, as CD-CLM-06 says. | GameTest: two Working workers hold work claims, a sleeper holds a bed claim, and a healer holds a heal claim. Turn Work Mode off and read the registry 1 tick later. Fails if: any of the four claims remains. | claim registry | agreed |
| CD-WMD-06 | While Work Mode is off, the work engine MUST NOT give any claim to a Pokémon of that pasture. | MUST NOT | R1 | Off means Cobblemon's normal behaviour. | GameTest: Work Mode off, and a Fire-type beside a furnace with input for 400 ticks. Fails if: the Fire-type gets a claim. | work engine | agreed |
| CD-WMD-07 | When a pasture block is broken, the work engine MUST clear the leash bindings of every Pokémon that was tethered to it. | MUST | R24, entity lifecycle sweep | Cobblemon sends those Pokémon back to the PC, so their bindings point nowhere. | GameTest: bind two workers, then break their pasture. Fails if: either binding remains 20 ticks later. | work engine | agreed |

### ASG: assignment and worker states

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-ASG-01 | The work engine MUST give claims only to owned, unfainted Pokémon that are tethered to a pasture in Work Mode. | MUST | R2, G4, actor × action sweep | Wild, party and fainted Pokémon never work. | GameTest: a wild Water-type, a party Water-type that a player sent out, a fainted pastured Water-type and a healthy pastured Water-type stand 3 blocks from a dry water wheel for 400 ticks. Fails if: any Pokémon except the healthy pastured one gets a claim. | work engine | agreed |
| CD-ASG-02 | The role registry MUST count a Pokémon as eligible for a role when its primary or secondary type is one of the role's types. | MUST | R15, R24 | Dual types fill either role, for example a Water/Flying Gyarados drives wheels. | Unit: Gyarados (Water/Flying) is eligible for the Wheel Driver, and Pikachu (Electric) is not. Fails if: the secondary type is ignored, or an unlisted type qualifies. | role registry | agreed |
| CD-ASG-03 | When the work engine scans a pasture in Work Mode, it MUST give each Idle eligible worker the nearest unclaimed target of an auto-assign role that the worker can do. | MUST | R1, R24, G1 | Workers find work with no player input. | GameTest: furnaces with input at 3 and 6 blocks from one Idle Fire-type. Fails if: the worker claims the 6-block furnace, or no furnace. | work engine | agreed |
| CD-ASG-04 | The work engine MUST NOT auto-assign a Pokémon with a Fairy type to a machine. | MUST NOT | R18, R24 | Fairy-types heal unless a player leashes them. | GameTest: an unleashed Azumarill (Water/Fairy) stands 3 blocks from a dry water wheel for 400 ticks. Fails if: it claims the wheel. | work engine | agreed |
| CD-ASG-05 | The work engine MUST NOT auto-assign a worker to a role whose auto-assign flag is false. | MUST NOT | R18, R19, R29 | Pumps, Muses and the four Phase 2 roles act only where a player binds them. | GameTest: an unleashed Water-type stands beside an empty fluid tank for 400 ticks. Fails if: it claims the tank. | work engine | agreed |
| CD-ASG-06 | With the default config, the work engine MUST start each new scan pass of a pasture in Work Mode at the first 40-tick boundary, counted from the previous pass's start, after that pass completes. | MUST | R24, REV1, limits sweep | Scans find new machines without a cost on every tick, and a pass that overruns 40 ticks must finish before the next one starts. | Unit: on a fake clock with the default config, passes start at ticks 0, 40 and 80, and none starts at tick 39. A pass that needs 108 ticks (radius 32 × 6 with a Rock-type worker) is followed by a pass at tick 120. Fails if: a pass starts before the previous pass completes, or later than the first 40-tick boundary after it. | work engine | agreed |
| CD-ASG-07 | With the default config, the work engine MUST check at most 512 block positions per pasture per tick during a scan. | MUST | R24, limits sweep, quality sweep | A large work area must not cause a lag spike. | Unit: with a Rock-type worker in the pasture, a scan of the default 17 × 13 × 17 area (3,757 positions) runs over 8 ticks, and no tick checks a 513th position. Fails if: a tick checks more than 512 positions. | work engine | agreed |
| CD-ASG-08 | The work engine MUST limit the work area to block offsets of at most 8 horizontally and 6 vertically from the pasture block, with the default config. | MUST | R24, limits sweep | A small work area keeps pasture placement strategic. | GameTest: furnaces with input at horizontal offsets 8 and 9, and at vertical offsets 6 and 7. Fails if: a worker claims the furnace at offset 9 or 7, or never claims the one at offset 8 or 6. | work engine | agreed |
| CD-ASG-09 | When a worker gets a claim, the work engine MUST pick a standable work spot inside the work area that has line of sight to the target center and does not stand on the target, preferring a spot beside the target at its base height, then another spot at that height, then a spot at another height, and taking a spot that a worker cannot walk into on the level, such as a pit, only when no other spot exists. | MUST | R24, RES1 | A worker that cannot see its target cannot aim its move at it. A worker beside its target, on the floor that the target stands on, looks like it works the target, and a worker on top of the target does not (approver's play-test, 2026-11-10). | Unit: in a test grid with a wall between one candidate spot and the target, the chosen spot has a clear line to the target center. A spot beside the target wins over spots nearer to the worker, also beside the footprint of a multi-block target, a spot on the target is never chosen, and a pit beside the target comes after every open spot. GameTest: a Charmander gets a spot beside a furnace on the floor, at floor height, and a Stoker works its furnace from the one spot beside it. Fails if: the chosen spot is blocked, outside the work area, not standable or on the target, or another spot is chosen while a free, open spot beside the target at its base height has room for the worker, or a closed spot is chosen while an open spot exists. | work engine | agreed |
| CD-ASG-10 | With the default role files, the work engine MUST pick work spots within 6 blocks of the target center for ranged roles and within 2 blocks for the Cranker. | MUST | R24, limits sweep | The range sets how far a move reaches. | Unit: a spot at 6.0 blocks is valid for the Stoker and one at 6.1 is not. A spot at 2.0 blocks is valid for the Cranker and one at 2.1 is not. Fails if: any of the four results is wrong. | work engine | agreed |
| CD-ASG-11 | When a worker reaches its work spot while the target has demand, the work engine MUST put the worker in Working. | MUST | R24, state × event sweep | Work starts on arrival. | GameTest: a Fire-type reaches a furnace with input. Fails if: it is not Working within 1 tick of arrival. | work engine | agreed |
| CD-ASG-12 | While a worker is at its work spot and the target has no demand, the work engine MUST keep the worker in Standby with its claim. | MUST | R24, R45 | The worker stays ready and keeps its machine. | GameTest: a furnace runs out of input, then gets new input. Fails if: the Stoker releases its claim, or it is not Working within 20 ticks of the new input. | work engine | agreed |
| CD-ASG-13 | With the default config, if an auto-assigned worker has not reached its work spot 600 ticks (30 s) after its work claim, then the claim registry MUST release that claim. | MUST | R24, REV1, failure sweep, limits sweep | An unreachable target must not hold a worker forever. | GameTest: a target walled off from every spot. Fails if: the claim is not held at tick 599 after the claim, or is still held at tick 601. | claim registry | agreed |
| CD-ASG-14 | With the default config, when an auto-assigned worker's travel times out, the work engine MUST NOT give that worker the same target for 6,000 ticks (5 min). | MUST NOT | R24, REV1, limits sweep | Repeated timeouts on one target waste every scan. | Unit: on a fake clock, the target is refused to that worker 5,999 ticks after the timeout and allowed at 6,000 ticks. Fails if: either result is wrong. | work engine | agreed |
| CD-ASG-15 | When a worker leaves its pasture, the claim registry MUST release the worker's claim within 20 ticks. | MUST | R24, RES1, failure sweep | Cobblemon fires no unpasture event, so the pasture adapter polls every 20 ticks. | GameTest: move a Working worker to the PC and read the registry 20 ticks later. Fails if: the claim remains. | claim registry | agreed |
| CD-ASG-16 | When a worker qualifies for two roles on one target, the work engine MUST give it the role whose role file has the higher `priority`. | MUST | R24, R42, REV1 | One worker per target means one role per target, and the stronger role wins. | Unit: with the default role files, a Poison/Fire Salazzle on a brewing stand gets the Brew Stoker role (priority 10 over 0), and a Fire/Dragon Turtonator on a Blaze Burner gets the Superheater role, the burner's only role. Fails if: either gets another role. | role registry | agreed |
| CD-ASG-17 | With the default config, if a leashed worker has not reached its work spot 600 ticks after its work claim, then the work engine MUST keep its leash binding and hold it in Standby for 6,000 ticks before it travels again. | MUST | R24, REV1, failure sweep, limits sweep | A leashed machine stays reserved for its own worker, and the worker tries again later. | Unit: on a fake clock, the binding stays, the worker is in Standby from tick 600 to tick 6,599 after the claim, and it is Traveling again at tick 6,600. Fails if: the binding clears, or the retry starts at another tick. | work engine | agreed |
| CD-ASG-18 | The work engine MUST keep each worker in exactly one of the states Idle, Traveling, Working, Standby, ToBed, Sleeping, RestingInPlace and Healing. | MUST | REV1, state × event sweep | One state list drives the icons, the claims and the stamina rules. | Unit: run a scripted day of 24,000 ticks for a worker and a healer, and record the state set on every tick. Fails if: a tick has no state, two states, or a state outside the list. | work engine | agreed |
| CD-ASG-19 | With the default config, when a scan finds an unclaimed target with demand for one of an auto-assigned worker's roles in its work area, and that worker has been in Standby for 200 ticks or more, the work engine MUST move the worker's work claim to that target. | MUST | R43, R44, limits sweep, state × event sweep | A worker must not wait at a machine with no work while another machine needs it, for example at a wheel that real water already turns. | Unit: on a fake clock, a Stoker in Standby at an empty furnace sees a second furnace with input. A scan at Standby tick 199 leaves its claim, and a scan at tick 200 moves it to the second furnace. GameTest: a Wheel Driver that has waited 400 ticks at a wheel that real water turns moves to a dry wheel in its work area within 80 ticks of the dry wheel's placement. Fails if: the claim moves at tick 199, it stays at tick 200, or the Wheel Driver does not move. | work engine | agreed |
| CD-ASG-20 | The work engine MUST NOT move a leashed worker off its bound target. | MUST NOT | R44, state × event sweep | A leash is the player's exact assignment. | GameTest: a leashed Stoker waits in Standby at an empty furnace for 400 ticks, while an unclaimed furnace with input sits 3 blocks away. Fails if: the leashed worker moves, claims the second furnace, or loses its binding. | work engine | agreed |

### CLM: claims

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-CLM-01 | The claim registry MUST hold at most one claim for each target. | MUST | R2, G4, ADR 0005 | Two workers on one machine break the one-worker rule. | Unit: workers from two pastures with different owners claim one furnace in the same tick. Fails if: both hold a claim. | claim registry | agreed |
| CD-CLM-02 | The claim registry MUST hold at most one claim (a work, bed or heal claim) for each worker. | MUST | R2, R24, REV1, ADR 0005 | One worker does one job at a time. | Unit: a worker with a work claim asks for a second target, and a sleeper with a bed claim asks for a heal claim. Fails if: the registry grants either second claim. | claim registry | agreed |
| CD-CLM-03 | The claim registry MUST key block targets by dimension and block position. | MUST | RES4, BM1, failure sweep | Cobbleworkers keys claims without the dimension, so equal coordinates in two dimensions collide. | Unit: claims on 0 64 0 in the overworld and on 0 64 0 in the Nether. Fails if: the registry refuses the second claim. | claim registry | agreed |
| CD-CLM-04 | The claim registry MUST store a multiblock target under its canonical target. | MUST | R2, RES2, ADR 0005 | A large water wheel has nine blocks but is one machine. | GameTest: worker A claims a large wheel through one ring block, and worker B tries another ring block of the same wheel. Fails if: worker B gets a claim. | claim registry | agreed |
| CD-CLM-05 | When a player makes a leash binding on a target that an auto-assigned worker holds, the claim registry MUST release that worker's work claim within 1 tick. | MUST | R3, R24, G5, REV1 | The leash is the player's override. | GameTest: worker A holds a work claim on a furnace, and the player binds worker B to it. Fails if: worker A still holds the claim 1 tick later, or worker B holds no work claim on the furnace 20 ticks later. | claim registry | agreed |
| CD-CLM-06 | The claim registry MUST NOT give a work claim on a target that a leash binding reserves to any worker except the bound one. | MUST NOT | R3, R24, REV1 | A leashed machine waits for its own worker, also while that worker sleeps or its pasture's Work Mode is off. | GameTest: the bound worker sleeps, and an Idle worker of the same type stands 3 blocks from the target for 400 ticks. Then turn the bound worker's pasture to Work Mode off, and repeat with a worker from a second pasture. Fails if: either other worker claims the target. | claim registry | agreed |
| CD-CLM-07 | The claim registry MUST NOT save claims to disk. | MUST NOT | R24, REV1, failure sweep, ADR 0005 | A restart must not leave stale claims. Leash bindings are saved apart from the registry. | GameTest: save the world with two work claims, one bed claim and one heal claim, restart, and read the registry before the first scan. Fails if: the registry holds a claim. | claim registry | agreed |
| CD-CLM-08 | When a target is broken, replaced or unloaded, the claim registry MUST release the claim on it within 1 tick. | MUST | R24, REV1, failure sweep | A claim on a missing block confuses the worker. Leash bindings follow the LSH rows. | GameTest: break one claimed furnace, replace a second with stone, and unload the chunk of a third. Fails if: any of the three claims remains 1 tick later. | claim registry | agreed |
| CD-CLM-09 | The claim registry MUST NOT count a leash binding as a claim. | MUST NOT | REV1, ADR 0005 | A Tired leashed worker keeps its binding and still needs a bed claim. | Unit: a leashed worker whose binding reserves a furnace becomes Tired and asks for a bed claim. Fails if: the registry refuses the bed claim. | claim registry | agreed |

### LSH: Work Leash

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-LSH-01 | When a player holding the Work Leash right-clicks a Pokémon that the player owns and that is tethered to a pasture in Work Mode, the Work Leash MUST select that Pokémon. | MUST | R3, G5 | Selection is the first step of a binding. | GameTest: the owner right-clicks a pastured Vaporeon with the leash. Fails if: the leash stack does not hold Vaporeon's UUID. | content | agreed |
| CD-LSH-02 | When the Work Leash acts on a Pokémon, the server MUST cancel the interaction before Cobblemon handles it. | MUST | R3, RES1, RES3 | Otherwise Cobblemon opens its interact wheel or puts the Pokémon on a shoulder. | Demo: a plain right-click and a sneak right-click with the leash on an owned pastured Pokémon. Fails if: either click opens Cobblemon's wheel or mounts the Pokémon. | content | agreed |
| CD-LSH-03 | When a player right-clicks a block with a Work Leash that holds a selection, and the role that fits the block needs one target, the work engine MUST bind the selected Pokémon to that block if every bind rule passes. | MUST | R3, G5 | The binding is the player's exact assignment. | GameTest: select a pastured Vaporeon, then right-click a water wheel in its work area. Fails if: no binding exists 1 tick later. | work engine | agreed |
| CD-LSH-04 | When the Work Leash binds a block, the server MUST stop that block from opening its own screen. | MUST | R3, RES3 | A furnace screen that opens on every bind gets in the way. | Demo: bind a Fire-type to a furnace. Fails if: the furnace screen opens. | content | agreed |
| CD-LSH-05 | If a bind breaks a rule, then the work engine MUST refuse it and show the rule's message: `"Outside its pasture's work area"`, `"No role fits"`, `"Already assigned to {Pokémon}"`, which names another player's Pokémon by its species as `"Already assigned to another player's {species}"`, or `"Not in a Work Mode pasture"`. | MUST | R3, R24, misuse sweep, limits sweep | Each refusal tells the player what to fix. A Pokémon's nickname is its owner's to show, so another player's Pokémon is named by its species (approver's multiplayer-audit ruling, 2026-11-10). | GameTest: a target at horizontal offset 9 (offset 8 binds), a stone block, a target bound to Pikachu, and a selection whose Pokémon left the pasture. Unit: a target bound to another player's Pikachu with the nickname Sparky. Fails if: a case binds, shows the wrong message, or the message for another player's Pikachu names Sparky. | work engine | agreed |
| CD-LSH-06 | When a bind succeeds, the client MUST show `"Bound {Pokémon} to {target}"` in the action bar. | MUST | R24 | The player sees which machine the worker now owns. | Demo: bind Vaporeon to a water wheel. Fails if: the action bar does not read "Bound Vaporeon to Water Wheel". | client | agreed |
| CD-LSH-07 | The work engine MUST save each leash binding in the bound Pokémon's `persistentData`. | MUST | R3, R24, RES1 | Bindings survive restarts, Cobblemon's PC sync and Work Mode toggles. | GameTest: bind a worker, restart the server, then turn Work Mode off and on. Fails if: the binding is missing after either step. | work engine | agreed |
| CD-LSH-08 | When a bound Pokémon is recalled to the PC or the party, or its target is broken or replaced, the work engine MUST clear the binding. | MUST | R24, R58, entity lifecycle sweep | A binding to a missing Pokémon or block reserves nothing real. | GameTest: one bound Pokémon goes to the PC, a second goes to the party, a third one's target is broken, and a fourth one's target is replaced with stone. Fails if: a binding remains 20 ticks later. | work engine | agreed |
| CD-LSH-09 | When a bound target unloads, the work engine MUST keep the binding. | MUST | R24, failure sweep | The worker resumes when the chunk loads again. | GameTest: unload the target's chunk and reload it 100 ticks later. Fails if: the binding is gone, or the worker is not Working within 600 ticks of the reload. | work engine | agreed |
| CD-LSH-10 | When a player sneak-right-clicks a bound Pokémon that the player owns with the Work Leash, the work engine MUST clear its binding. | MUST | R3 | The player can undo a binding. | GameTest: sneak-right-click a bound Vaporeon with the leash. Fails if: the binding remains 1 tick later. | work engine | agreed |
| CD-LSH-11 | When a player binds an already bound Pokémon to a new target, the work engine MUST replace the old binding. | MUST | R3, entity lifecycle sweep | A Pokémon holds one binding, so a new one replaces the old one. | GameTest: bind Vaporeon to wheel A, then to wheel B. Fails if: wheel A stays reserved, or Vaporeon is not bound to wheel B. | work engine | agreed |
| CD-LSH-12 | While a bound Pokémon is Tired, the work engine MUST NOT start work on a new binding. | MUST NOT | R10, state × event sweep | Binding a sleeping worker must not cut its rest short. | GameTest: bind a Tired Pokémon to a furnace. Fails if: it works before it reaches 100%, or the binding is lost. | work engine | agreed |
| CD-LSH-13 | When the work engine clears a binding for any reason except the owner's own leash action, the client MUST show the owner a chat message that names the Pokémon and the reason. | MUST | R24 | A silent loss of a binding looks like a bug. | Demo: evolve one bound Pokémon into a form that no role fits, and break another one's target. Fails if: an owner gets no message, or a message lacks the name or the reason. | client | agreed |
| CD-LSH-14 | When a bound Pokémon evolves into a form that no role on its target fits, the work engine MUST clear the binding. | MUST | R24 | The new form cannot do the job. | GameTest: bind a Water-type to a wheel, then change its form to one with no Water type. Fails if: the binding remains 20 ticks later. | work engine | agreed |
| CD-LSH-15 | Dynamics MUST register the Work Leash recipe from the Recipes table. | MUST | R24 | Players craft the leash in survival. | GameTest: craft a Lead and a Poké Ball in any two grid slots. Fails if: the result is not one Work Leash. | content | agreed |
| CD-LSH-16 | When more than one role fits a clicked target, the work engine MUST pick the role with the higher priority, then the role id that sorts first. | MUST | R55 | One click must give one predictable role. | Unit: a Water/Psychic Slowbro clicks a Create basin, which exposes both a fluid and an item handler. Both roles have priority 0, so the Pump wins because `pump` sorts before `telekinesis`. Fails if: the Telekinesis role is picked. | work engine | agreed |
| CD-LSH-17 | When a player right-clicks a block with a Work Leash whose selected Pokémon gets the Telekinesis role on that block, the Work Leash MUST store that block and the clicked face as the pending source. | MUST | R27, R33 | Telekinesis needs a source and a destination, so the first block click waits for the second. | GameTest: select a pastured Alakazam, then right-click the top face of a chest. Fails if: the leash does not hold the chest's position and the face `up`, or a binding exists already. | content | agreed |
| CD-LSH-18 | When a player right-clicks a second block while a pending source exists, the work engine MUST bind the selected Pokémon to the pending source and to that block as the destination, with both clicked faces, if every bind rule passes. | MUST | R27, R33 | The second click completes the Telekinesis binding. | GameTest: after CD-LSH-17, right-click the side of a barrel. Fails if: no binding with the chest as source, the barrel as destination and both faces exists 1 tick later. | work engine | agreed |
| CD-LSH-19 | If 600 ticks pass after the source click, or the player right-clicks air, while a pending source exists, then the Work Leash MUST clear the pending source. | MUST | R51, limits sweep | A stale pending source must not catch a later click by surprise. | Unit: the pending source still exists 599 ticks after its click and is gone at 600 ticks. A right-click on air clears a new pending source at once. Fails if: any of the three results is wrong. | content | agreed |
| CD-LSH-20 | When the Work Leash stores a pending source, the client MUST show `"Select a destination for {Pokémon}"` in the action bar. | MUST | R57, R33 | The player knows that the leash waits for a second click. | Demo: click a chest with a selected Alakazam. Fails if: the action bar does not read "Select a destination for Alakazam". | client | agreed |
| CD-LSH-21 | When a leashed worker misses its arrival time, the client MUST show its owner `"{Pokémon} cannot reach {target}"` in the action bar. | MUST | R57, REV1 | The owner learns why a leashed machine waits. | Demo: wall off a bound furnace from every spot. Fails if: the owner gets no message within 601 ticks of the claim, or the message lacks the name or the target. | client | agreed |
| CD-LSH-22 | Where Create is present, when a player picks up a bound target with a Create wrench, the work engine MUST clear the binding. | MUST | R58, entity lifecycle sweep | A picked-up block is no longer in the world, so the binding reserves nothing. | GameTest: sneak-use a Create wrench on a bound hand crank. Fails if: the binding remains 20 ticks later. | work engine | agreed |
| CD-LSH-23 | Where Create is present, when a contraption takes in a bound target, the work engine MUST clear the binding. | MUST | R58, entity lifecycle sweep | A block inside a contraption has left its place in its pasture's work area. | GameTest: a mechanical piston assembles a contraption that holds a bound furnace. Fails if: the binding remains 20 ticks after the assembly. | work engine | agreed |
| CD-LSH-24 | When a bound Pokémon moves to another pasture, the work engine MUST clear the binding. | MUST | R58, entity lifecycle sweep | The old target sits in the old pasture's work area. | GameTest: move a bound Pokémon from pasture A to pasture B. Fails if: the binding remains 20 ticks after the move, or its old target stays reserved. | work engine | agreed |
| CD-LSH-25 | When a bound Pokémon is released, the work engine MUST clear the binding. | MUST | R58, entity lifecycle sweep | A released Pokémon leaves its owner for good. | GameTest: release a bound Pokémon through Cobblemon's release action. Fails if: its old target stays reserved 20 ticks later. | work engine | agreed |
| CD-LSH-26 | When a Pokémon that carries binding data reaches a new owner through a trade, the work engine MUST clear that data. | MUST | R58, misuse sweep, entity lifecycle sweep | Binding data in `persistentData` travels with the Pokémon, and a stale binding must not reserve the old owner's machine. | Unit: owner B gains a Pokémon whose `persistentData` holds a binding that owner A made. Fails if: the binding data remains after the gain event. | work engine | agreed |
| CD-LSH-27 | When a pasture block is broken, the work engine MUST clear the bindings of every Pokémon that was tethered to it. | MUST | R58, entity lifecycle sweep | Cobblemon returns those Pokémon to their owners, so no binding keeps a pasture. | GameTest: break a pasture with three bound Pokémon. Fails if: a binding remains 20 ticks later, or a target stays reserved. | work engine | agreed |

### AI: Brain bridge

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-AI-01 | The brain bridge MUST add the `cobblemon_dynamics:work` activity through a behaviour file in Cobblemon's `behaviours/pokemon/auto/` folder. | MUST | R23, RES1, ADR 0004 | Cobblemon builds every Pokémon brain from these files. | Inspect: the jar holds the behaviour file. GameTest: the brain of a newly pastured Pokémon lists the activity. Fails if: the file is missing, or the brain lacks the activity. | brain bridge | agreed |
| CD-AI-02 | While a worker holds a work order, the brain bridge MUST run the work activity in place of Cobblemon's idle activities. | MUST | R23, ADR 0004 | The idle activities hold wandering, which would pull the worker off its spot. | GameTest: a Working worker stays at its work spot for 600 ticks. Fails if: it moves more than 0.5 blocks from the spot. | brain bridge | agreed |
| CD-AI-03 | When a worker's work order clears, the brain bridge MUST restore Cobblemon's own activities within 20 ticks. | MUST | R23, state × event sweep | Released workers go back to normal pasture life. | GameTest: release the claim of a Working worker and read its active activities 20 ticks later. Fails if: the work activity is still active. | brain bridge | agreed |
| CD-AI-04 | While a worker holds a claim, the work engine MUST write the worker's work order into its brain memory every tick. | MUST | R23, RES1, failure sweep | Cobblemon rebuilds brains when it swaps the entity's Pokémon instance, and that drops the memory. | GameTest: call `remakeBrain()` on a Working worker. Fails if: the work activity is not active again 1 tick later. | work engine | agreed |
| CD-AI-05 | While a worker is Working or in Standby, the brain bridge MUST keep the worker facing the target center. | MUST | R4, R23 | The move aims at the machine, as the approver described. | GameTest: track the worker's body yaw for 600 ticks. Fails if: it differs by more than 15 degrees from the direction to the target center. | brain bridge | agreed |
| CD-AI-06 | The brain bridge MUST NOT change the activities of a Pokémon that holds no work order. | MUST NOT | R2, G4, actor × action sweep | Wild Pokémon and Pokémon in pastures that are off keep Cobblemon's behaviour. | GameTest: a wild Pokémon and a Pokémon in a pasture that is off run for 1,200 ticks. Fails if: the active activities of either include the work activity. | brain bridge | agreed |
| CD-AI-07 | The brain bridge MUST NOT set Cobblemon's battle sleep status on a sleeping worker. | MUST NOT | RES1, failure sweep | That status carries into battles. | GameTest: a worker sleeps on a bed and wakes. Fails if: its status reads sleep at any point. | brain bridge | agreed |
| CD-AI-08 | When a sleeping worker wakes, the brain bridge MUST restore Cobblemon's pose recalculation. | MUST | RES1, failure sweep | Cobblemon saves that flag, so a missed restore freezes the pose for good. | GameTest: a worker wakes, then the server saves and reloads it. Fails if: pose recalculation is off after the wake or after the reload. | brain bridge | agreed |
| CD-AI-09 | The brain bridge MUST run its own look-at-target task inside the work activity. | MUST | RES1, REV1 | Cobblemon's own look task runs only in the idle activity, which the work activity replaces. | Inspect: the work activity's task list holds the look task. GameTest: with the work activity running, Cobblemon's idle look task is inactive and the worker faces its target as CD-AI-05 says. Fails if: the task is missing, or the worker stops facing the target. | brain bridge | agreed |

### FX: effects

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-FX-01 | While a worker is Working, the effects component MUST play the role's effect when the worker starts Working and again each time the worker's animation has ended and the role's `idle_ticks` (default 40) have passed, or every `loop_ticks` (default 40) ticks for a worker whose model has none of the role's animations. | MUST | R24, limits sweep | A loop shows the worker at work without a flood of packets. An animation that starts again before it ends looks glitchy, and most `special` and `physical` animations last 3 s (approver's play-test, 2026-11-10). | Unit: on a fake clock, a 60-tick animation with 40 idle ticks starts at ticks 0, 100 and 200, and none starts at tick 99. With no animation, effects start at ticks 0, 40 and 80, and none starts at tick 39. Cobblemon's files give a Charmander Stoker the `special` animation of 3 s. GameTest: the animations of a Working Charmander start 100 ticks apart. Demo: a Working Charmander plays its whole animation, then stands idle for 2 s before the next one. Fails if: an effect starts before the animation and the idle ticks have ended, or an interval differs. | effects | agreed |
| CD-FX-02 | The effects component MUST use the move effect that the Role table lists for each role. | MUST | R4, R5, R6, R24, RES1 | Each role looks like its move. | Inspect: each role file names the effect from the Role table. Fails if: a role file names another effect. | effects | agreed |
| CD-FX-03 | The effects component MUST send the first name of the role's animation list, in its listed order, that the worker's model has in Cobblemon's animation files with no sound. | MUST | R24, R46, RES1, BM2 | Most models lack attack animations. The server needs the length of the animation that plays to time the loop (CD-FX-01). Work is silent (CD-FX-09), so a model with none of the names plays no animation. | Unit: the Water Gun list is `watergun`, `spray`, `beam`, `special`, and a Squirtle gets `special`. A Bellibolt Generator gets no animation, because its `special` plays its cry. Fails if: the order differs, the list holds `cry`, or the server sends a name that the model lacks, a later name of the list, or an animation with a sound. | effects | agreed |
| CD-FX-04 | While a worker is Working, the effects component MUST aim the role's target particles at a work focus entity at the target center. | MUST | R4, RES1, ADR 0007 | Cobblemon particles aim only at entities. | Demo: Water Gun spray travels from the worker to the axle of a water wheel. Fails if: the spray points anywhere else. | effects | agreed |
| CD-FX-05 | The work focus entity MUST be invisible and have no collision box. | MUST | R24, ADR 0007 | The anchor must not change what players see or where they walk. | GameTest: a player walks through the focus position, and `isPickable` and `canBeCollidedWith` return false. Demo: nothing renders at the wheel's axle. Fails if: the player is blocked, either check returns true, or anything renders. | effects | agreed |
| CD-FX-06 | The effects component MUST NOT save work focus entities to disk. | MUST NOT | R24 | Saved anchors pile up after restarts. | GameTest: save the world with five active claims and scan the saved entities. Fails if: a `cobblemon_dynamics:work_focus` entity is saved. | effects | agreed |
| CD-FX-07 | When a worker leaves the Working state, the effects component MUST remove its work focus entity within 1 tick. | MUST | R24, REV1, entity lifecycle sweep | Only Working workers have a focus entity, and orphan anchors leak entities. | GameTest: a Working worker goes to Standby, and a second Working worker's claim is released. Fails if: either focus entity remains 1 tick later, or a Standby worker has one. | effects | agreed |
| CD-FX-08 | The effects component MUST send effect packets only to players within 64 blocks of the worker. | MUST | R24, limits sweep | Players farther away cannot see the effect. | Unit: the recipient filter keeps a player at 64.0 blocks and drops a player at 64.1 blocks. Fails if: either result is wrong. | effects | agreed |
| CD-FX-09 | The effects component MUST keep work and heal effects silent: it sends no sound, no `cry` animation and no animation or particle whose Cobblemon file plays a sound. | MUST | R21 | Many workers that loop sounds make noise. The approver's play-test ruling (2026-11-10) makes all work silent. Models with no attack animation fell back to `cry`, which played the cry on every loop. | Unit: no role file and no fixed effect names `cry`, the role loader skips a file that names `cry` or a sound, the server never picks an animation with a sound keyframe or a sound call, and no particle that the mod sends plays a sound. Demo: a Machop, an Eevee and a Vaporeon Work for 10 s and make no sound. Fails if: any of these checks fails, or a work or heal effect plays a sound. | effects | agreed |
| CD-FX-10 | While a worker is Sleeping or RestingInPlace, the effects component MUST play `cobblemon:sleep_actor` and `cobblemon:sleep_actorresidual` every 40 ticks with no sound. | MUST | R12, RES1 | The approver chose Cobblemon's sleep effect, played silently. | Demo: a worker on a bed and a worker with no bed, each for 400 ticks. Fails if: the sleep particles do not show every 2 s on either, or a sound plays. | effects | agreed |
| CD-FX-11 | If a role's particle id is missing from the loaded Cobblemon version, then the effects component MUST log one warning and play the animation without particles. | MUST | RES1, failure sweep | Cobblemon renames particle ids between versions. | Unit: a stub particle registry without `cobblemon:watergun_spray`. Fails if: an error is thrown, the animation is skipped, or the warning repeats. | effects | agreed |

### STA: Work Stamina

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-STA-01 | The stamina component MUST give each Pokémon 3,600 ticks of work capacity per level. | MUST | R10, G6, ADR 0008 | Capacity sets how long a worker works before it is Tired. | Unit: Lv1 has 3,600 ticks (3 min), Lv50 has 180,000 (150 min), and Lv100 has 360,000 (300 min). Fails if: a value differs. | stamina | agreed |
| CD-STA-02 | The stamina component MUST NOT give legendary or mythical Pokémon extra capacity. | MUST NOT | R10 | The approver ruled no rarity effect on stamina. | Unit: a Lv100 Mewtwo has 360,000 ticks. Fails if: the value differs. | stamina | agreed |
| CD-STA-03 | The stamina component MUST store Work Stamina as a percentage of capacity. | MUST | R24 | A level-up then keeps the current percentage. | Unit: a Lv30 worker at 40% reaches Lv31. Fails if: it no longer reads 40%. | stamina | agreed |
| CD-STA-04 | The stamina component MUST save Work Stamina, the Tired flag and the last-update game time in the Pokémon's `persistentData`. | MUST | R24, RES1 | Stamina follows the Pokémon through the PC, the party and restarts. | GameTest: a worker at 37% goes to the PC and back, the server restarts, and Cobblemon swaps the entity's Pokémon instance. Fails if: the value changes by more than the recovery that the elapsed time allows. | stamina | agreed |
| CD-STA-05 | When a Pokémon first enters a pasture in Work Mode, the stamina component MUST start it at 100%. | MUST | R10, entity lifecycle sweep | A new worker begins rested. | GameTest: pasture a Pokémon that has no Dynamics data. Fails if: it reads anything but 100%. | stamina | agreed |
| CD-STA-06 | The stamina component MUST drain Work Stamina only while a worker is Working, by 1 tick of capacity per tick. | MUST | R10, R24 | Waiting at an idle machine is rest, not work. | Unit: a Lv1 worker Working from 100% for 1,800 ticks reads 50%, and a Lv1 worker at 50% in Standby for 600 ticks reads 66.7%. Fails if: either value is off by more than 0.1%. | stamina | agreed |
| CD-STA-07 | Where a Pokémon follows the worker rule, the stamina component MUST recover it from 0% to 100% in 3 min × level outside the Working state. | MUST | R10, ADR 0008 | Recovery time equals drain time, as the approver ruled. | Unit: a Lv10 worker at 0% outside Working reads 50% after 18,000 ticks (15 min) and 100% after 36,000 ticks (30 min). Fails if: either value is off by more than 0.1%. | stamina | agreed |
| CD-STA-08 | When Work Stamina reaches 0%, the stamina component MUST mark the Pokémon Tired. | MUST | R10 | Tired starts the rest cycle. | Unit: a Lv1 worker with 1 tick of capacity left works 1 more tick. Fails if: it is not Tired. | stamina | agreed |
| CD-STA-09 | While a Pokémon is Tired, the work engine MUST NOT give it a claim on a machine. | MUST NOT | R10, limits sweep | The approver ruled no work until full recovery. | Unit: a Tired worker at 99.9% is refused a furnace claim, and at 100% it gets the claim. Fails if: either result is wrong. | work engine | agreed |
| CD-STA-10 | When a Tired Pokémon reaches 100%, the stamina component MUST clear its Tired flag. | MUST | R10 | The worker can work again. | Unit: a Tired worker recovers to 100%. Fails if: the flag stays set. | stamina | agreed |
| CD-STA-11 | When a Pokémon loads after time in the PC, the party or an unloaded chunk, the stamina component MUST apply recovery at its base rate for the elapsed game time. | MUST | R24, failure sweep | Rest goes on while the Pokémon is away. Bed factors never apply to time away. | GameTest: a Lv10 worker at 0% goes to the PC at game time T and comes back at T + 18,000 ticks. Fails if: it does not read 50% ± 0.1%. | stamina | agreed |
| CD-STA-12 | If an operator changes the time of day, then the stamina component MUST NOT change any Work Stamina value. | MUST NOT | failure sweep, misuse sweep | Elapsed time comes from game time, which `/time` commands do not change. | GameTest: run `/time set day` and `/time add 24000` while a worker sits in the PC. Fails if: its Work Stamina changes. | stamina | agreed |
| CD-STA-13 | When an auto-assigned worker becomes Tired, the claim registry MUST release its work claim within 1 tick. | MUST | R24 | Another worker can take over the machine. | GameTest: an auto worker becomes Tired, and a second worker of the same type waits nearby. Fails if: the first claim remains 1 tick later, or the second worker does not claim the machine at the next scan. | claim registry | agreed |
| CD-STA-14 | When a leashed worker's Tired flag clears, the work engine MUST send it back to its bound target. | MUST | R24, state × event sweep | A leashed machine resumes after the rest. | GameTest: a leashed worker wakes at 100%. Fails if: it is not Traveling to its target within 20 ticks. | work engine | agreed |
| CD-STA-15 | The stamina component MUST sync a worker's Work Stamina to clients when it changes by 1% or more, or when the worker's state changes. | MUST | R22, R24, limits sweep | Displays stay current without a packet on every tick. | Unit: a change of 0.9% sends nothing, a change of 1.0% sends one update, and a state change after a 0.1% change sends one update. Fails if: any result is wrong. | stamina | agreed |

### BED: beds

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-BED-01 | Dynamics MUST add four bed blocks: Hay Bed (tier 1), Wool Bed (tier 2), Plush Bed (tier 3) and Starlit Bed (tier 4). | MUST | R11, R34, G6 | Beds shorten recovery by tier. | Inspect: the block registry lists four bed ids with these tiers. Fails if: a bed is missing, or a tier is wrong. | content | agreed |
| CD-BED-02 | Dynamics MUST register the four bed recipes from the Recipes table, each crafted from the tier below. | MUST | R11, R24, R34 | Each tier upgrades the one before. | GameTest: craft each bed from its exact inputs, then try each upgrade recipe without the lower-tier bed. Fails if: a recipe fails, or an upgrade works without the lower-tier bed. | content | agreed |
| CD-BED-03 | While a Tired Pokémon lies on a bed, the stamina component MUST divide its full recovery time by 2, 3, 4 or 8 for tier 1, 2, 3 or 4. | MUST | R11, R14 | Better beds shorten rest. | Unit: a Lv100 worker recovers in 150, 100, 75 or 37.5 min, and a Lv100 healer recovers in 60, 40, 30 or 15 min. Fails if: a value is off by more than 1%. | stamina | agreed |
| CD-BED-04 | The stamina component MUST apply a bed's factor only while the Pokémon lies on that bed. | MUST | R11, R24 | The bonus belongs to the bed. | Unit: a Lv10 worker at 0% spends 9,000 ticks on a Hay Bed and then 9,000 ticks off it. Fails if: it does not read 75% ± 0.1%. | stamina | agreed |
| CD-BED-05 | When a worker becomes Tired, the work engine MUST make a bed claim on the nearest unclaimed bed inside its pasture's work area. | MUST | R12 | The approver ruled the nearest unclaimed bed. | GameTest: unclaimed beds at 3 and 5 blocks from a worker that becomes Tired. Fails if: it claims the 5-block bed, or no bed. | work engine | agreed |
| CD-BED-06 | If two unclaimed beds are the same distance from a Tired worker, then the work engine MUST claim the higher tier. | MUST | R47 | A tie needs a rule. | Unit: a Hay Bed and a Plush Bed are both 4.0 blocks away. Fails if: the worker claims the Hay Bed. | work engine | agreed |
| CD-BED-07 | The work engine MUST NOT claim a bed outside the pasture's work area. | MUST NOT | R12, R24, limits sweep | Beds serve the pasture that they stand in. | GameTest: a Tired worker with only a bed at horizontal offset 9, then only a bed at offset 8. Fails if: it claims the offset-9 bed, or skips the offset-8 bed. | work engine | agreed |
| CD-BED-08 | If no unclaimed bed is inside the work area, then the work engine MUST put the Tired worker in RestingInPlace, at its base recovery rate. | MUST | R12, R24 | Rest still happens without a bed. | GameTest: a Lv10 worker at 0% with no bed. Fails if: it leaves its position, it does not show the sleep pose, or it does not reach 100% at 36,000 ± 20 ticks. | work engine | agreed |
| CD-BED-09 | The work engine MUST let a Pokémon of any size claim and use a bed. | MUST | R12 | The approver ruled any size. | GameTest: a Tired Wailord, 8 blocks wide, with one free Hay Bed. Fails if: it does not claim the bed and sleep on it. | work engine | agreed |
| CD-BED-10 | While a worker sleeps on a bed, the brain bridge MUST hold it within 0.25 blocks of the bed's center. | MUST | R12, R24 | The bed bonus needs the worker on the bed. | GameTest: track a sleeper's position for 1,200 ticks. Fails if: it moves more than 0.25 blocks from the center. | brain bridge | agreed |
| CD-BED-11 | If an occupied bed is broken, then the work engine MUST move the sleeper to RestingInPlace at the base rate within 1 tick. | MUST | R24, failure sweep | The bed bonus ends with the bed. | GameTest: break the bed under a sleeper. Fails if: the bed factor still applies, or the sleeper is not in RestingInPlace 1 tick later. | work engine | agreed |
| CD-BED-12 | When a sleeping worker reaches 100%, the claim registry MUST release its bed claim within 1 tick. | MUST | R12, R13 | The bed is free for the next Tired worker, and a healed ally leaves its bed. | GameTest: one sleeper reaches 100% by recovery, and another reaches 100% from a heal. Fails if: either bed claim remains 1 tick later. | claim registry | agreed |
| CD-BED-13 | The work engine MUST NOT send a Pokémon to a bed unless it is Tired and tethered to a pasture in Work Mode. | MUST NOT | R2, R12 | Beds are for Tired workers. | GameTest: a worker at 50% and a wild Pokémon stand beside a free bed for 1,200 ticks. Fails if: either lies on the bed. | work engine | agreed |
| CD-BED-14 | When a Tired worker loads after a restart, the work engine MUST claim a bed for it again within 40 ticks. | MUST | failure sweep, REV1 | Claims are never saved, so a restart drops bed claims. | GameTest: restart while a worker sleeps on a bed. Fails if: it is not on a free bed 40 ticks after it loads. | work engine | agreed |

### HEAL: healers

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-HEAL-01 | The work engine MUST treat each Pokémon with a Fairy type and no machine binding as a healer. | MUST | R13, R18 | Unleashed Fairy-types heal by default. | GameTest: an unleashed Clefairy and a Tired Machop share a pasture. Fails if: Clefairy does not heal Machop within 600 ticks. | work engine | agreed |
| CD-HEAL-02 | The work engine MUST NOT start a heal while the healer's Work Stamina is under 25%. | MUST NOT | R13, limits sweep | A heal costs 25%. | Unit: a healer at 25.0% starts a heal, and a healer at 24.9% does not. Fails if: either result is wrong. | work engine | agreed |
| CD-HEAL-03 | While a healer is Tired, the work engine MUST NOT start a heal. | MUST NOT | R10, R13 | Tired means no work and no heals until 100%. | Unit: a Tired healer that has recovered to 60% has a Tired ally in range. Fails if: it starts a heal. | work engine | agreed |
| CD-HEAL-04 | When a heal completes, the stamina component MUST lower the healer's Work Stamina by 25 percentage points. | MUST | R13, G6 | The approver set the cost. | Unit: a healer at 100% heals four times. Fails if: it does not read 75%, 50%, 25% and 0%, or it is not Tired after the fourth heal. | stamina | agreed |
| CD-HEAL-05 | When a heal completes, the stamina component MUST set the ally's Work Stamina to 100%, whatever the levels of the healer and the ally. | MUST | R13, G6 | The approver ruled that levels do not change the heal. | Unit: a Lv1 healer heals a Tired Lv100 ally. Fails if: the ally reads anything but 100%. | stamina | agreed |
| CD-HEAL-06 | The work engine MUST pick heal targets only from allies without a Fairy type that are tethered to the healer's pasture, and never the healer itself. | MUST | R13 | The approver excluded Fairy allies and other pastures. | GameTest: a Tired Azumarill (Water/Fairy) in the same pasture, and a Tired Machop in a pasture 3 blocks away. Unit: the target filter drops the healer's own entry. Fails if: either Pokémon gets a heal, or the filter keeps the healer. | work engine | agreed |
| CD-HEAL-07 | When a Tired ally exists, the work engine MUST pick the nearest Tired ally. | MUST | R13 | Tired allies come first, as the approver ruled. | GameTest: Tired allies at 3 and 6 blocks and a 10% ally at 1 block. Fails if: the healer goes to any ally but the one at 3 blocks. | work engine | agreed |
| CD-HEAL-08 | When no Tired ally exists, the work engine MUST pick the ally with the lowest Work Stamina under 25%. | MUST | R13, limits sweep | The healer tops up the weakest ally. | Unit: allies at 24.9% and 10% give the 10% ally first, and a lone ally at 25.0% gets no heal. Fails if: either result is wrong. | work engine | agreed |
| CD-HEAL-09 | The claim registry MUST hold at most one healer claim on each ally. | MUST | R2, R13 | Two heals on one ally waste 25%. | GameTest: two healers and one Tired ally. Fails if: both healers heal the ally, or both lose stamina. | claim registry | agreed |
| CD-HEAL-10 | The work engine MUST complete a heal only while the healer is within 2 blocks of the ally. | MUST | R24, limits sweep | The healer walks to its ally first. | Unit: a heal completes at 2.0 blocks and does not complete at 2.1 blocks. Fails if: either result is wrong. | work engine | agreed |
| CD-HEAL-11 | If a heal target stops qualifying before the heal completes, then the work engine MUST cancel the heal at no stamina cost. | MUST | state × event sweep, failure sweep | A heal with no target must not cost the healer. | GameTest: a Tired ally reaches 100% on a bed, or leaves the pasture, while the healer walks to it. Fails if: the healer loses stamina, or the heal completes. | work engine | agreed |
| CD-HEAL-12 | Where a Pokémon follows the healer rule, the stamina component MUST set its full recovery time to 4 × (300 − 270 × (L − 1) ÷ 99) min. | MUST | R14, ADR 0008 | Higher-level healers recover faster, as the approver ruled. | Unit: Lv1 gives 1,200 min, Lv50 gives 665.5 ± 0.5 min, and Lv100 gives 120 min. Fails if: a value differs. | stamina | agreed |
| CD-HEAL-13 | Where a Pokémon with a Fairy type holds a machine binding, the stamina component MUST apply the worker rule to it. | MUST | R18, R24 | A leashed Fairy-type is a worker. | Unit: a leashed Lv50 Azumarill recovers from 0% to 100% outside Working. Fails if: the time falls outside 150 ± 0.5 min. | stamina | agreed |
| CD-HEAL-14 | While a Pokémon with a Fairy type holds a machine binding, the work engine MUST NOT give it heal targets. | MUST NOT | R18 | A Muse or other leashed Fairy-type does not heal. | GameTest: a leashed Clefairy Muse and a Tired ally share a pasture for 1,200 ticks. Fails if: Clefairy heals the ally. | work engine | agreed |
| CD-HEAL-15 | When a heal completes while Cobblemon's server data has no `cobblemon:floralhealing` action effect, the effects component MUST play the healer's `special` animation when its model has one with no sound, and spawn `cobblemon:heal_circles` on the ally. | MUST | R13, RES1, REV1 | No Cobblemon heal move has a move effect in 1.8.4. The file of `cobblemon:heal_sparkles` calls `math.random()` with no arguments, which crashes the 1.8.4 client. Work is silent (CD-FX-09), so the healer never falls back to its cry. | Demo: on Cobblemon 1.8.4, a Clefairy's heal shows its special pose and the heal circles on the ally, with no sound. Fails if: either part is missing, the healer plays its cry, a sound plays, or the client crashes. | effects | agreed |
| CD-HEAL-16 | When a heal completes while Cobblemon's server data has a `cobblemon:floralhealing` action effect, the effects component MUST run that action effect with the healer as the user and the ally entity as the target. | MUST | R13, REV1 | The approver wants Floral Healing as soon as Cobblemon ships it. The target is an entity, so the action effect needs no work focus entity. | Unit: a stub action-effect registry with `cobblemon:floralhealing` runs it with the healer as user and the ally as target, and a registry without it sends the CD-HEAL-15 visual. Fails if: either path is wrong, or the roles in the context are swapped. | effects | agreed |
| CD-HEAL-17 | When a heal drops a healer to 0%, the work engine MUST move it from Healing to ToBed, or to RestingInPlace when no unclaimed bed is in the work area. | MUST | R13, REV1, state × event sweep | A healer that spends its last 25% rests like any Tired worker. | GameTest: a healer at 25% heals once with one free bed in range, and a second healer at 25% heals once with no free bed. Fails if: the first is not in ToBed, or the second is not in RestingInPlace, 1 tick after its heal. | work engine | agreed |

### WHL: Wheel Driver

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-WHL-01 | Where Create is present, the role registry MUST enable the Wheel Driver role for Create water wheels and large water wheels. | MUST | R4, R15, G3 | This is the core Create role. | GameTest: a Water-type claims a dry small wheel, and another claims a dry large wheel. Fails if: either wheel is not a target. | target handler (Create) | agreed |
| CD-WHL-02 | While a Wheel Driver is Working on a wheel with no water, the handler MUST make it turn at 8 RPM with 256 SU for a small wheel, and at 4 RPM with 512 SU for a large wheel. | MUST | R4, RES2, G2 | The wheel works as if water flowed, at Create's own values. | GameTest: a dry small wheel and a dry large wheel, each with a Working worker and the default Create config. Fails if: a speed or a capacity differs. | target handler (Create) | agreed |
| CD-WHL-03 | The Wheel Driver handler MUST report demand exactly while no real water flow turns the wheel. | MUST | R24, R43 | A wheel always counts as busy, unless real water already drives it. CD-ASG-19 then moves an auto-assigned worker to a wheel that needs it. | GameTest: a dry wheel with no load on its network keeps its worker Working, and real flowing water puts the worker in Standby. Fails if: either state is wrong. | target handler (Create) | agreed |
| CD-WHL-04 | The Wheel Driver handler MUST store a spin direction for each wheel, starting at Create's positive speed. | MUST | R17, R40 | Each wheel keeps its own direction. | GameTest: new wheels A and B both turn at +8 RPM. Flip wheel A. Fails if: a new wheel turns at −8 RPM, or wheel B changes. | target handler (Create) | agreed |
| CD-WHL-05 | When the binder, the placer or an operator sneak-right-clicks a driven wheel with the Work Leash, the Wheel Driver handler MUST reverse that wheel's stored direction. | MUST | R17, REV1 | The approver ruled the flip gesture for driven wheels. | GameTest: the binder clicks a worked wheel at +8 RPM. Fails if: it does not read −8 RPM 2 ticks after the click. | target handler (Create) | agreed |
| CD-WHL-06 | When another worker takes over a wheel, the Wheel Driver handler MUST keep the wheel's stored direction. | MUST | R17 | A swap on the other side must not reverse a build. | GameTest: worker A on the north side drives a wheel at +8 RPM, then worker B takes over from the south side. Fails if: the wheel turns at −8 RPM. | target handler (Create) | agreed |
| CD-WHL-07 | The Wheel Driver handler MUST save each wheel's direction with the wheel. | MUST | R17, failure sweep | A restart must not reverse a build. | GameTest: flip a wheel, then restart. Fails if: the direction differs after the restart. | target handler (Create) | agreed |
| CD-WHL-08 | When a Wheel Driver stops Working on a wheel with no real water flow, the handler MUST stop the wheel within 2 ticks. | MUST | R24 | The wheel produces only while the worker works. | GameTest: release the claim on a worked dry wheel. Fails if: the wheel still turns 2 ticks later. | target handler (Create) | agreed |
| CD-WHL-09 | The Wheel Driver handler MUST sync its virtual flow to clients, so Create's goggles show the generated capacity. | MUST | RES2, R24 | Without the sync, the goggle tooltip shows 0 SU. | Demo: look at a worked dry small wheel with goggles. Fails if: the tooltip does not show 256 SU. | target handler (Create) | agreed |
| CD-WHL-10 | If the water-wheel mixin fails to apply, then the role registry MUST disable the Wheel Driver role and log one warning. | MUST | RES2, failure sweep | A Create update must not crash the game. | Unit: start with the mixin's applied flag set to false. Fails if: the role stays enabled, the game crashes, or the warning repeats. | role registry | agreed |

### GEN: Generator

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-GEN-01 | The role registry MUST enable the Generator role for every block whose NeoForge energy capability can receive energy. | MUST | R5, RES3, G3 | Every FE mod and AE2 work with no extra code. | GameTest: an AE2 Energy Acceptor and a test battery are targets, and a block whose capability cannot receive is not. Fails if: any result is wrong. | target handler (NeoForge caps) | agreed |
| CD-GEN-02 | With the default Generator role file, while a Generator is Working, the handler MUST offer round(m × 10 × 500^((L − 1) ÷ 99)) FE per tick, where m is 2 for legendary or mythical Pokémon and 1 for all others. | MUST | R5, G2 | The approver set the curve and the multiplier. | Unit: Lv1 gives 10, Lv25 gives 45, Lv50 gives 217, Lv75 gives 1,041 and Lv100 gives 5,000 FE/t. A legendary Lv50 gives 433 and a legendary Lv100 gives 10,000 FE/t. Fails if: a value differs. | target handler (NeoForge caps) | agreed |
| CD-GEN-03 | With the default Generator role file, the handler MUST NOT double the rate for any label other than `legendary` and `mythical`. | MUST NOT | R5 | The approver named only legendary and mythical Pokémon. | Unit: a Lv100 Pokémon with the label `ultra_beast` and a Lv100 Pokémon with the label `paradox` each give 5,000 FE/t. Fails if: either gives 10,000 FE/t. | target handler (NeoForge caps) | agreed |
| CD-GEN-04 | The Generator handler MUST push FE through the face of the target nearest the worker that accepts energy. | MUST | RES3, R41 | Many AE2 machines accept FE on some faces only. | GameTest: an AE2 Charger with the worker in front of its front face, which does not accept FE. Fails if: the charger's buffer does not rise. | target handler (NeoForge caps) | agreed |
| CD-GEN-05 | The Generator handler MUST NOT keep FE that the target refuses. | MUST NOT | R24 | A hidden buffer would hand out bursts above the rate. | Unit: a full battery refuses 217 FE. When it empties, the next tick offers 217 FE. Fails if: an offer exceeds the rate. | target handler (NeoForge caps) | agreed |
| CD-GEN-06 | The Generator handler MUST report demand only while a simulated insert of 1 FE succeeds. | MUST | R24 | A worker at a full battery rests in Standby. | GameTest: fill the battery. Fails if: the worker is not in Standby within 20 ticks, or its stamina drains. | target handler (NeoForge caps) | agreed |
| CD-GEN-07 | Where AE2 is present, the Generator handler MUST deliver its FE into an AE2 Energy Acceptor at AE2's configured ratio. | MUST | R5, RES3 | AE2 is a named integration target, and it converts 2 FE to 1 AE by default. | GameTest: a Lv50 Generator on an Energy Acceptor whose grid can store energy. Fails if: stored energy does not rise by 108.5 ± 1 AE per tick. | target handler (NeoForge caps) | agreed |
| CD-GEN-08 | The Charge Cell MUST store up to 50,000 FE through NeoForge's energy capability, which it exposes on all six faces, each of which takes and gives FE. | MUST | R60 | The approver's ruling of 2026-11-10. A Generator worker charges a cell with no handler change, because CD-GEN-01 targets any block whose energy capability can receive. Cables and machines of other mods reach it from any side. | Unit: a cell takes FE up to 50,000 and refuses the rest. GameTest: each face of a placed cell has a capability that takes FE, and a Lv100 Pikachu fills the cell in offers of 5,000 FE, then waits in Standby. Fails if: a face has no capability or refuses FE, the full cell holds more or less than 50,000 FE, or the Pikachu does not charge it. | content | agreed |
| CD-GEN-09 | Each tick, the Charge Cell MUST push its stored FE into the adjacent blocks that take FE: an equal share for each face whose block is not a Charge Cell, with what a face cannot take going to the others, and then to each neighbouring cell that holds less only enough FE to leave both at one level. | MUST | R60 | The approver's ruling of 2026-11-10. A cell feeds the machines around it. FE flows into a cell only while that cell holds less, and never back, so it cannot loop between two cells. | Unit: 10,000 FE over a face that takes 1,000 and two faces that take any amount gives 1,000, 4,500 and 4,500. Two cells of 10,000 and 0 FE settle at 5,000 each and move nothing after that, and a ring of four cells keeps its FE and stops moving it. GameTest: a cell gives test batteries on three faces 1,000, 4,500 and 4,500 FE in one tick, and two adjacent cells settle at 5,000 FE each and hold it for 40 ticks. Fails if: a face gets another share, FE is lost or made, or FE keeps moving between cells. | content | agreed |
| CD-GEN-10 | The Charge Cell MUST give a comparator signal of 0 when it is empty and 1 + floor(14 × stored ÷ 50,000) otherwise, so the signal is 15 only when the cell is full. | MUST | R60 | The approver's ruling of 2026-11-10. Redstone can switch machines on the fill of a cell, as it can on the fill of a vanilla container. | Unit: 0, 1, 25,000, 49,999 and 50,000 FE give 0, 1, 8, 14 and 15, and the signal never falls as the cell fills. GameTest: comparators behind cells with 0, 25,000 and 50,000 FE read 0, 8 and 15. Fails if: a signal differs. | content | agreed |
| CD-GEN-11 | When a Charge Cell is broken, its drop MUST keep the cell's stored FE in the data component `cobblemon_dynamics:stored_energy`, show the FE in its tooltip and give it to the cell that it places. | MUST | R60 | The approver's ruling of 2026-11-10. A player moves a charged cell and keeps its FE. | GameTest: break a cell that holds 12,345 FE, then place its drop. Fails if: the drop's component is not 12,345, its tooltip does not read "12,345 / 50,000 FE", or the placed cell does not hold 12,345 FE. | content | agreed |
| CD-GEN-12 | Dynamics MUST register the Charge Cell recipe from the Recipes table. | MUST | R60 | The approver's ruling of 2026-11-10. Players craft cells in survival. | GameTest: craft the cell from its exact inputs, then try the same grid without the Thunder Stone. Fails if: the first grid makes no Charge Cell, or the second makes one. | content | agreed |

### STK: Stoker

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-STK-01 | The role registry MUST enable the Stoker role for every block entity that extends `AbstractFurnaceBlockEntity`. | MUST | R6, RES3, G3 | Furnaces, smokers, blast furnaces and modded subclasses all work. | GameTest: a furnace, a smoker and a blast furnace, each with input. Fails if: one is not claimed. | target handler (vanilla) | agreed |
| CD-STK-02 | While a Stoker is Working, the handler MUST keep the furnace lit while its recipe can run. | MUST | R6, G2 | The furnace burns as if it had coal. | GameTest: a furnace with 8 raw iron and an empty fuel slot smelts 8 iron in 1,600 ± 20 ticks, and a smoker cooks 8 beef in 800 ± 20 ticks. Fails if: either takes longer, or stops. | target handler (vanilla) | agreed |
| CD-STK-03 | The Stoker handler MUST NOT consume items from the fuel slot. | MUST NOT | R6 | The Pokémon is the fuel. | GameTest: a fuel slot with 1 coal, then 8 smelts. Fails if: the coal count drops. | target handler (vanilla) | agreed |
| CD-STK-04 | When a Stoker starts heating an unlit furnace, the handler MUST set the furnace's `LIT` block state to true. | MUST | RES3 | Vanilla sets `LIT` only when it burns its own fuel. | GameTest: read the block state 1 tick after Working starts. Fails if: `LIT` is false. | target handler (vanilla) | agreed |
| CD-STK-05 | The Stoker handler MUST report demand only while the furnace's recipe can run. | MUST | R24 | An empty or blocked furnace needs no heat. | GameTest: fill the output slot to 64. Fails if: the worker is not in Standby within 20 ticks. | target handler (vanilla) | agreed |
| CD-STK-06 | When a Stoker stops Working, the handler MUST return the furnace to vanilla fuel rules within 2 ticks. | MUST | R24 | Players keep a normal furnace after the worker leaves. | GameTest: with no fuel, the furnace goes out within 2 ticks, and with 1 coal it burns that coal. Fails if: it stays lit with no fuel, or ignores the coal. | target handler (vanilla) | agreed |
| CD-STK-07 | While a Stoker heats a furnace, the handler SHOULD set the burn duration to 1,600 ticks, so the flame gauge reads as a coal burn. | SHOULD | R6 | The gauge shows why the furnace runs. Acceptable to skip for a modded furnace that draws its own gauge. | Demo: watch the flame gauge of a worked furnace. Fails if: the gauge shows empty while the furnace cooks. | target handler (vanilla) | agreed |

### BRN: Blaze Burner heat

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-BRN-01 | With the default Superheater role file, while a Superheater below level 50 is Working, the handler MUST hold the Blaze Burner at `KINDLED`, as coal does. | MUST | R26, R59, RES2 | A Dragon-type heats a burner from its first level, so a young dragon has a job before it can superheat. CD-SUP-04 covers the change at level 50. | GameTest: a Lv10 Dratini on a filled burner with no fuel and a basin above. Fails if: it reads anything but `KINDLED` 1 tick after Working starts. | target handler (Create) | agreed |
| CD-BRN-02 | The Superheater handler MUST NOT target an empty Blaze Burner. | MUST NOT | RES2, misuse sweep | Heat on an empty burner makes it drop a full burner, which duplicates a blaze. | GameTest: an empty burner with a basin above, and a Dragon-type beside it for 400 ticks. Fails if: the burner is claimed. | target handler (Create) | agreed |
| CD-BRN-03 | The Superheater handler MUST report demand only while a basin or a fluid tank sits directly above the burner. | MUST | R24 | Heat with no consumer wastes stamina. | GameTest: remove the basin above a worked burner. Fails if: the worker is not in Standby within 20 ticks. | target handler (Create) | agreed |
| CD-BRN-04 | The Superheater handler MUST NOT change the burner's stored fuel. | MUST NOT | RES2, REV1 | The burner's own fuel burns down at its normal rate while a worker heats it, so the worker neither saves nor adds fuel. | GameTest: two burners with 1,000 ticks of coal fuel each, one worked for 400 ticks. Fails if: their remaining burn times differ. | target handler (Create) | agreed |
| CD-BRN-05 | While a Superheater below level 50 heats a burner under a Create boiler, the boiler MUST count 1 heat from that burner. | MUST | R26, R59, RES2 | Pokémon heat powers steam engines from a Dragon-type's first level. | GameTest: a boiler on a burner that a Lv10 Dratini works. Fails if: the heat from that burner is not 1. | target handler (Create) | agreed |
| CD-BRN-06 | When a Superheater stops Working, the handler MUST return the burner to the heat level of its own fuel within 2 ticks. | MUST | R24 | Heat ends with the work. | GameTest: release the claim on a worked burner with no fuel. Fails if: it does not read `SMOULDERING` 2 ticks later. | target handler (Create) | agreed |
| CD-BRN-07 | If the Blaze Burner mixin fails to apply, then the role registry MUST disable the Superheater role and log one warning. | MUST | RES2, failure sweep | A Create update must not crash the game. | Unit: start with the mixin's applied flag set to false. Fails if: the role stays enabled, or the game crashes. | role registry | agreed |

### SUP: Superheater

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-SUP-01 | With the default Superheater role file, while a Superheater of level 50 or higher is Working, the handler MUST hold the Blaze Burner at `SEETHING`. | MUST | R15, R26, RES2, G3 | Dragon-types superheat as a Blaze Cake does once they reach level 50. | GameTest: a Lv50 Dragonair on a filled burner with no fuel and a basin above. Fails if: it does not read `SEETHING` 1 tick after Working starts. | target handler (Create) | agreed |
| CD-SUP-02 | With the default role files, the role registry MUST give a Blaze Burner the Superheater as its only role, and list only the Dragon type for it. | MUST | R15, R59 | Superheat is the Dragon role, and the approver ruled that Fire-types do not heat Blaze Burners (2026-11-10). | Unit: a Fire-type Charmander is not eligible for the Superheater, a Dragon-type Dratini is, and the default role files give a burner no other role. GameTest: a Charmander stands beside a filled burner with a basin above for 400 ticks, then a Dratini joins its pasture. Fails if: a result is wrong, the Charmander claims the burner, or the Dratini does not claim it. | role registry | agreed |
| CD-SUP-03 | While a Superheater of level 50 or higher heats a burner under a Create boiler, the boiler MUST count 2 heat from that burner. | MUST | R15, R26, RES2 | Superheat doubles the boiler heat. | GameTest: a boiler on a burner that a Lv50 Superheater works. Fails if: the heat from that burner is not 2. | target handler (Create) | agreed |
| CD-SUP-04 | With the default Superheater role file, while a Superheater below level 50 is Working, the handler MUST hold the Blaze Burner at `KINDLED`. | MUST | R26, R25, limits sweep | Leveling pays off, and a blaze-cake chain stays the early answer to superheat. | GameTest: a Lv49 Dragonair works a filled burner with no fuel under a boiler, then levels to 50. Fails if: the burner reads anything but `KINDLED` with 1 boiler heat at Lv49, or anything but `SEETHING` with 2 boiler heat at Lv50. | target handler (Create) | agreed |

### CRK: Cranker

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-CRK-01 | Where Create is present, the role registry MUST enable the Cranker role for Create hand cranks, for the Fighting and Normal types. | MUST | R15, G3 | Common Pokémon get an early job. | Unit: Machop (Fighting) and Rattata (Normal) are eligible, and Pikachu is not. Fails if: any result is wrong. | role registry | agreed |
| CD-CRK-02 | While a Cranker is Working, the handler MUST turn the hand crank every 5 ticks. | MUST | R24, RES2, limits sweep | Create stops a crank that nobody turns for 10 ticks. | Unit: turns at ticks 0, 5 and 10, and none at tick 4. GameTest: the crank holds 32 RPM for 600 ticks. Fails if: an interval is not 5 ticks, or the speed drops at any tick. | target handler (Create) | agreed |
| CD-CRK-03 | While a Cranker is Working, the handler MUST make the hand crank generate 32 RPM with 256 SU. | MUST | R24, RES2 | The crank works at Create's own values. | GameTest: a worked crank with the default Create config. Fails if: the speed or the capacity differs. | target handler (Create) | agreed |
| CD-CRK-04 | The role registry MUST NOT enable the Cranker role for valve handles. | MUST NOT | RES2 | Create turns a valve handle only by a fixed angle. | GameTest: a Machop beside a valve handle for 400 ticks. Fails if: it claims the valve handle. | role registry | agreed |
| CD-CRK-05 | The Cranker handler MUST store a spin direction for each hand crank, starting at Create's positive speed. | MUST | R24, R40 | A crank keeps its direction, as a wheel does. | GameTest: a new worked crank. Fails if: it turns at −32 RPM. | target handler (Create) | agreed |
| CD-CRK-06 | When the binder, the placer or an operator sneak-right-clicks a driven hand crank with the Work Leash, the Cranker handler MUST reverse that crank's stored direction. | MUST | R24, REV1 | Cranks use the same flip gesture as wheels. | GameTest: the binder clicks a worked crank at +32 RPM. Fails if: it does not read −32 RPM 5 ticks after the click. | target handler (Create) | agreed |
| CD-CRK-07 | The Cranker handler MUST report demand at all times. | MUST | R24 | A crank always counts as busy, as approved. | GameTest: a crank with no network keeps its worker Working, and stamina drains. Fails if: the worker enters Standby. | target handler (Create) | agreed |
| CD-CRK-08 | The Cranker handler MUST save each crank's direction with the crank. | MUST | failure sweep | A restart must not reverse a build. | GameTest: flip a crank, then restart. Fails if: the direction differs after the restart. | target handler (Create) | agreed |

### CRY: Crystal Tender

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-CRY-01 | The role registry MUST enable the Crystal Tender role for blocks in the `#c:budding_blocks` tag, for the Rock type. | MUST | R15, RES3, G3 | The tag covers budding amethyst and AE2's budding certus. | GameTest: a Rock-type claims budding amethyst, and with AE2 present, flawless budding certus. Fails if: either block is not claimed. | target handler (AE2 tag) | agreed |
| CD-CRY-02 | While a Crystal Tender is Working, the handler MUST give the target 1 extra random tick every 10 ticks. | MUST | R15, RES3, limits sweep | The rate equals one AE2 Growth Accelerator, including AE2's faster decay of non-flawless budding certus. | Unit: random ticks at ticks 0, 10 and 20, and none at tick 9. Fails if: an interval is not 10 ticks. | target handler (AE2 tag) | agreed |
| CD-CRY-03 | The Crystal Tender handler MUST report demand only while at least one face of the target can still grow a bud. | MUST | R24 | Extra ticks on a fully grown block do nothing. | GameTest: budding amethyst with a full cluster on all 6 faces. Fails if: the worker is not in Standby within 20 ticks. | target handler (AE2 tag) | agreed |
| CD-CRY-04 | Where AE2 is absent, the role registry MUST keep the Crystal Tender role enabled for `minecraft:budding_amethyst`. | MUST | R9, RES3 | The tag comes from NeoForge, so the role needs no AE2 code. | GameTest: a server without AE2. Fails if: a Rock-type does not claim budding amethyst. | target handler (AE2 tag) | agreed |
| CD-CRY-05 | The main source set MUST NOT import AE2 classes. | MUST NOT | R9, RES3 | The tag-only design keeps AE2 optional. | Inspect: no main source file imports from `appeng`, and the main source set has no AE2 compile dependency. Fails if: either exists. | build | agreed |

### BRF: Brew Stoker

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-BRF-01 | The role registry MUST enable the Brew Stoker role for vanilla brewing stands, for the Fire type. | MUST | R15, G3 | The approver selected Brewing Fuel. | GameTest: a Fire-type beside a brewing stand that can brew. Fails if: the stand is not claimed. | role registry | agreed |
| CD-BRF-02 | While a Brew Stoker is Working, the handler MUST keep the stand's fuel at 1 or more. | MUST | R15, RES3 | The stand brews as if it had blaze powder. | GameTest: 3 water bottles and nether wart, with no blaze powder. Fails if: the brew does not complete. | target handler (vanilla) | agreed |
| CD-BRF-03 | The Brew Stoker handler MUST NOT consume blaze powder from the fuel slot. | MUST NOT | R15 | The Pokémon is the fuel. | GameTest: 1 blaze powder in the fuel slot, then 3 brews. Fails if: the count drops. | target handler (vanilla) | agreed |
| CD-BRF-04 | The Brew Stoker handler MUST report demand only while the stand holds an ingredient that can brew at least one of its bottles. | MUST | R24 | A stand with nothing to brew needs no fuel. | GameTest: remove the ingredient. Fails if: the worker is not in Standby within 20 ticks. | target handler (vanilla) | agreed |
| CD-BRF-05 | When a Brew Stoker stops Working, the handler MUST NOT leave the stand with more fuel than it held before the worker started. | MUST NOT | misuse sweep | Leftover fuel would be a free brew. | GameTest: a stand at fuel 0 is worked, then released before a brew starts. Fails if: its fuel reads above 0. | target handler (vanilla) | agreed |

### BRW: Brewer

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-BRW-01 | The role registry MUST enable the Brewer role for vanilla brewing stands, for the Poison type. | MUST | R15, G3 | The approver selected Brewing Speed. | GameTest: a Poison-type beside a stand with a running brew. Fails if: the stand is not claimed. | role registry | agreed |
| CD-BRW-02 | While a Brewer is Working, the handler MUST make each brew finish in 200 ticks. | MUST | R15, limits sweep | Brews run 2× faster, as the approver selected. | GameTest: time a worked brew from its start. Fails if: it takes more than 202 or less than 198 ticks. | target handler (vanilla) | agreed |
| CD-BRW-03 | The Brewer handler MUST report demand only while a brew runs. | MUST | R24 | A Brewer has nothing to speed up otherwise. | GameTest: a stand with no brew running. Fails if: the worker is not in Standby within 20 ticks. | target handler (vanilla) | agreed |
| CD-BRW-04 | The Brewer handler MUST NOT change the stand's fuel. | MUST NOT | R24 | Speed and fuel are separate roles. | GameTest: two stands with fuel 20 run one brew each, one with a Brewer. Fails if: their fuel values differ after the brews. | target handler (vanilla) | agreed |

### PMP: Pump

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-PMP-01 | The role registry MUST enable the Pump role for the Water type on blocks whose NeoForge fluid capability accepts water, and on vanilla cauldrons. | MUST | R15, RES3, G3 | Tanks, boilers, basins and cauldrons take water. | GameTest: bind a Water-type to a Create fluid tank, a basin and a cauldron in turn. Fails if: a bind is refused. | role registry | agreed |
| CD-PMP-02 | The role registry MUST set the Pump role's auto-assign flag to false. | MUST | R19 | The approver ruled the Pump leash only. | Inspect: the Pump role file has `"auto_assign": false`. Fails if: the flag is true or missing. | role registry | agreed |
| CD-PMP-03 | With the default Pump role file, while a Pump is Working, the handler MUST offer 5 × L mB of water per tick. | MUST | R24 | Water supply grows with level. | GameTest: a Lv1 Pump adds 5,000 ± 50 mB to an empty tank in 1,000 ticks, and a Lv100 Pump adds 500 mB per tick. Fails if: either rate is off by more than 1%. | target handler (NeoForge caps) | agreed |
| CD-PMP-04 | While a Pump works on a cauldron, the handler MUST raise the water level by one step for each 333 mB that it delivers, by setting the cauldron's block state. | MUST | R24, RES5, REV1 | A bucket is 1,000 mB and fills a cauldron in 3 steps. NeoForge's cauldron wrapper moves only a full 1,000 mB at a time. | GameTest: a Lv10 Pump, at 50 mB per tick, fills an empty cauldron. Fails if: it takes less than 19 or more than 21 ticks. | target handler (vanilla) | agreed |
| CD-PMP-05 | For a target other than a vanilla cauldron, the Pump handler MUST report demand only while a simulated fill of 1 mB of water succeeds. | MUST | R24, REV1 | A full tank, or a tank of another fluid, needs no Pump. | GameTest: fill the tank. Fails if: the worker is not in Standby within 20 ticks. | target handler (NeoForge caps) | agreed |
| CD-PMP-06 | For a vanilla cauldron, the Pump handler MUST report demand only while the cauldron is empty or holds water below level 3. | MUST | R24, RES5, REV1, limits sweep | Cauldrons fill in steps, not through a 1 mB fill. | GameTest: an empty cauldron and a water cauldron at level 2 give demand, and a water cauldron at level 3, a lava cauldron and a powder snow cauldron give none. Fails if: any of the five results is wrong. | target handler (vanilla) | agreed |

### MUS: Muse

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-MUS-01 | The role registry MUST enable the Muse role for vanilla enchanting tables, for the Fairy type. | MUST | R15, R18, G3 | The approver selected Enchanting Power. | GameTest: bind a Clefairy to an enchanting table. Fails if: the bind is refused. | role registry | agreed |
| CD-MUS-02 | The role registry MUST set the Muse role's auto-assign flag to false. | MUST | R18 | A Fairy-type is a Muse only when leashed. | Inspect: the Muse role file has `"auto_assign": false`. Fails if: the flag is true or missing. | role registry | agreed |
| CD-MUS-03 | With the default Muse role file, while a Muse is at its enchanting table, the handler MUST add ceil(15 × L ÷ 100) bookshelves to the table's count. | MUST | R18 | The approver set the formula. | Unit: Lv1 adds 1, Lv7 adds 2, Lv50 adds 8 and Lv100 adds 15. Fails if: a value differs. | target handler (vanilla) | agreed |
| CD-MUS-04 | The Muse handler MUST cap the table's total bookshelf count at 15. | MUST | R18, limits sweep | Vanilla offers peak at 15 bookshelves. | Unit: with a Lv50 Muse, 12 real bookshelves count as 15, 7 count as 15 and 6 count as 14. Fails if: a total differs. | target handler (vanilla) | agreed |
| CD-MUS-05 | The Muse handler MUST report demand only while a player has that table's enchanting menu open. | MUST | R24, REV1 | A Muse rests until someone enchants. | GameTest: no menu open for 600 ticks, then a player keeps the table's menu open for 600 ticks. Fails if: the Muse's stamina drains while the menu is closed, or does not drain while it is open. | target handler (vanilla) | agreed |
| CD-MUS-06 | If the enchanting-table mixin fails to apply, then the role registry MUST disable the Muse role and log one warning. | MUST | failure sweep, RES5 | A NeoForge update must not crash the game. The mixin changes the menu's power value, so vanilla's random rolls stay the same. | Unit: start with the mixin's applied flag set to false. Fails if: the role stays enabled, or the game crashes. | role registry | agreed |

### TLK: Telekinesis (Phase 2)

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-TLK-01 | The role registry MUST enable the Telekinesis role for the Psychic type, with a source and a destination that each expose NeoForge's item handler capability. | MUST | R16, R27, R33, RES5, G3 | Psychic-types move items between containers. | GameTest: bind a Psychic-type from a chest to a barrel. Fails if: the bind is refused. | role registry | agreed |
| CD-TLK-02 | The role registry MUST set the Telekinesis role's auto-assign flag to false. | MUST | R29 | The approver ruled the Phase 2 roles leash only. | Inspect: the Telekinesis role file has `"auto_assign": false`. Fails if: the flag is true or missing. | role registry | agreed |
| CD-TLK-03 | With the default Telekinesis role file, the handler MUST move at most ceil(64 × L ÷ 100) items in each 20-tick batch. | MUST | R27, R52, limits sweep | The approver set the rate. | Unit: from a full source, Lv1 moves 1, Lv25 moves 16, Lv50 moves 32 and Lv100 moves 64 items per batch, and Lv50 never moves a 33rd item in one batch. Fails if: a batch moves more, or moves fewer while both containers allow the full amount. | target handler (NeoForge caps) | agreed |
| CD-TLK-04 | The Telekinesis handler MUST extract through the source face and insert through the destination face that the player clicked. | MUST | R33 | Sided containers, such as furnaces, take items by face. | GameTest: a chest of coal is the source, and a furnace clicked on its side is the destination. Fails if: the coal lands anywhere but the fuel slot. | target handler (NeoForge caps) | agreed |
| CD-TLK-05 | The claim registry MUST hold a Telekinesis worker's work claim on its source only. | MUST | R27 | Many workers can feed one destination, for example one ME Interface. | GameTest: two Psychic-types with different chests as sources bind to the same barrel. Fails if: the second bind is refused, or the barrel holds a claim. | claim registry | agreed |
| CD-TLK-06 | The Telekinesis handler MUST report demand only while the source holds an item that the destination accepts. | MUST | R33 | A worker with nothing to move rests. | GameTest: empty the source. Then fill the destination with stone while the source holds dirt. Fails if: the worker is not in Standby within 20 ticks in either case. | target handler (NeoForge caps) | agreed |
| CD-TLK-07 | The Telekinesis handler MUST NOT lose or duplicate items. | MUST NOT | RES5, failure sweep | A transfer that eats or copies items breaks a base. Create chutes and depots keep the stack object that they receive, so the handler inserts copies. | GameTest: move 2,000 items from a chest into a Create chute, a depot and an item vault in turn, and break the destination in the middle of one batch. Fails if: the total item count across the source, the destination and the drops changes. | target handler (NeoForge caps) | agreed |
| CD-TLK-08 | Where AE2 is present, the Telekinesis handler MUST insert into an ME Interface block, and into an ME Interface cable part through the face that the part sits on. | MUST | R33, RES5 | AE2 is a named integration target. | GameTest: a powered network with a free channel, with an interface block and an interface part as destinations in turn. Fails if: the items do not reach the network's storage within 40 ticks. | target handler (NeoForge caps) | agreed |
| CD-TLK-09 | If the Telekinesis source sits outside the worker's pasture work area, then the work engine MUST refuse the bind with `"Outside its pasture's work area"`. | MUST | R54, limits sweep | The source must serve its own pasture. The destination follows CD-TLK-12. | GameTest: with the default config, a source at horizontal offset 9 and one at offset 8, each with a destination inside the work area. Fails if: the offset-9 bind succeeds, or the offset-8 bind fails. | work engine | agreed |
| CD-TLK-10 | If the destination is the source block, then the work engine MUST refuse the bind with `"Destination must differ from the source"`. | MUST | R57, misuse sweep | A loop on one block moves nothing and wastes a worker. | GameTest: click the same chest as source and as destination. Fails if: a binding exists, or the message is missing. | work engine | agreed |
| CD-TLK-11 | When a Telekinesis source or destination is broken or replaced, the work engine MUST clear the binding. | MUST | entity lifecycle sweep | A binding to a missing container moves nothing. | GameTest: break the destination of one binding, and replace the source of a second binding with stone. Fails if: either binding remains 20 ticks later. | work engine | agreed |
| CD-TLK-12 | If the Telekinesis destination sits more than 32 blocks from the pasture block on any axis, then the work engine MUST refuse the bind with `"Too far from its pasture"`. | MUST | R54, R24, limits sweep | A destination may sit outside the work area, but not past Cobblemon's default roam box. | GameTest: with the default Telekinesis role file, destinations at offsets 32 and 33 from the pasture block on the x axis. Fails if: the offset-32 bind fails, the offset-33 bind succeeds, or the refusal shows another message. | work engine | agreed |

### MAG: Magnet (Phase 2)

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-MAG-01 | The role registry MUST enable the Magnet role for the Steel type, on blocks that expose NeoForge's item handler capability. | MUST | R16, R31, R33, G3 | Steel-types collect drops into a container. | GameTest: bind a Steel-type to a chest. Fails if: the bind is refused. | role registry | agreed |
| CD-MAG-02 | The role registry MUST set the Magnet role's auto-assign flag to false. | MUST | R29 | The approver ruled the Phase 2 roles leash only. | Inspect: the Magnet role file has `"auto_assign": false`. Fails if: the flag is true or missing. | role registry | agreed |
| CD-MAG-03 | With the default Magnet role file, the handler MUST insert at most ceil(64 × L ÷ 100) dropped items into its container in each 20-tick batch. | MUST | R31, R52, limits sweep | The approver set the rate. | Unit: with 200 items on the ground, Lv1 inserts 1, Lv50 inserts 32 and Lv100 inserts 64 per batch. Fails if: a batch inserts more, or fewer while the container has room. | target handler (NeoForge caps) | agreed |
| CD-MAG-04 | The Magnet handler MUST collect only dropped items inside its worker's pasture work area. | MUST | R31, limits sweep | The approver set the range to the whole work area. | GameTest: with the default config, items at horizontal offsets 8 and 9 from the pasture block. Fails if: the offset-9 item is taken, or the offset-8 item is not. | target handler (NeoForge caps) | agreed |
| CD-MAG-05 | The Magnet handler MUST NOT collect an item that still has a pickup delay. | MUST NOT | R33, RES5 | A fresh drop belongs to the player who dropped it. | GameTest: a player drops an item with a 40-tick pickup delay in range. Fails if: the Magnet takes it before its delay ends, or has not taken it 40 ticks after the delay ends. | target handler (NeoForge caps) | agreed |
| CD-MAG-06 | The Magnet handler MUST NOT lose or duplicate items. | MUST NOT | RES5, failure sweep | A partial insert must leave the rest on the ground. | GameTest: a container with room for 10 items, and a stack of 64 on the ground, with a Lv100 Magnet. Fails if: the container gains more than 10 items, or the ground stack does not hold 54 after the batch. | target handler (NeoForge caps) | agreed |
| CD-MAG-07 | The Magnet handler MUST report demand only while a dropped item is in range and the container accepts it. | MUST | R33 | A Magnet with nothing to collect rests. | GameTest: no items on the ground. Then a full container with items on the ground. Fails if: the worker is not in Standby within 20 ticks in either case. | target handler (NeoForge caps) | agreed |
| CD-MAG-08 | The Magnet handler MUST look for dropped items at most once every 20 ticks. | MUST | RES5, quality sweep | An entity query on every tick costs server time. | Unit: on a fake clock, queries run at ticks 0, 20 and 40, and none runs at tick 19. Fails if: a query runs more often. | target handler (NeoForge caps) | agreed |

### FAN: Fan Catalyst (Phase 2)

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-FAN-01 | Where Create is present, the role registry MUST enable the Fan Catalyst role for Create encased fans, for the Fire, Water and Ghost types. | MUST | R16, R33, RES5, G3 | The worker replaces the lava, water or soul fire in front of a fan. | Unit: Charmander, Squirtle and Gastly are eligible, and Pikachu is not. Fails if: any result is wrong. | role registry | agreed |
| CD-FAN-02 | The role registry MUST set the Fan Catalyst role's auto-assign flag to false. | MUST | R29 | The approver ruled the Phase 2 roles leash only. | Inspect: the Fan Catalyst role file has `"auto_assign": false`. Fails if: the flag is true or missing. | role registry | agreed |
| CD-FAN-03 | The work engine MUST put a Fan Catalyst's work spot in the block directly in front of the fan's face. | MUST | R33, RES5 | Create checks the blocks along the air current, and a catalyst in the first block covers the whole line. | GameTest: bind a Charmander to a fan that faces east. Fails if: its work spot is not the block east of the fan. | work engine | agreed |
| CD-FAN-04 | If the block in front of the fan cannot hold the worker, then the work engine MUST refuse the bind with `"No room in front of the fan"`. | MUST | R57, failure sweep | A worker cannot stand inside a solid block. | GameTest: place stone in front of the fan, then bind. Fails if: a binding exists, or the message is missing. | work engine | agreed |
| CD-FAN-05 | While a Fire-type Fan Catalyst works a fan in blasting mode, the fan MUST process items in its air current as Create's bulk blasting does. | MUST | R28, RES5 | Fire-types replace lava. | GameTest: raw iron on a belt in the air current. Fails if: it does not become an iron ingot. | target handler (Create) | agreed |
| CD-FAN-06 | While a Fire-type Fan Catalyst works a fan in smoking mode, the fan MUST process items in its air current as Create's bulk smoking does. | MUST | R28, RES5 | Fire-types replace a campfire for food. | GameTest: raw beef and raw iron in the air current. Fails if: the beef does not become steak, or the raw iron changes. | target handler (Create) | agreed |
| CD-FAN-07 | While a Water-type Fan Catalyst works a fan, the fan MUST process items in its air current as Create's bulk washing does. | MUST | R33, RES5 | Water-types replace a water source. | GameTest: red wool in the air current. Fails if: it does not become white wool. | target handler (Create) | agreed |
| CD-FAN-08 | While a Ghost-type Fan Catalyst works a fan, the fan MUST process items in its air current as Create's bulk haunting does. | MUST | R33, RES5 | Ghost-types replace soul fire. | GameTest: an item with a Create haunting recipe in the air current. Fails if: it does not become the recipe's output. | target handler (Create) | agreed |
| CD-FAN-09 | The Fan Catalyst handler MUST store a mode for each fan, starting at blasting. | MUST | R28 | Each fan keeps its own Fire mode. | GameTest: read the mode of a new fan. Fails if: it reads anything but blasting. | target handler (Create) | agreed |
| CD-FAN-10 | When the binder, the placer or an operator sneak-right-clicks a driven fan with the Work Leash, the Fan Catalyst handler MUST switch that fan's mode between blasting and smoking. | MUST | R28, REV1 | The approver ruled a per-fan flip, with the same gesture as a wheel. | GameTest: the binder flips a worked fan twice. Fails if: the mode does not read smoking after the first click and blasting after the second. | target handler (Create) | agreed |
| CD-FAN-11 | The Fan Catalyst handler MUST save each fan's mode with the fan. | MUST | failure sweep | A restart must not change a production line. | GameTest: flip a fan to smoking, then restart. Fails if: it reads blasting after the restart. | target handler (Create) | agreed |
| CD-FAN-12 | The Fan Catalyst handler MUST exempt its own worker from the fan's push and from the fan's effects on entities. | MUST | R33, RES5 | Create's fan pushes, burns, damages and blinds the entities in its current. | GameTest: a Fire, a Water and a Ghost worker each work a fan at 256 RPM for 1,200 ticks. Fails if: a worker moves more than 0.5 blocks from its spot, takes damage, burns, or gains blindness or slowness. | target handler (Create) | agreed |
| CD-FAN-13 | Dynamics MUST register its fan processing types with a priority above 400. | MUST | RES5 | A worker that stands in water must still count as its own catalyst, ahead of Create's washing at priority 400. | GameTest: a Ghost worker stands in a water source block in front of the fan. Fails if: the items are washed and not haunted. | target handler (Create) | agreed |
| CD-FAN-14 | While a Fan Catalyst is Working, the client MUST show the processing particles of its catalyst type in the air current. | MUST | RES5 | The client rebuilds the air current itself, so it must see the worker as a catalyst. | Demo: watch a fan with each of the three catalyst types. Fails if: the particles do not match the type. | client | agreed |
| CD-FAN-15 | The Fan Catalyst handler MUST report demand only while the fan spins and its air current holds at least one item. | MUST | R33, R45 | A fan with nothing to process needs no catalyst. | GameTest: stop the fan. Then restart it and remove all items from the current. Fails if: the worker is not in Standby within 20 ticks in either case. | target handler (Create) | agreed |
| CD-FAN-16 | When a fan's mode changes, the client MUST show `"Fan mode: Blasting"` or `"Fan mode: Smoking"` in the action bar. | MUST | R57 | The mode is not visible on the fan. | Demo: flip a fan twice. Fails if: the action bar does not show each new mode. | client | agreed |
| CD-FAN-17 | If a fan mixin fails to apply, then the role registry MUST disable the Fan Catalyst role and log one warning. | MUST | failure sweep | A Create update must not crash the game. | Unit: start with the mixin's applied flag set to false. Fails if: the role stays enabled, or the game crashes. | role registry | agreed |

### TLW: Tailwind (Phase 2)

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-TLW-01 | Where Create is present, the role registry MUST enable the Tailwind role for Create windmill bearings, for the Flying type. | MUST | R16, R33, G3 | Flying-types add wind to a windmill. | Unit: Pidgey is eligible, and Pikachu is not. Fails if: either result is wrong. | role registry | agreed |
| CD-TLW-02 | The role registry MUST set the Tailwind role's auto-assign flag to false. | MUST | R29 | The approver ruled the Phase 2 roles leash only. | Inspect: the Tailwind role file has `"auto_assign": false`. Fails if: the flag is true or missing. | role registry | agreed |
| CD-TLW-03 | With the default Tailwind role file, while a Tailwind worker is Working, the handler MUST add floor(S × L ÷ 100) virtual sails to a windmill with S real sails. | MUST | R30, R25 | Building sails stays the main lever, and levels help. | Unit: S = 64 at Lv50 adds 32 (96 sails, 12 RPM), S = 8 at Lv100 adds 8 (16 sails, 2 RPM), and S = 64 at Lv100 adds 64 (128 sails, 16 RPM). Fails if: a value differs. | target handler (Create) | agreed |
| CD-TLW-04 | The Tailwind handler MUST cap a windmill's real and virtual sails at 128 in total. | MUST | R30, limits sweep | Create caps a windmill at 16 RPM, which 128 sails reach. | Unit: S = 100 at Lv100 counts 128 sails, and S = 96 at Lv33 counts 127 sails. Fails if: either count differs. | target handler (Create) | agreed |
| CD-TLW-05 | The Tailwind handler MUST NOT count virtual sails toward the 8 real sails that a windmill needs to assemble. | MUST NOT | RES5 | Virtual sails add speed, not structure. | GameTest: a bearing with 7 real sails and a bound Lv100 Tailwind worker, and a player tries to assemble it. Fails if: the windmill assembles. | target handler (Create) | agreed |
| CD-TLW-06 | When a windmill's virtual sail bonus changes, the Tailwind handler MUST call `updateGeneratedRotation()` on the server within 1 tick. | MUST | RES5 | Create reads the speed live, but the network updates only on that call. | GameTest: a Lv50 worker starts Working on a 64-sail windmill. Fails if: the network does not turn at 12 RPM 2 ticks later. | target handler (Create) | agreed |
| CD-TLW-07 | The Tailwind handler MUST sync each windmill's virtual sail bonus to clients. | MUST | RES5 | Otherwise the client spins the windmill at the wrong rate and snaps it back every 3 ticks. | Demo: watch a worked windmill for 600 ticks. Fails if: its sails visibly snap. | target handler (Create) | agreed |
| CD-TLW-08 | The Tailwind handler MUST report demand only while the windmill is assembled and turning. | MUST | R33 | A stopped windmill needs no wind. | GameTest: disassemble the windmill. Fails if: the worker is not in Standby within 20 ticks. | target handler (Create) | agreed |
| CD-TLW-09 | When a Tailwind worker stops Working, the handler MUST remove its virtual sails within 2 ticks. | MUST | R24 | The bonus ends with the work. | GameTest: release the claim on a worked 64-sail windmill. Fails if: it does not turn at 8 RPM 2 ticks later. | target handler (Create) | agreed |
| CD-TLW-10 | If the windmill mixin fails to apply, then the role registry MUST disable the Tailwind role and log one warning. | MUST | failure sweep | A Create update must not crash the game. | Unit: start with the mixin's applied flag set to false. Fails if: the role stays enabled, or the game crashes. | role registry | agreed |

### BAL: balance

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-BAL-01 | With the default role files, the Generator's maximum output per worker MUST stay below 15,000 FE/t. | MUST | R25, BM3, G10 | 15,000 FE/t is the approver's figure for a small Extreme Reactors reactor, and one Pokémon must not replace it. | Analysis: the highest default output is 2 × 5,000 = 10,000 FE/t, for a Lv100 legendary. Fails if: any default role file allows 15,000 FE/t or more per worker. | role registry | agreed |
| CD-BAL-02 | With the default config and role files, a full pasture of Lv100 Generators that are not legendary or mythical MUST average at most 40,000 FE/t over a work-and-rest cycle with no beds and no healers. | MUST | R25, G10 | A fleet can compete, at the cost of many Pokémon and planned rest. | Analysis: 16 workers × 5,000 FE/t × 50% uptime = 40,000 FE/t. Fails if: the computed average is above 40,000 FE/t. | stamina | agreed |
| CD-BAL-03 | With the default config, the stamina component MUST NOT let a worker that no healer serves work more than 8 ÷ 9 (88.9%) of the time. | MUST NOT | R25, R11, G10 | Rest is the cost of every worker. | Analysis: uptime = 1 ÷ (1 + 1 ÷ f), and the best bed factor f is 8. Unit: a simulated Lv50 worker on a Starlit Bed over 10 cycles works 88.9% ± 0.1% of the time. Fails if: the uptime is above 89.0%. | stamina | agreed |
| CD-BAL-04 | The Wheel Driver, Cranker, Tailwind and Fan Catalyst handlers MUST NOT push a machine past Create's own maximum for that machine. | MUST NOT | R25, RES2, RES5 | These roles are exempt from the output limits because the machine caps their output. | GameTest: a worked small wheel gives at most 8 RPM, a large wheel 4 RPM, a hand crank 32 RPM and a windmill 16 RPM, and a worked fan keeps Create's processing time. Fails if: any machine exceeds its Create maximum. | target handler (Create) | agreed |
| CD-BAL-05 | With the default role files, one Telekinesis or Magnet worker MUST move at most 64 items per second. | MUST | R25, R27, R31 | Item transfer stays at the approved rates. | Analysis: ceil(64 × 100 ÷ 100) = 64. Fails if: a default role file allows more than 64 items per second per worker. | role registry | agreed |
| CD-BAL-06 | Dynamics MUST NOT raise Cobblemon's limit of 16 Pokémon per pasture. | MUST NOT | R25, RES1 | The pasture cap limits each fleet. | GameTest: with the default Cobblemon config, try to pasture a 17th Pokémon in a pasture in Work Mode. Fails if: Cobblemon accepts it. | pasture adapter | agreed |
| CD-BAL-07 | With the default role files, the Crystal Tender MUST NOT give a budding block more extra random ticks than one AE2 Growth Accelerator gives. | MUST NOT | R25, RES3 | A powered accelerator array stays the better choice. | Analysis: 1 extra random tick per 10 ticks equals AE2's default accelerator interval. Fails if: a default role file sets a shorter interval. | role registry | agreed |

### UI: displays, messages and commands

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-UI-01 | When a player opens a pasture's panel, the client MUST show a Work Mode button directly above the Recall button. | MUST | R1, R35, ADR 0001, G1 | The approver wants the toggle in the pasture's interface. | Demo: open a pasture. Fails if: the button is missing, or overlaps another control. | client | agreed |
| CD-UI-02 | The client MUST label the Work Mode button with the server's current state, `"Work Mode: On"` or `"Work Mode: Off"`, within 20 ticks of the panel opening. | MUST | R1, R24 | The button shows the real state. | Demo: open a pasture that is on and one that is off. Fails if: a label is wrong, or takes more than 20 ticks to show. | client | agreed |
| CD-UI-03 | While the viewer is neither the placer nor an operator, the client MUST show the Work Mode button disabled, with the tooltip `"Only the pasture's owner can change Work Mode"`. | MUST | R24, R39 | Other players see why they cannot toggle. | Demo: a second player opens the pasture. Fails if: the button is enabled, or the tooltip is missing. | client | agreed |
| CD-UI-04 | While a pasture panel is open, the client MUST show a Work Stamina bar and a state icon beside each Pokémon in the pasture list. | MUST | R22, G7 | The approver wants stamina in the panel. | Demo: a pasture with 4 workers in mixed states. Fails if: a row lacks its bar or its icon. | client | agreed |
| CD-UI-05 | While a pasture panel is open, the server MUST send that pasture's Work Stamina values every 20 ticks. | MUST | R22, R37, limits sweep | The panel stays current. The `pasture_panel_state` payload carries the values. | Unit: sends at ticks 0, 20 and 40, and none at tick 19. Fails if: an interval is not 20 ticks. | stamina | agreed |
| CD-UI-06 | Only while a player holds the Work Leash, the client MUST show a Work Stamina bar and a state icon over each worker within 16 blocks. | MUST | R22, R36, limits sweep, G7 | Holding the work tool shows work information. | Demo: workers at 16.0 and 16.1 blocks, then switch the held item. Fails if: the 16.0 bar is missing, the 16.1 bar shows, or bars stay after the switch. | client | agreed |
| CD-UI-07 | The client MUST show the Idle icon for Idle, the Traveling icon for Traveling, the Working icon for Working, the Standby icon for Standby, the Tired icon for ToBed and RestingInPlace, the Sleeping icon for Sleeping, and the Healing icon for Healing. | MUST | R22, R38, G7, REV1 | Each state reads at a glance, and seven icons cover all eight states. | Inspect: seven icon textures. Demo: a worker in each of the eight states. Fails if: a state shows the wrong icon or no icon, or two icons look alike. | client | agreed |
| CD-UI-08 | The client SHOULD make the seven state icons differ in shape as well as color. | SHOULD | R38, quality sweep | Color-blind players read the icons too. Acceptable to rely on color for a state whose name also shows as text. | Demo: view the icons through a grayscale filter. Fails if: two icons look the same. | client | agreed |
| CD-UI-09 | While a player holds the Work Leash, the client MUST outline the targets of that player's bound workers. | MUST | R24 | Players see what each worker owns. | Demo: a player with two bound workers holds the leash. Fails if: a target is not outlined, or another player's target is. | client | agreed |
| CD-UI-10 | When a player hovers over a panel stamina bar, the client SHOULD show the Work Stamina percentage and the state name. | SHOULD | quality sweep | The bar alone is imprecise. Acceptable to drop if Cobblemon's list leaves no room for a tooltip. | Demo: hover over a bar at 42%. Fails if: the tooltip does not show 42% and the state name. | client | agreed |
| CD-UI-11 | The client MUST label the stat `"Work Stamina"` everywhere it shows the stat. | MUST | RES1 | Cobblemon already uses stamina for riding. | Inspect: every lang value for the stat reads "Work Stamina". Fails if: a value reads only "Stamina". | client | agreed |
| CD-UI-12 | The client MUST take every player-facing string from a lang key, with `en_us` complete. | MUST | R9, G9, quality sweep | Translators can localize a public release. | Inspect: UI code holds no `Component.literal` call, and every key resolves in `en_us`. Fails if: either check finds a gap. | client | agreed |
| CD-UI-13 | If the pasture-list mixin fails to apply, then the client MUST keep the Work Mode button and the head bars working and log one warning. | MUST | RES1, failure sweep | A Cobblemon update must not take down every display. | Unit: start the client with the mixin's applied flag set to false. Fails if: the button or the head bars fail, or the game crashes. | client | agreed |
| CD-UI-14 | Dynamics MUST give operators the command `/cobblemondynamics claims`, which lists each claim and each leash binding with its target, worker and kind. | MUST | R24, quality sweep | Operators find stuck machines. | GameTest: run the command with a work claim, a bed claim, a heal claim and a leash binding. Fails if: the output lacks an entry or a field. | work engine | agreed |
| CD-UI-15 | Dynamics MUST give operators the command `/cobblemondynamics stamina`, which shows a Pokémon's Work Stamina, state and recovery rule. | MUST | R24, quality sweep | Operators check the stamina math in play. | GameTest: run the command on a Lv50 healer at 40%. Fails if: the output lacks the value, the state, or "healer rule". | stamina | agreed |
| CD-UI-16 | Dynamics MUST list every item of the mod in its own creative mode tab `cobblemon_dynamics:main`, titled "Cobblemon Dynamics" with the Work Leash as its icon, in this order: the Work Leash, the four beds by tier and the Charge Cell, and in no vanilla tab. | MUST | R61 | The approver's ruling of 2026-11-10: the approver did not find the items in the creative menu, and one tab holds them all in one place. | GameTest: build the tab and the vanilla Tools and Utilities and Functional Blocks tabs. Fails if: the tab is missing, its title or icon differs, it misses an item that the mod registers or lists the items in another order, or one of the vanilla tabs lists an item of the mod. | content | agreed |
| CD-UI-17 | Dynamics MUST give operators the command `/cobblemondynamics unbind <pos>`, which clears every leash binding whose target or Telekinesis source is the block at that position in the operator's level, or the canonical block that the Work Leash finds from it, and sends each binder the chat message of CD-LSH-13 with the reason "an operator cleared it". | MUST | R62 | The approver's ruling of 2026-11-10, after the multiplayer audit: a player who binds a Pokémon in another player's pasture to that player's block and then leaves the server for good reserves the block forever, because Cobblemon 1.8.4 lets only a Pokémon's owner take it out of a pasture. | Demo: an operator binds a Charmander to a furnace and runs `/cobblemondynamics unbind` with the furnace's position. Fails if: the binding remains, `/cobblemondynamics claims` still lists it, the reply does not name the Charmander, or the chat lacks "Charmander lost its work binding: an operator cleared it". | work engine | agreed |

### CMP: compatibility

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-CMP-01 | Where Cobbleworkers is loaded, the compat component MUST set `cobbleworkers.resting` on each Pokémon in a pasture in Work Mode within 20 ticks. | MUST | R20, RES4, BM1, ADR 0009, G8 | Cobbleworkers skips resting Pokémon, so it stops re-pathing workers. | Unit: a fake `persistentData` gets the flag within 20 ticks of Work Mode turning on. Demo: with Cobbleworkers 2.0.7, a Fire worker stays at its furnace for 1,200 ticks. Fails if: the flag is missing, or Cobbleworkers moves the worker. | compat | agreed |
| CD-CMP-02 | The compat component MUST clear `cobbleworkers.resting` only on Pokémon where Dynamics set it. | MUST | R20, misuse sweep | A player's own rest choice survives. | Unit: after Work Mode turns off, a flag that the player set stays, and a flag that Dynamics set is cleared. Fails if: either result is wrong. | compat | agreed |
| CD-CMP-03 | Where Cobblebase is loaded, the compat component MUST stop Cobblebase's pasture tick for pastures in Work Mode. | MUST | R20, RES4, BM2, ADR 0009, G8 | Cobblebase freezes and teleports pastured Pokémon. | Demo: with Cobblebase 2.0.1, a Water worker beside a wheel on dry land runs for 1,200 ticks. Fails if: Cobblebase teleports it into water or freezes it. | compat | agreed |
| CD-CMP-04 | The compat component MUST NOT change Cobblebase's behaviour for pastures that are not in Work Mode. | MUST NOT | R20 | Other pastures keep their jobs. | Demo: a Cobblebase job in a pasture that is off. Fails if: the job stops. | compat | agreed |
| CD-CMP-05 | If the Cobblebase mixin fails to apply, then the compat component MUST log one warning and let the game continue. | MUST | RES4, failure sweep | Cobblebase broke on Cobblemon 1.8 once already. | Demo: run with a Cobblebase version that lacks the target method. Fails if: the game crashes, or the warning repeats. | compat | agreed |
| CD-CMP-06 | Where Cobbleworkers is not loaded, the compat component MUST load no Cobbleworkers compat class. | MUST | failure sweep, REV1 | A class that refers to a missing mod crashes the game. | GameTest: a server with Cobblebase and without Cobbleworkers starts. Fails if: an error names a Cobbleworkers compat class. | compat | agreed |
| CD-CMP-07 | Where Cobblebase is not loaded, the compat component MUST load no Cobblebase compat class. | MUST | failure sweep, REV1 | A class that refers to a missing mod crashes the game. | GameTest: a server with Cobbleworkers and without Cobblebase starts. Fails if: an error names a Cobblebase compat class. | compat | agreed |

### SEC: misuse

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-SEC-01 | If a player who is neither the placer nor an operator sends a Work Mode request, then the server MUST reject it and leave Work Mode unchanged. | MUST | R24, misuse sweep, actor × action sweep | Strangers must not stop a base. | GameTest: a player who is not the owner requests off. Fails if: Work Mode changes. | pasture adapter | agreed |
| CD-SEC-02 | If a pasture has no recorded placer, then the server MUST accept Work Mode requests from operators only. | MUST | R39, misuse sweep | An ownerless pasture has no placer to trust. | GameTest: clear the pasture's `ownerId`, then a regular player requests on, then an operator does. Fails if: the regular request changes Work Mode, or the operator's request does not. | pasture adapter | agreed |
| CD-SEC-03 | If a player sends a Work Mode request within 10 ticks of that player's last accepted request, then the server MUST ignore it. | MUST | R24, misuse sweep, limits sweep | Request spam must not flood the server. | Unit: a request 9 ticks after the last accepted one is ignored, and one 10 ticks after is applied. Fails if: either result is wrong. | pasture adapter | agreed |
| CD-SEC-04 | If the requesting player is more than 10 blocks from the pasture or in another dimension, then the server MUST reject the Work Mode request. | MUST | RES1, misuse sweep, limits sweep | Cobblemon links a player to a pasture only within 10 blocks. | GameTest: requests from 10.0 blocks, from 10.1 blocks and from the Nether. Fails if: the 10.0 request fails, or another request succeeds. | pasture adapter | agreed |
| CD-SEC-05 | The Work Mode payload MUST carry the requested state, so a repeated payload gives the same result. | MUST | R50, failure sweep | A duplicated packet must not flip the setting back. | Unit: two identical "on" payloads 10 ticks apart. Fails if: Work Mode reads off. | pasture adapter | agreed |
| CD-SEC-06 | If two Work Mode requests for one pasture arrive in the same tick, then the server MUST apply them in arrival order and send every viewer the final state. | MUST | failure sweep | Two operators must see the same result. | Unit: on, then off, in one tick from two operators. Fails if: the final state is on, or a viewer shows on. | pasture adapter | agreed |
| CD-SEC-07 | If a player uses the Work Leash on a Pokémon that the player does not own, then the server MUST refuse the selection. | MUST | R3, misuse sweep | Players must not command other players' Pokémon or wild Pokémon. | GameTest: player B uses the leash on player A's pastured Pokémon and on a wild Pokémon. Fails if: either Pokémon is selected. | content | agreed |
| CD-SEC-08 | The server MUST evaluate every bind rule from its own state. | MUST | misuse sweep | A modified client must not bind outside the rules. | Unit: a bind call that names a Pokémon with no server-side selection on the player's leash. Fails if: the bind succeeds. | work engine | agreed |
| CD-SEC-09 | If a player below permission level 2 runs a `/cobblemondynamics` command, then the server MUST refuse it. | MUST | R39, misuse sweep | The claim list names other players' Pokémon. | GameTest: a level-0 player runs both commands. Fails if: either command runs. | work engine | agreed |
| CD-SEC-10 | If a player sneak-right-clicks a wheel, a crank or a fan with the Work Leash while that target is not driven, or while the player is not its binder, its placer or an operator, then the server MUST leave its direction and mode unchanged. | MUST | REV1, misuse sweep, actor × action sweep | A stranger must not reverse a build, and Create destroys a generator that turns against its network. | GameTest: a second player clicks a worked wheel, a worked crank and a worked fan, and then the binder clicks a wheel that no worker drives. Fails if: any direction or mode changes. | work engine | agreed |
| CD-SEC-11 | If another mod cancels the player's right-click on a block, then the Work Leash MUST NOT bind that block. | MUST NOT | RES3, misuse sweep | Protection mods cancel right-clicks on blocks that the player may not touch, so the leash obeys them. | GameTest: an event listener cancels right-clicks on a chest, and the player clicks the chest with a selected Psychic-type. Fails if: a pending source or a binding exists. | content | agreed |

### PERF: performance

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-PERF-01 | While 64 workers are Working, Dynamics MUST use at most 1.0 ms of server time per tick on average. | MUST | R24, quality sweep | A large base must not lag the server. | Analysis: a spark profile of the reference test world (8 pastures, 64 Working workers) over 6,000 ticks. Fails if: the mean Dynamics time is above 1.0 ms per tick. | work engine | agreed |
| CD-PERF-02 | While 64 workers are Working, Dynamics MUST keep its 95th-percentile server time at or under 2.0 ms per tick. | MUST | R24, quality sweep | Spikes lag players even when the mean is low. | Analysis: the spark run of CD-PERF-01. Fails if: the p95 is above 2.0 ms per tick. | work engine | agreed |
| CD-PERF-03 | If no worker of a pasture is eligible for the Crystal Tender role, then the work engine MUST NOT iterate block positions in that pasture's scan. | MUST NOT | R24, quality sweep | Machines and beds are block entities, which each chunk lists. | Unit: a scan with only Fire-type workers. Fails if: the scan reads one block state or more. | work engine | agreed |
| CD-PERF-04 | The effects component MUST keep at most one work focus entity for each claim. | MUST | quality sweep | Leaked anchors cost memory and bandwidth. | GameTest: create and release 10 claims, 5 times. Fails if: the focus entity count ever exceeds the claim count. | effects | agreed |
| CD-PERF-05 | Dynamics MUST NOT log at INFO level or above from per-tick code. | MUST NOT | quality sweep | Log spam hides real errors. | Analysis: run the reference test world for 6,000 ticks and count Dynamics log lines after startup. Fails if: a line is not a once-per-cause warning. | work engine | agreed |

### CFG: configuration and datapacks

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-CFG-01 | Dynamics MUST read its server settings from the NeoForge server config `cobblemon_dynamics-server.toml`. | MUST | R9 | Server owners tune balance without datapacks. | Inspect: after the first start of a new server, its `config/` folder holds the file with every key in this section. Fails if: the file or a key is missing. | build | agreed |
| CD-CFG-02 | The server config MUST accept a horizontal and a vertical work radius from 2 to 32 blocks each, with defaults of 8 and 6. | MUST | R24, REV1, limits sweep | 32 is Cobblemon's default roam limit. | Unit: for each radius, 2 and 32 are accepted, and 1 and 33 are rejected. The defaults read 8 and 6. Fails if: any result is wrong. | build | agreed |
| CD-CFG-03 | The Generator role file MUST hold the FE rate at level 1 (default 10), the FE rate at level 100 (default 5,000), the rarity multiplier (default 2) and the rarity labels (default `legendary` and `mythical`). | MUST | R5, REV1, ADR 0006 | Role-specific numbers live in the role file, so packs tune power output with a datapack. | GameTest: a datapack sets the level-100 rate to 2,500, then `/reload`. Fails if: a Lv100 Generator does not give 2,500 FE/t. | role registry | agreed |
| CD-CFG-04 | The server config MUST hold the work capacity per level (default 3,600 ticks), the four bed factors (defaults 2, 3, 4 and 8) and the healer recovery times at level 1 and level 100 (defaults 1,200 and 120 min). | MUST | R10, R11, R14 | Packs tune stamina balance. | Unit: set the capacity to 7,200 ticks per level. Fails if: a Lv1 worker is Tired before 7,200 ticks of work, or is not Tired after 7,201 ticks. | build | agreed |
| CD-CFG-05 | The server config MUST let operators disable each role by its id. | MUST | R9 | A pack drops a role that clashes with its balance. | GameTest: disable the Generator, then an Electric-type stands beside a battery for 400 ticks. Fails if: it claims the battery. | role registry | agreed |
| CD-CFG-06 | The client config MUST hold no work sound option, because work effects play no sound (CD-FX-09). | MUST | R21 | R21 made work sounds a client choice. The approver's play-test ruling (2026-11-10) makes all work silent, so the option would turn on nothing. | Unit: the client config has no `effects.workSounds` key. Fails if: the key exists. | client | agreed |
| CD-CFG-07 | The role registry MUST load each role from `data/{namespace}/cobblemon_dynamics/roles/{role}.json`, with its eligible types, auto-assign flag, priority, range, handler, handler parameters and effect. | MUST | R24, REV1, ADR 0006 | Pack makers add or change roles without code. Role files hold no species lists. | GameTest: a datapack lets Water-types act as Stokers. Fails if: a Water-type does not claim a furnace after `/reload`. | role registry | agreed |
| CD-CFG-08 | When an operator runs `/reload`, the role registry MUST apply changed role files without a restart. | MUST | R24, ADR 0006 | Pack makers test changes in one session. | GameTest: change the Stoker's types, then run `/reload`. Fails if: the old types still apply. | role registry | agreed |
| CD-CFG-09 | If a role file fails to parse, then the role registry MUST skip that file and log one error that names it. | MUST | failure sweep, ADR 0006 | One bad file must not break every role. | GameTest: load a datapack with one malformed role file. Fails if: the game crashes, another role stops, or the error lacks the file name. | role registry | agreed |
| CD-CFG-10 | If a role file names a handler that is not registered, then the role registry MUST disable that role and log one line that names it. | MUST | R9, failure sweep, ADR 0006 | Create roles must switch off when Create is absent. | GameTest: start a server without Create. Fails if: a Create role is active, or the log shows more than one line per disabled role. | role registry | agreed |
| CD-CFG-11 | The server config MUST hold the scan interval (default 40 ticks) and the block-check budget per pasture per tick (default 512). | MUST | R24, REV1 | Server owners trade scan speed for server time. | Unit: set the interval to 80 and the budget to 256. Fails if: passes start more often than every 80 ticks, or a tick checks more than 256 positions. | build | agreed |
| CD-CFG-12 | The server config MUST hold the travel timeout (default 600 ticks) and the wait after a missed arrival (default 6,000 ticks). | MUST | R24, REV1 | Packs with slow pathing need longer timeouts. | Unit: set the timeout to 1,200 ticks. Fails if: an auto-assigned worker's work claim on an unreachable target is not held at tick 1,199 after the claim, or is still held at tick 1,201. | build | agreed |
| CD-CFG-13 | The work engine MUST NOT cap a role's output below the values in its role file. | MUST NOT | R5, REV1 | A pack that raises a rate gets that rate, and no hidden cap overrides the role file. | GameTest: a datapack sets the Generator's level-100 rate to 20,000. Fails if: a Lv100 legendary Generator gives less than 40,000 FE/t. | work engine | agreed |
| CD-CFG-14 | The work engine MUST clamp each configured work radius to Cobblemon's `pastureMaxWanderDistance`. | MUST | RES1, REV1, limits sweep | Cobblemon teleports or recalls a pastured Pokémon that leaves its roam box. | Unit: with `pastureMaxWanderDistance` set to 20, a configured radius of 32 acts as 20, and a configured radius of 19 stays 19. Fails if: either result is wrong. | work engine | agreed |
| CD-CFG-15 | The server config MUST hold the Standby move delay, with a default of 200 ticks. | MUST | R44 | Packs tune how long a worker waits before it moves. | Unit: set the delay to 400 ticks. Fails if: an auto-assigned worker moves after 399 ticks in Standby, or stays after a scan at 400 ticks. | build | agreed |

### ART: art (Phase 2)

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-ART-01 | Dynamics MUST ship an original block model, an original block texture and an original item model for each of the four beds and for the Charge Cell. | MUST | R32, R60, G11 | The beds and the Charge Cell are new blocks that need their own look. | Inspect: the jar holds a block model, a block texture and an item model for each bed and for the Charge Cell. Fails if: an asset is missing. | content | agreed |
| CD-ART-02 | Each bed's art MUST follow its tier concept: the Hay Bed a round straw nest, the Wool Bed a round wool cushion on a low wooden frame, the Plush Bed a thick velvet cushion with gold trim, and the Starlit Bed a midnight-blue cushion with a gold star pattern, a faint glow and an occasional sparkle. | MUST | R32, R33 | The approver approved these concepts. | Demo: the approver compares each bed with its concept on the screenshot review page. Fails if: the approver rejects a bed. | content | agreed |
| CD-ART-03 | Dynamics MUST ship an original Work Leash item texture that shows a lead with a red-and-white clasp. | MUST | R32, R33 | The leash is a new item. | Demo: the approver reviews the texture on the screenshot review page. Fails if: the texture is missing, or the approver rejects it. | content | agreed |
| CD-ART-04 | Dynamics MUST ship original UI sprites for the Work Mode button in 4 states (on, off, disabled, hover), the Work Stamina bar, and the 7 state icons. | MUST | R32, R33, R38, G7 | The panel and the head bars need their own sprites. | Inspect: the jar holds 4 button sprites, the bar sprites and 7 icon sprites. Demo: the approver reviews them. Fails if: a sprite is missing, or the approver rejects one. | content | agreed |
| CD-ART-05 | Dynamics MUST ship the approver's logo, scaled from `art/logo/logo-source-1254.png`, as a `logo.png` of 256 × 256 pixels and as a project-page icon of 512 × 512 pixels. | MUST | R32, R53 | The approver supplied the logo, and the mod list and both release pages show it. | Inspect: the jar's `logo.png` is 256 × 256 pixels and matches `art/logo/logo-256.png`, and `art/logo/icon-512.png` is 512 × 512 pixels. Fails if: a size differs, a file is missing, or the jar's logo differs from `art/logo/logo-256.png`. | content | agreed |
| CD-ART-06 | Every block and item texture MUST be 16 × 16 pixels. | MUST | R32, limits sweep | The approver chose vanilla 16 × 16 style. | Inspect: a script reads every PNG under `assets/cobblemon_dynamics/textures/block/` and `assets/cobblemon_dynamics/textures/item/`. Fails if: a texture is not 16 × 16, for example 17 × 16 or 32 × 32. | content | agreed |
| CD-ART-07 | Every block model MUST be a Minecraft JSON block model that Blockbench opens. | MUST | R32 | Contributors edit the models in Blockbench. | Inspect: each model loads in the game with no model warning. Demo: each model opens in Blockbench. Fails if: either check fails for a model. | content | agreed |
| CD-ART-08 | The repository MUST keep the source file of every asset under `art/`. | MUST | R32 | A contributor must be able to edit the art from its source. | Inspect: each shipped texture and model has a source file under `art/`. Fails if: a source file is missing. | content | agreed |
| CD-ART-09 | The jar and the repository MUST NOT contain pixels copied from Mojang, Cobblemon, Create or AE2 assets. | MUST NOT | R32, RES1, RES2, RES3 | Cobblemon models carry a NonCommercial license and Create assets are All Rights Reserved. | Analysis: compare each texture with the textures of Minecraft 1.21.1, Cobblemon 1.8.4, Create 6.0.10 and AE2 19.2.19, tile by tile at 16 × 16. Fails if: any tile matches exactly. | content | agreed |
| CD-ART-10 | Before the public release, the approver MUST approve each asset on a screenshot review page. | MUST | R32 | The approver decides how the mod looks. | Inspect: the review record holds an approval for every asset in CD-ART-01 to CD-ART-05. Fails if: an asset lacks an approval. | content | agreed |
| CD-ART-11 | Phase 1 and Phase 2 builds MAY ship placeholder art. | MAY | R32, R33 | Testing does not wait for the art. When a private build has no placeholders, it ships the final art that exists. | Inspect: a Phase 1 build with a placeholder bed texture passes CI. Fails if: CI rejects a private build for placeholder art. | content | agreed |
| CD-ART-12 | The public release MUST NOT ship placeholder art. | MUST NOT | R32 | Public players get the approved art. | Inspect: every texture in the 1.0.0 jar matches its approved file under `art/`. Fails if: a placeholder ships. | content | agreed |
| CD-ART-13 | Every asset MUST carry the MPL-2.0 license. | MUST | R8, R32 | One license covers the code and the art. | Inspect: the repository `LICENSE` covers `art/` and `assets/`, and no asset file states another license. Fails if: an asset states another license. | content | agreed |
| CD-ART-14 | The mod metadata MUST set `logoBlur=false`. | MUST | R53 | The logo is pixel art, and blur smears it when the mod list scales it. | Inspect: `neoforge.mods.toml` in the jar holds `logoBlur=false`. Fails if: the key is missing or true. | build | agreed |

### REL: CI, release and docs

| ID | Requirement | Level | Source | Rationale | Acceptance test | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CD-REL-01 | The repository MUST run the build, the unit tests and the GameTests in GitHub Actions on every push and pull request to `main`. | MUST | R9, R24 | Every change is tested before it merges. | Demo: open a pull request with a failing unit test. Fails if: its check passes. | build | agreed |
| CD-REL-02 | The repository MUST run CodeQL and dependency review from `.github/workflows/security.yml`. | MUST | R24 | Public code gets security scans on every change. | Inspect: the workflow runs both jobs. Fails if: either job is missing. | build | agreed |
| CD-REL-03 | The repository MUST keep a `.github/dependabot.yml` for the Gradle and GitHub Actions ecosystems. | MUST | R24 | Dependencies stay current. | Inspect: the file lists both ecosystems. Fails if: either is missing. | build | agreed |
| CD-REL-04 | When a maintainer pushes a `v*` tag with no pre-release suffix, the release workflow MUST publish the jar to Modrinth and CurseForge. | MUST | R9, G9, R56 | The approver ruled a public release. | Demo: push the tag `v1.0.0`. Fails if: either site lacks version `1.0.0+mc1.21.1` after the workflow ends. | build | agreed |
| CD-REL-05 | The build MUST version releases as `MAJOR.MINOR.PATCH+mc1.21.1`. | MUST | R24 | Players see the game version in the version name. | Inspect: the mod version in the v1.0.0 jar reads `1.0.0+mc1.21.1`. Fails if: it has another form. | build | agreed |
| CD-REL-06 | The repository MUST license its code under MPL-2.0. | MUST | R8, G9 | The approver chose MPL-2.0. | Inspect: `LICENSE` holds MPL-2.0, and `neoforge.mods.toml` declares `license = "MPL-2.0"`. Fails if: either differs. | build | agreed |
| CD-REL-07 | The jar MUST NOT contain copies of Cobblemon assets. | MUST NOT | RES1, misuse sweep | Cobblemon models carry a NonCommercial license, so the mod refers to them by id. | Inspect: list the jar. Fails if: a file sits under `assets/cobblemon/`. | build | agreed |
| CD-REL-08 | The repository MUST hold a player guide at `docs/player-guide.md` that covers Work Mode, the Work Leash, Work Stamina, beds, healers, the Charge Cell and every role. | MUST | R9, R49, R60 | Public players need a guide. | Inspect: at the v1.0.0 tag, the guide has a section for each item. Fails if: a section is missing. | build | agreed |
| CD-REL-09 | The repository MUST hold a manual checklist at `docs/testing/manual-checklist.md` that lists every `Demo:` test in this sheet. | MUST | R24, R49 | Demo tests need a repeatable run. | Inspect: at the v1.0.0 tag, compare the checklist with the Demo tests in this sheet. Fails if: a Demo test is missing. | build | agreed |
| CD-REL-10 | The GameTest run MUST load Cobblemon 1.8.4, Create 6.0.10 and AE2 19.2.19. | MUST | R24, RES3 | Integration tests need the real mods. | Inspect: the CI log of a GameTest run lists the three mods at these versions. Fails if: a mod is missing or at another version. | build | agreed |
| CD-REL-11 | The README SHOULD state that Cobblemon Dynamics is not affiliated with Cobblemon, The Pokémon Company, Create or Applied Energistics 2. | SHOULD | R9 | A clear notice protects a fan project. Acceptable to put the notice on the Modrinth and CurseForge pages instead. | Inspect: the README and both release pages. Fails if: none of them holds the notice. | build | agreed |
| CD-REL-12 | When a maintainer pushes a `v*` tag with a pre-release suffix, the release workflow MUST NOT publish to Modrinth or CurseForge. | MUST NOT | R16, R56 | Phase 1 and Phase 2 builds are private tests. | Demo: push `v1.0.0-alpha.1`, then `v1.0.0-beta.1`. Fails if: either version appears on either site. | build | agreed |
| CD-REL-13 | The project MUST NOT merge Phase 2 work before every Phase 1 row at level `MUST` or `MUST NOT` passes on a named commit and the approver has play-tested a Phase 1 build. | MUST NOT | R16, R33 | The approver ruled a v1 test before v2. | Inspect: the M5 milestone records a passing run on a named commit and the approver's play-test note, both dated before the first M6 merge. Fails if: either record is missing, or is dated after that merge. | build | agreed |
| CD-REL-14 | The project MUST NOT publish 1.0.0 before every row at level `MUST` or `MUST NOT` in this sheet passes on a named commit and the approver has play-tested a Phase 2 build. | MUST NOT | R16, R33 | The approver ruled a v2 test before the public release. | Inspect: the M8 milestone records a passing run on the release commit and the approver's play-test note, both dated before the `v1.0.0` tag. Fails if: either record is missing, or is dated after the tag. | build | agreed |
| CD-REL-15 | When 1.0.0 is published, the approver's modpack MUST include it. | MUST | R16, G9 | The approver ruled that Phase 3 ends with the mod in the pack. | Inspect: the pack's mod list at its next release names Cobblemon Dynamics 1.0.0. Fails if: the mod is missing from that release. | build | agreed |

## Sweep record

| Sweep | Rows produced |
| --- | --- |
| Actor × action | Placer: CD-WMD-03. Operator: CD-WMD-04, CD-UI-14, CD-UI-15. Binder: CD-WHL-05, CD-CRK-06, CD-FAN-10. Other player: CD-SEC-01, CD-SEC-07, CD-SEC-09, CD-SEC-10. Pokémon owner: CD-LSH-01, CD-LSH-03, CD-LSH-10, CD-LSH-17, CD-LSH-18, CD-LSH-25, CD-LSH-26. Wild Pokémon: CD-ASG-01, CD-AI-06, CD-BED-13. Scan job: CD-ASG-03, CD-ASG-06. Pack maker: CD-CFG-03, CD-CFG-07, CD-CFG-08, CD-CFG-13. Other mods: CD-CMP-01, CD-CMP-03, CD-SEC-11. Approver: CD-ART-10, CD-REL-13, CD-REL-14. |
| Entity lifecycle | Work Mode flag: CD-WMD-01, CD-WMD-02, CD-WMD-03 (delete: the flag goes with the block entity). Claim: CD-CLM-01, CD-UI-14, CD-CLM-08. Leash binding: CD-LSH-03, CD-LSH-07, CD-LSH-11, CD-LSH-08, CD-LSH-10, CD-WMD-07, CD-LSH-22, CD-LSH-23, CD-LSH-24, CD-LSH-25, CD-LSH-26, CD-LSH-27. Telekinesis binding: CD-LSH-17, CD-LSH-18, CD-LSH-19, CD-TLK-11. Work Stamina: CD-STA-05, CD-STA-04, CD-STA-06 (delete: the data goes with the Pokémon). Bed: CD-BED-01, CD-BED-11. Work focus entity: CD-FX-04, CD-FX-07. Role: CD-CFG-07, CD-CFG-08, CD-CFG-05. Fan mode: CD-FAN-09, CD-FAN-10, CD-FAN-11. Windmill bonus: CD-TLW-03, CD-TLW-06, CD-TLW-09. Art asset: CD-ART-01, CD-ART-08, CD-ART-12. |
| State × event | CD-ASG-11, CD-ASG-12, CD-ASG-17, CD-ASG-18, CD-WMD-05, CD-AI-03, CD-AI-04, CD-LSH-12, CD-STA-09, CD-STA-14, CD-BED-11, CD-HEAL-11, CD-HEAL-17, CD-ASG-19, CD-ASG-20. |
| Failure | CD-ASG-13, CD-ASG-15, CD-ASG-17, CD-CLM-03, CD-CLM-07, CD-CLM-08, CD-LSH-09, CD-AI-04, CD-AI-07, CD-AI-08, CD-FX-11, CD-STA-11, CD-STA-12, CD-BED-11, CD-BED-14, CD-WHL-07, CD-WHL-10, CD-BRN-07, CD-CRK-08, CD-MUS-06, CD-TLK-07, CD-MAG-06, CD-FAN-04, CD-FAN-11, CD-FAN-17, CD-TLW-10, CD-UI-13, CD-CMP-05, CD-CMP-06, CD-CMP-07, CD-CFG-09, CD-CFG-10, CD-SEC-05, CD-SEC-06. |
| Misuse | CD-SEC-01 to CD-SEC-11, CD-LSH-05, CD-TLK-10, CD-BRN-02, CD-BRF-05, CD-CMP-02, CD-STA-12, CD-REL-07, CD-ART-09, CD-LSH-26, CD-TLK-12. |
| Limits | CD-PLAT-01, CD-PLAT-02, CD-PLAT-03, CD-PLAT-05, CD-ASG-06, CD-ASG-07, CD-ASG-08, CD-ASG-10, CD-ASG-13, CD-ASG-14, CD-ASG-17, CD-LSH-05, CD-LSH-19, CD-FX-01, CD-FX-08, CD-STA-09, CD-STA-15, CD-BED-07, CD-HEAL-02, CD-HEAL-08, CD-HEAL-10, CD-SUP-04, CD-CRK-02, CD-CRY-02, CD-BRW-02, CD-PMP-06, CD-MUS-04, CD-TLK-03, CD-TLK-09, CD-MAG-03, CD-MAG-04, CD-TLW-04, CD-UI-05, CD-UI-06, CD-SEC-03, CD-SEC-04, CD-CFG-02, CD-CFG-14, CD-ART-06, CD-ASG-19, CD-TLK-12, CD-CFG-15. |
| Quality | Performance: CD-PERF-01 to CD-PERF-05, CD-ASG-07, CD-MAG-08. Balance: CD-BAL-01 to CD-BAL-07. Observability: CD-UI-14, CD-UI-15. Accessibility: CD-UI-08, CD-UI-10, CD-UI-12. Compatibility: CD-STK-01, CD-GEN-01, CD-TLK-08, CD-CMP-01 to CD-CMP-07. |

## Trace table

| Input item | Row IDs, or out of scope |
| --- | --- |
| G1 | CD-WMD-01, CD-WMD-03, CD-ASG-03, CD-UI-01 |
| G2 | CD-WHL-02, CD-GEN-02, CD-STK-02 |
| G3 | CD-WHL-01, CD-GEN-01, CD-STK-01, CD-SUP-01, CD-CRK-01, CD-CRY-01, CD-BRF-01, CD-BRW-01, CD-PMP-01, CD-MUS-01, CD-HEAL-01, CD-TLK-01, CD-MAG-01, CD-FAN-01, CD-TLW-01 |
| G4 | CD-CLM-01, CD-CLM-02, CD-ASG-01, CD-AI-06 |
| G5 | CD-LSH-01, CD-LSH-03, CD-CLM-05 |
| G6 | CD-STA-01, CD-BED-01, CD-BED-03, CD-HEAL-04, CD-HEAL-05 |
| G7 | CD-UI-04, CD-UI-06, CD-UI-07, CD-ART-04 |
| G8 | CD-CMP-01, CD-CMP-03, CD-PLAT-08 |
| G9 | CD-REL-04, CD-REL-06, CD-REL-15, CD-UI-12 |
| G10 | CD-BAL-01, CD-BAL-02, CD-BAL-03 |
| G11 | CD-ART-01, CD-ART-06, CD-ART-10 |
| R1 | CD-WMD-01, CD-WMD-02, CD-WMD-03, CD-WMD-06, CD-UI-01 |
| R2 | CD-CLM-01, CD-ASG-01, CD-AI-06, CD-BED-13, CD-HEAL-09 |
| R3 | CD-LSH-01, CD-LSH-02, CD-LSH-03, CD-LSH-10, CD-CLM-05, CD-SEC-07 |
| R4 | CD-WHL-01, CD-WHL-02, CD-AI-05, CD-FX-04 |
| R5 | CD-GEN-02, CD-GEN-03, CD-GEN-07, CD-CFG-03, CD-CFG-13 |
| R6 | CD-STK-01, CD-STK-02, CD-STK-03, CD-STK-07 |
| R7 | CD-PLAT-01, CD-PLAT-05 |
| R8 | CD-REL-06, CD-ART-13 |
| R9 | CD-REL-04, CD-REL-08, CD-UI-12, CD-CFG-01, CD-CFG-05, CD-PLAT-05 |
| R10 | CD-STA-01, CD-STA-02, CD-STA-06, CD-STA-07, CD-STA-09, CD-STA-10 |
| R11 | CD-BED-01, CD-BED-02, CD-BED-03, CD-BED-04, CD-BAL-03 |
| R12 | CD-BED-05, CD-BED-07, CD-BED-09, CD-BED-10, CD-FX-10 |
| R13 | CD-HEAL-01, CD-HEAL-04, CD-HEAL-05, CD-HEAL-06, CD-HEAL-07, CD-HEAL-08, CD-HEAL-15, CD-HEAL-16, CD-HEAL-17 |
| R14 | CD-HEAL-12, CD-BED-03, CD-CFG-04 |
| R15 | CD-WHL-01, CD-PMP-01, CD-GEN-01, CD-STK-01, CD-BRF-01, CD-SUP-01, CD-CRK-01, CD-CRY-01, CD-BRW-01, CD-HEAL-01, CD-MUS-01 |
| R16 | CD-TLK-01, CD-MAG-01, CD-FAN-01, CD-TLW-01, CD-REL-12, CD-REL-13, CD-REL-14, CD-REL-15 |
| R17 | CD-WHL-04, CD-WHL-05, CD-WHL-06, CD-WHL-07 |
| R18 | CD-ASG-04, CD-MUS-02, CD-MUS-03, CD-MUS-04, CD-HEAL-13, CD-HEAL-14 |
| R19 | CD-PMP-02, CD-ASG-05 |
| R20 | CD-CMP-01, CD-CMP-02, CD-CMP-03, CD-CMP-04, CD-PLAT-08 |
| R21 | CD-FX-09, CD-CFG-06 |
| R22 | CD-UI-04, CD-UI-05, CD-UI-06, CD-UI-07, CD-STA-15 |
| R23 | CD-AI-01, CD-AI-02, CD-AI-03, CD-AI-04 |
| R24 | CD-ASG-06 to CD-ASG-16, CD-CLM-07, CD-STA-13, CD-UI-14, CD-PERF-01, CD-REL-01, CD-REL-05 |
| R25 | CD-BAL-01 to CD-BAL-07, CD-SUP-04, CD-TLW-03 |
| R26 | CD-SUP-01, CD-SUP-03, CD-SUP-04, CD-BRN-01, CD-BRN-05 |
| R27 | CD-TLK-01, CD-TLK-03, CD-TLK-05, CD-LSH-17, CD-LSH-18 |
| R28 | CD-FAN-05, CD-FAN-06, CD-FAN-09, CD-FAN-10 |
| R29 | CD-TLK-02, CD-MAG-02, CD-FAN-02, CD-TLW-02, CD-ASG-05 |
| R30 | CD-TLW-03, CD-TLW-04 |
| R31 | CD-MAG-01, CD-MAG-03, CD-MAG-04 |
| R32 | CD-ART-01 to CD-ART-13 |
| R33 | CD-TLK-04, CD-MAG-05, CD-FAN-03, CD-TLW-08, CD-ART-02, CD-REL-13 |
| R34 | CD-BED-01, CD-BED-02 |
| R35 | CD-UI-01 |
| R36 | CD-UI-06 |
| R37 | CD-UI-05 |
| R38 | CD-UI-07, CD-UI-08, CD-ART-04 |
| R39 | CD-WMD-04, CD-UI-03, CD-SEC-02, CD-SEC-09 |
| R40 | CD-WHL-04, CD-CRK-05 |
| R41 | CD-GEN-04 |
| R42 | CD-ASG-16 |
| R43 | CD-WHL-03, CD-ASG-19 |
| R44 | CD-ASG-19, CD-ASG-20, CD-CFG-15 |
| R45 | CD-ASG-12, CD-FAN-15 |
| R46 | CD-FX-03 |
| R47 | CD-BED-06 |
| R48 | CD-PLAT-07 |
| R49 | CD-REL-08, CD-REL-09 |
| R50 | CD-SEC-05 |
| R51 | CD-LSH-19 |
| R52 | CD-TLK-03, CD-MAG-03 |
| R53 | CD-ART-05, CD-ART-14 |
| R54 | CD-TLK-09, CD-TLK-12 |
| R55 | CD-LSH-16 |
| R56 | CD-REL-04, CD-REL-12 |
| R57 | CD-LSH-20, CD-LSH-21, CD-TLK-10, CD-FAN-04, CD-FAN-16 |
| R58 | CD-LSH-08, CD-LSH-22, CD-LSH-23, CD-LSH-24, CD-LSH-25, CD-LSH-26, CD-LSH-27 |
| R59 | CD-BRN-01 to CD-BRN-07, CD-SUP-02, CD-ASG-16 |
| R60 | CD-GEN-08 to CD-GEN-12, CD-REL-08, CD-ART-01 |
| R61 | CD-UI-16 |
| R62 | CD-UI-17 |
| RES1 | CD-PLAT-02, CD-AI-01, CD-AI-04, CD-AI-07, CD-AI-09, CD-FX-03, CD-HEAL-15, CD-UI-11, CD-REL-07, CD-CFG-14 |
| RES2 | CD-WHL-02, CD-WHL-09, CD-BRN-02, CD-CRK-02, CD-CRK-04 |
| RES3 | CD-PLAT-01, CD-GEN-04, CD-GEN-07, CD-STK-04, CD-CRY-02, CD-SEC-11 |
| RES4 | CD-CLM-03, CD-CMP-01, CD-CMP-03, CD-PLAT-08 |
| RES5 | CD-PMP-04, CD-PMP-06, CD-MUS-06, CD-TLK-07, CD-TLK-08, CD-MAG-05, CD-FAN-12, CD-FAN-13, CD-TLW-05, CD-TLW-06 |
| REV1 | CD-CLM-02, CD-CLM-09, CD-ASG-06, CD-ASG-17, CD-ASG-18, CD-AI-09, CD-HEAL-16, CD-HEAL-17, CD-SEC-10, CD-CFG-11 to CD-CFG-14 |
| ADR 0001 | CD-WMD-01, CD-WMD-03, CD-UI-01 |
| ADR 0002 | CD-PLAT-01, CD-PLAT-05 |
| ADR 0003 | CD-PLAT-08 |
| ADR 0004 | CD-AI-01, CD-AI-02 |
| ADR 0005 | CD-CLM-01, CD-CLM-02, CD-CLM-04, CD-CLM-07, CD-CLM-09 |
| ADR 0006 | CD-CFG-03, CD-CFG-07, CD-CFG-08, CD-CFG-09, CD-CFG-10 |
| ADR 0007 | CD-FX-04, CD-FX-05 |
| ADR 0008 | CD-STA-01, CD-STA-07, CD-HEAL-12 |
| ADR 0009 | CD-CMP-01, CD-CMP-03 |
| ADR 0010 | CD-BAL-01 to CD-BAL-07 |
| ADR 0011 | CD-TLK-01, CD-MAG-01, CD-FAN-01, CD-TLW-01, CD-LSH-17, CD-LSH-18, CD-REL-13 |
| ADR 0012 | CD-ART-01 to CD-ART-14 |
| BM1 | CD-CLM-03, CD-CMP-01 |
| BM2 | CD-CMP-03, CD-FX-03 |
| BM3 | CD-BAL-01 |

## Open questions

none: the approver answered Q1-Q8 on 2026-11-09.

## Checks

Output of `scripts/check_spec_sheet.py` (documentation-driven-design skill 1.2.1) on this file,
exit 0:

```text
docs/plans/2026-11-09-v1-spec-sheet.md: 33 requirements tables, 349 rows
  malformed row (cell count differs from the header): 0
  missing, malformed or duplicate ID: 0
  empty or placeholder cell: 0
  row with no acceptance test: 0
  acceptance test with no 'Fails if: <defect>': 0
  Requirement cell without exactly one keyword: 0
  Requirement cell with a lowercase keyword: 0
  Requirement cell with an ambiguous token: 0
  Level differs from the Requirement keyword: 0
  unknown Status value: 0
```

- Trace-table lines mapped: 94 of 94 (11 goals + 83 inputs + 0 assumptions)
- Sweeps recorded: 7 of 7
- Rows by level: 294 MUST, 50 MUST NOT, 4 SHOULD, 1 MAY
