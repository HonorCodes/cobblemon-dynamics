# ADR-0019: The Freezer, an area job

Status: Accepted (2026-11-15) by the coordinator under the approver's delegation, with the rulings (R81) on the design
decisions of the
[1.2 plan](../plans/2026-11-15-v1.2-new-roles-and-compat.md#design-decisions). Inputs R76 and R79 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) hold the rulings, RES13 the research.

## Context

The approver vetoed the Ice Quencher of the 1.2 list (N7) and gave Ice-types another job, 2026-11-15 (R76):

> give ice the job to cool nearby water blocks, causing them to ice over on-top in a 5block radius of the ice pokemon, no
> quick work

The coordinator's rulings (R79):

- The Freezer is automatic in Work Mode pastures, with no Quick Work.
- The worker goes to unfrozen surface water in the work area. It turns the water source blocks with air above them, within
  5 blocks of itself, into ice. Only the top layer freezes, as on a lake. It can walk on the new ice to reach water further
  out.
- It never freezes water that hydrates farmland in vanilla's range, the top of a bubble column, or a source that feeds a
  flow.
- Freezing changes a block, so it asks each claim mod's break question (FTB Chunks: `EDIT_BLOCK`) for each cell.
- Vanilla's melting by block light stays.
- The design proposes the pace, the Work Stamina, the move effect and the way that the role claims its work. The design rule
  "one claimable block per role" of `docs/roles.md` needs an answer for an area job.

### The engine today

- Every role claims one canonical block. An auto-assigned farming worker releases its claim once its target has no demand,
  and the next scan pass gives it the nearest target (CD-CRP-10).
- A block scan (`ScanKind.BLOCK`) visits each cell of the work area, at most 512 per pasture per tick, so `matches` must read
  only a few cells.
- A work spot must be standable and in the work area, with line of sight to the target (CD-ASG-09).
- A Quick Work order takes any auto-assign role (CD-QWK-03). The role file key `quick_work` opts a leash-only role in.

### Research (RES13)

Read on 2026-11-15 in the NeoForge 21.1.265 sources jar:

- `ServerLevel.tickChunk`: for each of `randomTickSpeed` tries (3 by default), a chance of 1 in 48 calls `tickPrecipitation`
  on a random column of the chunk. So a cold biome tries about 1.25 columns per second in each chunk.
- `tickPrecipitation` takes the cell below the `MOTION_BLOCKING` heightmap and sets ice with `setBlockAndUpdate` when
  `Biome.shouldFreeze` passes: the cell is a `LiquidBlock` of `Fluids.WATER` (a source), its block light is below 10, and for
  natural freezing, one of its four sides is not water.
- `IceBlock.randomTick` melts ice where the block light exceeds 11 minus the ice's light block. `melt` sets water, or removes
  the ice in an ultra-warm dimension.
- `FarmBlock.isNearWater` checks the box from (−4, 0, −4) to (4, 1, 4) around the farmland. So water hydrates farmland that
  lies within 4 blocks of it horizontally, at its height or one block below it.
- Placing ice with `setBlock` posts no level event and plays no sound.

## Decision

1. **The role.** `freezer.json`: handler `cobblemon_dynamics:freezer`, the Ice type, `auto_assign` true, `quick_work`
   false, priority 0, range 6. The file key `quick_work: false` keeps an auto-assign role out of Quick Work. A file
   without the key keeps today's rule, so no other role changes.
2. **One claimable cell.** The answer to the design rule: an area job claims the one cell that it walks to, and it works the
   cells around its worker. The target is a freezable cell: a `minecraft:water` block at its source level, with air above
   it, inside the work area. `matches` reads two cells. Two Freezers never claim the same cell. Their reach may overlap, which
   costs nothing, because freezing a cell twice is not possible.
3. **The work.** While a Freezer Works, every ceil(1,200 ÷ ceil(F × L ÷ 100)) ticks it freezes one cell, where F is
   `cells_per_minute_at_level_100` (default 60). That is 1 cell per minute at level 1, 30 at level 50 and 60 at level 100.
   Each time, it takes the freezable cell nearest to the worker within `reach` (default 5.0) blocks, measured between block
   centres, and it takes its claimed cell last. It sets `minecraft:ice` with `UPDATE_ALL`.
4. **Freezable.** A cell is freezable when decision 2 holds and none of these does:
   - Farmland lies within 4 blocks of it horizontally, at its height or one block below it, so the water hydrates the
     farmland.
   - The block is a `minecraft:bubble_column`. Decision 2 already leaves it out, because it is not a water block, and the
     rule stays explicit.
   - A horizontal neighbour or the cell below holds flowing water, so the source feeds a flow.
   - Its block light is 10 or more, where vanilla freezes no water and the ice would melt again.
5. **Demand and release.** A Freezer has demand while its claimed cell is freezable. Its handler releases the claim of an
   auto-assigned worker once the cell has no demand, as the farming roles do, so the worker goes to the nearest freezable
   cell of the next scan pass. Ice is solid, so the work spots of the next cells include the new ice, and the worker walks
   out over the lake.
6. **Claims.** In the tick of each freeze, the handler asks the use question and the break question of that cell, as a
   harvest that breaks a block asks them (CD-HRV-13, CD-HRV-16). On a refusal it freezes nothing there, and that cell has no
   demand for that worker for 6,000 ticks.
7. **Pace benchmark.** A cold biome tries about 75 columns per minute in each chunk. A level 100 Freezer freezes 60 cells
   per minute and reaches 5 blocks.
8. **Work Stamina.** It drains only through Working.
9. **The effect.** Ice Beam (`icebeam`), the move of the earlier Freezer idea, with its target particle only if its actor
   file plays a sound. The build checks the particle ids in Cobblemon 1.8.4.
10. **Melting.** The handler never melts or keeps ice. Vanilla melts it where the light allows, and the cell is freezable again
    only if its light falls below 10.
11. **Silence.** No level event and no sound. `SilenceTest` covers `handler/water/`.

## Consequences

- A pond in a Work Mode pasture with an Ice-type freezes from the worker outward, and a lake freezes from the shore. The
  water under the top layer stays, as under natural ice.
- A water source of a farm, an elevator, a stream or a lit pool stays water. A source that a Create pump or another machine
  drains from below can still freeze on top. The player builds such a pool under a roof or a lamp.
- An ice field melts only by light, so a dark ice field stays. A player who wants water again breaks the ice or lights it.
- A Freezer works in any biome. Ice in a warm biome melts only by light, as vanilla's ice does.
- The Work Leash cannot bind a Freezer. A block click never hits a water block, and the handler matches only water.
- `quick_work: false` is a new meaning of an existing key of role file schema 1. A third-party file that already sets it on
  an auto-assign role loses Quick Work. The integration page records the change.

## Alternatives considered

- **A claim on a zone of the work area.** One Freezer per zone avoids overlap, but it needs a canonical cell that is not a
  target and a work spot rule of its own. Rejected for the cost of the overlap, which is zero.
- **Packed ice or blue ice by level**, the earlier idea in `docs/roles.md`. Rejected by the approver's ruling: water ices over
  on top.
- **Freeze all cells within 5 blocks at once.** Rejected: one Cobblemon would freeze about 80 cells at a time, faster than any
  cold biome.
- **A handler flag for Quick Work in place of the role file key.** Kept as a design decision. The key lets a pack opt the
  Freezer back in.
