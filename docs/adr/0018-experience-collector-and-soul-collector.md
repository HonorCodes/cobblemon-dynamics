# ADR-0018: The Experience Collector and the Soul Collector

Status: Proposed (2026-11-15). The coordinator rules on it with the design decisions of the
[1.2 plan](../plans/2026-11-15-v1.2-new-roles-and-compat.md#design-decisions). Inputs R76, R79 and R80 of the
[spec sheet](../plans/2026-11-09-v1-spec-sheet.md) hold the rulings, RES9, RES12 and RES13 the research.

## Context

The approver, 2026-11-15 (R76), on the Soul Collector of the 1.2 list (N3):

> N2 and N3 are approved, but build an Experience Collector container for the mod, so it doesn't have to rely on other
> mods and will fit better with vanilla. it should have an interface on right-click that has a collect experience button
> and also an output on the back and bottom of the collector for pipes, other mods were used in conjunction with it

The coordinator's rulings (R79):

- The Soul Collector (Ghost) is a Work Leash role only, and it binds only to an Experience Collector. It has no bottle mode
  and no direct target in another mod's tank, because pipes from the collector reach those mods.
- It takes the XP orbs of its pasture's work area, but leaves every orb within 8 blocks of a player, so it never takes a
  player's XP or Mending repairs. Its pace grows with level, and it costs Work Stamina.
- The Experience Collector is a block of this mod with a facing: its front faces the placer. A right-click opens a screen
  with the stored XP, in points and in levels, and a "Collect experience" button. A request that the server checks gives
  all stored XP to that player.
- The back face and the bottom face are outputs for pipes, and they only drain. Pipes see the stored XP as the XP fluid
  of the installed mod with no setup: Create: Enchantment Industry's `create_enchantment_industry:experience` at 1 mB per
  XP first, otherwise the first fluid of `c:experience` at 20 mB per XP. Where Applied Experienced can import XP through
  experiencelib's XP capability, the outputs offer that too. The mod registers no fluid of its own, and pipes cannot insert.
- The collector keeps its XP on the item when broken, as the Charge Cell keeps its FE, and gives a comparator signal. Its
  capacity is a config key. Its recipe uses vanilla items. Anyone who can open it can collect, as at a chest, and claim mods
  protect it as a block.
- The block, item and screen art goes through the art pipeline and the approver's review (ADR-0012).

The coordinator's ruling on the audits (R80, ruling 4): the XP machines of Ender IO, Industrial Foregoing, Cyclic and Just
Dire Things are pipe destinations of the collector's output. They are not Soul Collector targets.

### Research (RES9, RES12, RES13)

- **XP fluids.** NeoForge registers no XP fluid. Its tag `c:experience` (`Tags.Fluids.EXPERIENCE`) documents 20 mB per XP
  point. In COHO, two mods add an XP fluid: Create: Enchantment Industry 2.5.4b with `create_enchantment_industry:experience`
  at 1 mB per XP, which is not in `c:experience`, and Sophisticated Core 1.5.33.2470 with `sophisticatedcore:xp_still` at
  20 mB per XP, which is (RES9).
- **Pipe destinations.** Ender IO's Experience Obelisk and tanks, Industrial Foregoing's essence tanks, Cyclic's
  Experience Pylon and Just Dire Things' Experience Holder each accept any `c:experience` fluid at 20 mB per point through
  NeoForge's fluid capability (RES12).
- **Applied Experienced 1.3.2.** Its ME import bus (`ExperienceStackImportStrategy`) keeps a `BlockCapabilityCache` of
  `ExperienceLibCapabilities.EXPERIENCE.block()` on the block that it faces. `transfer` reads `IExperienceHandler.getExperience()`
  and calls `extractExperience(tank, amount, simulate)` for each tank (`javap -c`, RES13). So a block that exposes that
  capability feeds an ME network with no fluid step.
- **experiencelib 1.2.1.** `IExperienceHandler` extends `INBTSerializable<CompoundTag>`: `getTanks()`,
  `canAcceptExperience(int, long)`, `canProvideExperience(int, long)`, `getMaxExtract(int)`, `getMaxReceive(int)`,
  `setExperience(int, long)`, `setCapacity(int, long)`, `receiveExperience(int, long, boolean)`,
  `extractExperience(int, long, boolean)`, the two `...Recipe` variants, `getExperience()` and `getExperienceCapacity()`.
  `ExperienceLibCapabilities.EXPERIENCE` is a record of a sided `BlockCapability<IExperienceHandler, Direction>` and an
  item capability (RES13).
- **Orbs.** An `ExperienceOrb` holds `value` × `count` XP. `value` is public through NeoForge's access transformer, and
  `count` is private with no getter. A merged orb gives one unit of `value` per pickup, one pickup every 2 ticks
  (`takeXpDelay` 2). An orb follows the nearest non-spectator player within 8 blocks, and a pickup repairs Mending gear
  first (RES9).
- **Menus.** `ClientboundContainerSetDataPacket.write` writes each data value with `writeShort`, so a data slot holds 16
  bits. `ServerGamePacketListenerImpl.handleContainerButtonClick` checks that the packet names the open menu, that the
  player is no spectator and that `AbstractContainerMenu.stillValid` holds, before it calls `clickMenuButton` (RES13).

## Decision

### The Experience Collector

1. **The block.** `cobblemon_dynamics:experience_collector`, a block with a block entity and an item. Its `FACING` is
   horizontal, and placement turns the front toward the placer, as a furnace does. It joins the creative tab after the
   Charge Cell and the tag `minecraft:mineable/pickaxe`.
2. **Storage.** The block entity stores an `int` of XP from 0 to `experienceCollector.capacity`, a new key of the server
   config: 1,000 to 1,000,000, default 100,000. 100,000 XP takes a player from level 0 to about level 166. A collector that
   holds more than a lowered capacity keeps its XP and takes no more until it holds less.
3. **The screen.** A right-click opens a menu with no slots, through the block's own use path, for any player who can
   open it. The screen shows the stored XP in points, the level that a player at level 0 would reach with it, and a button
   "Collect experience". The menu syncs the XP to the client in two data slots of 16 bits each, because the vanilla packet
   writes shorts.
4. **The button.** The button sends vanilla's container button packet. On the server, `clickMenuButton` gives all stored
   XP to that player through `Player.giveExperiencePoints` and sets the store to 0 in the same tick. Vanilla's own checks
   apply first: the open menu, no spectator, and the distance of `stillValid` (8 blocks). The mod adds no network payload,
   so the payload version stays 3.
5. **Protection.** The screen opens only through the block's use path, so a claim mod that cancels the right-click keeps the
   player out, as at a chest. The block breaks under the claim mods' rules like any block.
6. **Fluid output.** The back face (opposite the front) and the bottom face expose `Capabilities.FluidHandler.BLOCK`. The
   other faces and a null side expose none. The handler has one tank that holds the resolved XP fluid, with stored XP ×
   ratio mB. It drains only: `fill` returns 0, and `isFluidValid` is false. A drain of A mB removes floor(A ÷ ratio) XP and
   gives that XP × ratio mB, so no part of an XP point is lost or made.
7. **The fluid and its ratio.** After each tag load, the collector resolves its fluid: `create_enchantment_industry:experience`
   at 1 mB per XP when the fluid registry holds it, otherwise the first fluid of `#c:experience` at 20 mB per XP, otherwise
   none. With none, the faces expose no fluid handler, and the button still works. One INFO line names the choice.
8. **XP capability.** Where experiencelib is loaded, the back and bottom faces also expose `ExperienceLibCapabilities.EXPERIENCE`
   through `compat/experiencelib/`. Its handler drains only: one tank, `extractExperience` takes up to the stored XP and
   honours `simulate`, `receiveExperience` returns 0, `canAcceptExperience` is false, and `setExperience` and `setCapacity`
   change nothing. An Applied Experienced import bus on such a face imports the XP into its ME network.
9. **Comparator.** The signal is 0 when the collector is empty and 1 + floor(14 × stored ÷ capacity) otherwise, as for the
   Charge Cell (CD-GEN-10).
10. **The item.** A broken collector drops an item with the data component `cobblemon_dynamics:stored_experience`, through
    `copy_components` in its loot table, and its tooltip reads "12,345 / 100,000 XP". The placed block takes the XP back.
11. **The recipe.** Shaped: iron ingots in the 4 corners, glass in the top middle and on both sides of the middle row, a block
    of lapis lazuli in the centre, and a hopper in the bottom middle. All vanilla items.
12. **Art.** A block model with a front, a back output and a bottom output, an item model that renders the block, and a screen
    background, all drafted for this mod and approved on the review spritesheet (CD-ART-01, CD-ART-04, CD-ART-10).

### The Soul Collector

13. **The role.** `soul_collector.json`: handler `cobblemon_dynamics:soul_collector`, the Ghost type, `auto_assign` false,
    priority 0, range 6. The handler scans nothing and matches only an Experience Collector.
14. **Shared claims.** The handler marks its targets as shared, as the logistics roles do, so up to
    `logistics.maxWorkersPerContainer` (default 5) Soul Collectors work one collector. A collector holds no items, so no
    Magnet or Telekinesis worker shares it.
15. **Orbs.** Every 20 ticks, the worker queries the `ExperienceOrb` entities in its pasture's work area once, as the Magnet
    queries items. It never takes an orb that has a non-spectator player within `player_distance` (default 8) blocks. That
    is the radius at which vanilla makes an orb follow a player, so the role never takes a player's XP or Mending repairs.
16. **Pace.** In each batch, the worker takes at most ceil(U × L ÷ 100) orb units, nearest first, where U is
    `orb_units_per_second_at_level_100` (default 10). A level 100 Soul Collector takes one unit per 2 ticks, as a player
    does.
17. **No XP lost or made.** An orb holds `value` × `count` XP, and an access transformer line makes `count` public, as
    Create: Enchantment Industry and Modular Routers do. The worker takes a whole unit while the collector has room for it.
    It takes part of an orb with a count of 1 by lowering its `value`, and it discards an orb that reaches 0. It takes
    nothing from an orb whose unit does not fit and whose count is above 1.
18. **Demand.** While a takeable orb lies in the work area and the collector has room. A worker with no demand waits in
    Standby.
19. **Work Stamina.** It drains only through Working (CD-STA-06), as for the Magnet.
20. **The effect.** Night Shade (`nightshade`), with its target particle only if its actor file plays a sound. The build checks
    the particle ids in Cobblemon 1.8.4.

## Consequences

- A pasture over a mob farm fills a collector with no other mod. Pipes of Create: Enchantment Industry, Sophisticated Storage,
  Ender IO, Industrial Foregoing, Cyclic and Just Dire Things drain it at their own ratios, and an Applied Experienced import
  bus drains it into an ME network.
- In COHO, the output offers Create: Enchantment Industry's fluid, so a Sophisticated tank, which accepts only `c:experience`,
  takes none. That follows the coordinator's order of fluids.
- A fluid of `c:experience` whose mod reads it at another ratio than 20 mB per XP makes or loses XP in that mod. NeoForge
  documents 20 mB per XP, and the conflict sits between the other mods.
- The Soul Collector moves only XP that already exists, so the farm's own output caps it. A Create: Enchantment Industry
  Experience Lantern in the work area competes for the same orbs.
- The land claim gate asks about the bound collector only. An orb in another team's land claim inside the work area is
  taken, as the Magnet takes items there.
- The collect button may play vanilla's level-up sound for the player. That is the player's action, not work.

## Alternatives considered

- **A tank target in another mod.** Rejected by the coordinator: pipes from the collector reach those mods.
- **A bottle mode.** Rejected by the coordinator. Below a cost of 10 XP per bottle, a loop with Create: Enchantment Industry's
  Item Drain would make XP from nothing (RES9).
- **A fluid of our own.** Rejected: NeoForge registers none, and the collector needs none to work. A new fluid would be a
  third ratio in the pack.
- **A payload of our own for the button.** Kept as a design decision. Vanilla's button packet carries one id and gets the
  server's checks with no new code.
- **Exclusive claims, one Soul Collector per collector.** Kept as a design decision. Shared claims follow the approver's
  ruling on logistics, and a big farm takes more than one worker.
