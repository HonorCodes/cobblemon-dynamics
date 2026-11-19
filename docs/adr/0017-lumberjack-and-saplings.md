# ADR-0017: The Lumberjack, and saplings for the Cultivator

Status: Accepted (2026-11-15) by the coordinator under the approver's delegation, with the rulings (R81) on the design
decisions of the
[1.2 plan](../plans/2026-11-15-v1.2-new-roles-and-compat.md#design-decisions). Inputs R76 and R79 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) hold the rulings, RES9 and RES12 the research. The coordinator's
rulings on the review of the Lumberjack branch (R82, 2026-11-16) amend decisions 3, 4, 5, 7, 8, 9, 10 and 11, and the
ruling on the detection of Dynamic Trees (R89, 2026-11-17) amends decision 15. The approver's rulings of 2026-11-18
(R94) replace the rule of decision 10 that no sapling comes from nothing, and extend decision 14: see
[Amendment (1.2.1, R94)](#amendment-121-r94-a-full-replant-and-the-bugground-cycle).

## Context

The approver, 2026-11-15 (R76), on how a Bug-type fells a tree:

> cut the whole tree with the leaves, then replant, yes

And on the list of 1.2 items: "Lumberjack: make it leash only, then approved", and "N2 and N3 are approved". N2 adds
saplings to the Cultivator, so that a Ground-type and a bound Bug-type run a wood line.

The coordinator's rulings (R79):

- The Lumberjack is a Work Leash role only: no auto-assignment and no Quick Work.
- The binding goes to a tree and resolves to its trunk base cell.
- The worker fells the whole tree with that tree's own natural leaves: a real `LeavesBlock` whose `PERSISTENT` is false.
  The tree comes down also where it rises above the work area.
- It replants a sapling from the drops at once. The drops land beside the trunk for a Magnet. The binding then waits on
  the sapling until a grown tree stands there again, as a bound Harvester waits.
- Never: player-placed leaves, Pam's fruit trees (the blocks of `harvest/pick_to_age_0`), apricorn trees, the magic trees
  of the Twilight Forest.
- Skip huge nether fungi, which leave nothing to replant, and trees with more than 256 logs.
- Ask each claim mod's break question for each block, as the Harvester does.
- Attached blocks (cocoa, vines, propagules, Pam's fruit) need a rule. The design proposes defaults for the open details:
  dark oak with 4 saplings, mangroves, the pace and the Work Stamina, and silence.

### The engine today

- The Harvester breaks blocks with no sound: loot from `Block.getDrops` with an empty tool and no entity, `Level.setBlock`
  to the cell's fluid, `spawnAfterBreak`, and `Block.popResourceFromFace` toward the work spot (ADR-0015, decisions 7 and
  8). It asks the use question and the break question of every block that it changes before it changes any, and one refusal
  leaves everything as it was (CD-HRV-13, CD-HRV-16).
- The claim registry keys a farming claim by the crop and the role's lane (ADR-0015, decision 9). Every other role claims
  in the null lane, so a Cultivator claim and a claim of another role on one cell never block each other.
- A leash-only role scans nothing (`ScanKind.NONE`), as the Magnet and Tailwind do.
- A farming binding keeps the empty cell of its crop (CD-CRP-09).

### Research (RES9, RES12)

The research of 2026-11-14 read vanilla and NeoForge 21.1.265 in the sources jar and the frozen jars of COHO with `javap`.
The facts that shape the decision:

- **Tags.** `#minecraft:logs` holds `#logs_that_burn`, `#crimson_stems` and `#warped_stems`, and each `#<wood>_logs` tag
  also holds the wood block and both stripped forms. So the tag matches build materials. `#minecraft:overworld_natural_logs`
  misses the logs of the Aether, the Undergarden, Ars Nouveau, Occultism and the Twilight Forest. `#minecraft:saplings`
  holds the vanilla saplings, azalea, flowering azalea, the mangrove propagule and the 50 saplings of Pam's trees.
- **Leaves.** `LeavesBlock` has `DISTANCE` (1 to 7) and `PERSISTENT`. `getStateForPlacement` sets `PERSISTENT` true, so every
  leaf that a player places is persistent. Leaves of world generation and of sapling growth are not. A leaf counts each
  block of `#minecraft:logs` as distance 0, steps over the 6 faces, and decays at distance 7 when it is not persistent.
  Only leaves carry a natural-or-placed signal. Logs carry none.
- **Shapes.** Dark oak grows only from 4 saplings in a 2 × 2. Spruce and jungle grow a mega tree from a 2 × 2 and a normal
  tree from 1. Acacia logs step diagonally, so a search needs the 26 neighbours. The largest vanilla trees have about 150
  logs (mega jungle) and 113 logs (mega spruce). Fancy oaks and taller trees rise above a work area of vertical radius 6.
- **Drops.** A log drops itself. A leaf drops its sapling at 5% with an empty tool (jungle 2.5%), sticks at 2% and, for oak
  and dark oak, an apple at 0.5%. With about 60 leaves, a small oak drops at least one sapling 95% of the time. A dark oak
  with about 150 leaves drops 4 or more saplings about 94% of the time (estimates).
- **Hard shapes.** Huge fungi drop no fungus, so they cannot replant. Mangrove leaves never drop a propagule. Only a hanging
  propagule at age 4 drops one, and the trunk starts 1 to 3 blocks above the planted cell, over its roots.
- **Look-alikes.** Pam's fruit trees use vanilla logs and leaves and hold `pamhc2trees:pam<fruit>` blocks. Apricorn trees
  use `#cobblemon:apricorn_logs` and `cobblemon:apricorn_leaves`. The Twilight Forest's magic log cores are in no log tag.
  Unusual End puts `chorus_cane_block` in `#minecraft:logs`, and Better Archeology has a `rotten_log` (RES11).
- **Attached blocks.** When a log goes with `UPDATE_ALL`, vanilla's shape update breaks each attached block through
  `Level.destroyBlock`: cocoa, a hanging propagule, a vine, a snow layer, Pam's fruit or a torch. That call plays the break
  sound with level event 2001, and it drops the loot at that block. Flag 16 skips the shape update, and flag 32 drops nothing, but neither
  stops the sound of a `destroyBlock`.
- **Other mods.** TreeChop and Accelerated Decay listen to the break event of a player only, so a removal with
  `Level.setBlock` never reaches them. Create's `TreeCutter` has no `PERSISTENT` check, no cap and a private result.
  MineColonies' forester checks `PERSISTENT` and caps a tree at 400 blocks. Cobbleworkers 2.0.7 has no tree job.
- **Regrowth.** A sapling needs block light 9 or more and about 19,100 ticks (16 minutes) of random ticks to grow, only in
  a chunk near a player. Bone meal advances it with a chance of 0.45 per use (`SaplingBlock.isBonemealSuccess`), and a tree
  grows after two steps.
- **Benchmarks.** A player with an iron axe breaks a log in 10 ticks (hardness 2, speed 6). Oritech's Treefeller fells at
  most 1 log per tick for 100 FE per log (RES12).
- **Dynamic Trees 1.7.2** (RES12). Its branches are in `#minecraft:logs`. A break with no entity is a "sloppy break" that
  drops nothing by default. The public `BranchBlock.futureBreak(state, level, pos, LivingEntity)` fells the tree above the
  cut as Dynamic Trees fells it for an entity.

## Decision

1. **The role.** `lumberjack.json`: handler `cobblemon_dynamics:lumberjack`, the Bug type, `auto_assign` false, priority
   0, range 6. The handler scans nothing. A leash-only role takes no Quick Work (CD-QWK-03).
2. **The target.** A block of `#cobblemon_dynamics:lumberjack_targets`, whose default holds `#minecraft:logs`, or of
   `#minecraft:saplings`, so that a player can bind a Lumberjack to a sapling before its tree grows. A click on any log of
   a tree resolves to the trunk base cell. That is the lowest log that the search of decision 3 finds from the clicked log,
   with the least coordinates on a tie, so a 2 × 2 trunk resolves to its corner. A click on a sapling resolves to the sapling, or
   to the corner of its 2 × 2 of saplings. The base cell must sit in the work area. The rest of the tree may reach past it.
3. **The tree.** A pure search over plain facts of the cells:
   - The logs: the blocks of the base log's own block id, reached from the base through the 26 neighbours. A stripped log,
     a wood block or a log of another kind never joins, so a log build beside a tree stays.
   - The canopy: each `LeavesBlock` with `PERSISTENT` false that lies within 6 face steps of a log of the tree through
     leaves, and not within 6 such steps of a log outside the tree. That is the set that vanilla would let decay once the
     tree is gone, so a neighbouring tree keeps its leaves.
   - A tree needs at least 3 such leaves, as MineColonies' forester does. A trunk with no natural canopy is a build.
   - Caps: at most 256 logs (`max_logs`) and 1,024 leaves (`max_leaves`). A tree above a cap is skipped whole. A search
     that visits more than 4 × `max_leaves` cells of leaves finds no tree (R82).
   - A cell of a chunk that is not loaded counts as a log, so it keeps the leaves near it, and a log next to such a cell
     gives no tree (R82).
   - The never tag `#cobblemon_dynamics:lumberjack/never` skips a whole tree when a block of the tree or a block next to
     one is in it. The defaults: `#cobblemon_dynamics:harvest/pick_to_age_0` (Pam's fruit), `#cobblemon:apricorns`,
     `#cobblemon:apricorn_logs`, `cobblemon:apricorn_leaves`, the four magic log cores of the Twilight Forest,
     `minecraft:bee_nest`, `minecraft:beehive`, `#minecraft:crimson_stems`, `#minecraft:warped_stems`,
     `#minecraft:mangrove_logs`, `unusualend:chorus_cane_block` and `betterarcheology:rotten_log`. Each entry of another
     mod is optional.
4. **The shell.** The plan of a fell also takes each block next to the tree that could not survive once the tree is gone,
   such as cocoa, vines, a hanging propagule, a snow layer or a torch. The handler asks each such block's `canSurvive`
   against a view of the level in which the tree's cells are empty, and repeats for the blocks next to those, so a chain
   of vines goes as one. The fell removes the shell first, with no shape update, and its loot joins the drops. So no
   attached block pops with a sound. An ascending rail whose raised side is a block of the tree joins the shell. Each item
   frame, glow item frame and painting that hangs on a block of the tree or the shell comes down with no sound, and the
   frame or the painting and the frame's item join the drops. A block whose check throws plans no fell, and the role stays
   on (R82).
5. **Demand.** A Lumberjack has demand while a tree of decision 3 stands on its base. At a sapling or an empty cell, it
   waits in Standby with its claim and its binding. A base log with no tree waits 1,200 ticks before its next search
   (R82).
6. **Pace and Work Stamina.** The fell happens T ticks of Working per log after the work starts. T falls with level, from
   `ticks_per_log_at_level_1` (default 20) to `ticks_per_log_at_level_100` (default 5), so a level 67 worker matches an iron
   axe. Work Stamina drains only through Working, so a big tree costs more.
7. **Claims.** In the tick that the fell starts, the handler asks the use question and the break question of every block of
   the fell, and the use question of the base cell for the replant. It asks each claim mod once for each chunk and block
   id of the fell, which gives the same answers, because each claim mod decides by chunk and the edit whitelist of FTB
   Chunks by block. One refusal stops the whole fell, and the tree has no demand for that worker for 6,000 ticks. The
   handler asks once per start of work, which is how "once per claim" of CD-LUM-11 reads. It asks about the cell of each
   frame and painting of decision 4 once for each chunk and entity type (R82).
8. **The fell.** The handler removes the blocks with `Level.setBlock` to each cell's fluid and `spawnAfterBreak`: the shell
   with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, then the leaves and the logs from the top, with `UPDATE_ALL`. It removes at
   most `blocks_per_tick` (128) blocks per tick, so a vanilla tree falls in one tick and a mega tree in a few. A fell that
   has started completes: if the worker's claim ends during it, the handler removes the rest in that tick. A running fell
   holds a region ticket on each chunk of its plan, so no chunk of the plan unloads before the fell ends. A leaf that is
   persistent when its step comes stays (R82).
9. **Drops.** The loot of each block comes from `Block.getDrops` with an empty tool and no entity. The handler merges equal
   stacks and pops them, after the last block, out of the base cell's face toward the work spot, as the Harvester does. A
   Magnet of the pasture collects them. A block whose loot throws drops no loot, and the role stays on (R82).
10. **Replant.** The replant item is a dropped `BlockItem` whose block is in `#minecraft:saplings` and survives in the base
    cell. One item leaves the drops, and the handler plants it in the tick of the last removal. A 2 × 2 trunk replants four
    saplings of one kind when the drops hold four. Otherwise it plants one at the corner cell, except a sapling of
    `#cobblemon_dynamics:lumberjack/needs_four` (default: `minecraft:dark_oak_sapling`), which never grows alone. Then the
    cells stay empty and every item drops. With no sapling in the drops, the base cell stays empty and the binding waits
    for the player. A claim can change during a fell of several ticks, so the end of the fell asks the use question of
    each replant cell again, and a refused replant drops its saplings with the rest (R82).
11. **Binding.** A Lumberjack binding holds while its base cell holds a log of the target tag, a sapling, or air or a fluid
    above a block that is not air. A bind on a tree that decision 3 refuses is refused with a message, so a player never
    leashes a worker to a tree that it will not fell. A bind on a sapling of `#cobblemon_dynamics:lumberjack/never_saplings`,
    whose tree the never tag skips, is refused too. Its default holds the mangrove propagule, `#cobblemon:apricorn_saplings`,
    the four magic saplings of the Twilight Forest and the saplings of Pam's trees (R82).
12. **Silence.** The handler posts no level event, plays no sound, calls no `Level.destroyBlock` and posts no break event.
    `SilenceTest` scans `handler/tree/`.
13. **The effect.** Aerial Ace (`aerialace`), with its target particle only if its actor file plays a sound. The build
    checks the particle ids in Cobblemon 1.8.4.
14. **Saplings for the Cultivator.** The default `cobblemon_dynamics:cultivator_targets` gains `#minecraft:saplings`. A
    sapling is a `BonemealableBlock`, so the growth adapter reads it with no new code. The Cultivator claims in its own lane
    and the Lumberjack in the null lane, so a Ground-type and a bound Bug-type share a base cell: the Cultivator grows the
    sapling, and the Lumberjack fells the grown tree. A sapling with no room to grow changes nothing, so the stall of
    CD-CUL-09 stops the Cultivator there.
15. **Dynamic Trees.** Where Dynamic Trees is loaded, a base log of `#dynamictrees:branches` is felled through
    `BranchBlock.futureBreak(state, level, pos, worker)` with the worker as the entity, so Dynamic Trees drops the wood by its
    own rules. Dynamic Trees then owns the drops and the regrowth, and the handler replants nothing. Its own fell is not
    silent: `futureBreak` posts level event 2001 at the cut, and its falling tree entity plays its own sounds (Task 21 read
    the 1.7.2 jar). The compat package `compat/dynamictrees/` holds the only call. A base log of the namespace
    `dynamictrees`, whatever its tags, and one that the compat package reports as a `BranchBlock` take the same fell, so a
    renamed branch tag never lets the handler remove the branches itself. With no hook, such a tree is no tree (R89).

## Consequences

- A bound Bug-type fells one tree per regrowth. With a level 50 Cultivator on the sapling, an oak line yields about one tree
  per minute (estimate), well below a Create saw farm or an Oritech Treefeller.
- About 1 dark oak in 17 drops fewer than 4 saplings. Its base cells then stay empty for the player, as a cabbage cell does.
- A Pam's fruit tree whose fruit a player broke looks like a vanilla tree, and the Lumberjack fells it.
- A mangrove, a huge fungus and a tree with a bee nest stay standing. A pack can change the never tag.
- A block that a player placed against a tree and that cannot stand alone, such as a torch, is part of the shell. It drops
  beside the trunk with the logs.
- A fell of a mega tree asks a few hundred claim questions in one tick, fewer with the cache. The removal spreads over a
  few ticks.
- A Dynamic Trees fell does not follow the silence rule of the other trees. Its falling tree can also hurt entities where it
  lands, if the `enableFallingTreeDamage` setting of Dynamic Trees is on.
- A player's own logs of the same block id that touch a tree belong to the search of decision 3, so they come down with the
  tree. A fixed item frame on the tree comes down too, a framed map keeps the frame's marker, and the fell takes no leash
  knot (R82).

## Alternatives considered

- **Auto-assignment, tree to tree.** Rejected by the approver: leash only.
- **Let the leaves decay.** Rejected: the saplings would drop minutes later across the canopy, not in time for the replant.
- **Break every natural leaf that touches the tree.** Rejected: it takes the leaves of a neighbouring tree.
- **Remove the logs with `UPDATE_ALL` and wait for vanilla's leaf distances.** Exact, but it spreads one fell over 10 or
  more ticks and needs the attached blocks gone first anyway. The pure canopy rule gives the same set in the plan.
- **Call Create's `TreeCutter`.** Rejected: a dependency on Create for a vanilla role, a private result, no `PERSISTENT`
  check and sounds.
- **TreeChop's API.** Rejected as the default: a soft dependency whose rules change with each pack's TreeChop config.
- **One pace at every level, as an iron axe.** Rejected by the ruling on decision 1 (R81 of the spec sheet): the level sets
  the pace, as it does for the other roles that name a level.

## Amendment (1.2.1, R94): a full replant, and the Bug/Ground cycle

Status: Proposed (2026-11-18), with the [1.2.1 plan](../plans/2026-11-18-v1.2.1-replant-area-dual-type.md). Input R94 of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md) holds the approver's
report and rulings of 2026-11-18. This amendment replaces the rule "no sapling comes from nothing" of decision 10.

The approver's report: "lumberjacks that are chopping trees with a base of more than 1 block only replant one block." The
approver's rulings: "fix it by always replanting even if not enough saplings were dropped", and, for a 2 × 2 tree that drops
fewer than 4 saplings, "Always a full 2×2".

- **Why only one.** A 2 × 2 trunk replanted four saplings only when its drops held four. With no Fortune, a jungle leaf drops
  a sapling 1 time in 40, and a spruce or dark oak leaf 1 time in 20, so a giant tree often drops fewer than four. The
  handler then planted one at the corner. One spruce or jungle sapling grows a small tree, whose next fell replants one
  again, so one short fell ended the giant tree for good. A dark oak with fewer than four got none. The code of
  `ReplantRule` and `FellPlan.twoByTwo` has no other fault: the corner is the trunk's least cell, and each cell of the trunk
  is empty and over dirt when the fell ends.
- **Decision 10, replaced.** A fell always replants (CD-LUM-17). A 2 × 2 trunk gets four saplings of one kind when each cell
  of its square is empty, holds the sapling and passes the use question at the end of the fell. Otherwise it gets one at the
  corner, except a sapling of `#cobblemon_dynamics:lumberjack/needs_four`, which it plants only as four (CD-LUM-18).
- **The kind.** The first kind of this order: a sapling of the drops; the sapling that the tree's leaves drop by their loot
  table; the item `ns:x_sapling` of the base log `ns:x_log` or of a leaf `ns:x_leaves`. A kind counts only when its item
  places a block of `#minecraft:saplings` (CD-LUM-31). The leaf source rolls the loot table of the tree's most common leaf
  with no tool and a fixed seed until a sapling drops, at most 512 times, and keeps the answer for each leaf block until the
  next data reload. With no kind, the handler plants nothing and logs one line for the log's block id (CD-LUM-33).
- **Drops first.** The replant takes as many saplings of its kind as the drops hold, up to the 1 or 4 that it plants, and
  creates the rest. A created sapling never joins the drops (CD-LUM-32).
- **Unchanged.** The use question of each replant cell at the end of the fell (R82), and Dynamic Trees' own fell and regrowth
  (decision 15).
- **Decision 14, extended.** A Bug/Ground worker bound to a tree also does the Cultivator's work on its bound cell: it fells
  the tree, grows the replanted sapling with bone meal, and fells the new tree (CD-LSH-30,
  [ADR-0005, Amendment 4](0005-global-claim-registry.md#amendment-4-one-role-at-a-time-on-a-target-121-r94)). So the Cultivator resolves a sapling of a complete 2 × 2 to the square's least corner, as
  the Lumberjack does (CD-CUL-13), and both handlers name the same cell. Bone meal on that corner grows the giant tree.
