# Cobblemon Dynamics player guide

Cobblemon Dynamics puts your pastured Cobblemon to work. Turn on Work Mode for a pasture. Each of its Cobblemon
then finds a block that fits one of its types, walks to it and works it. For example, a Fire-type keeps a
furnace lit, a Water-type turns a Create water wheel, and an Electric-type pushes Forge Energy into a machine.
A party Cobblemon that you send out while you look at a block can work that block too, with [Quick Work](#quick-work).

Install the mod on the client and on the server. The [README](../README.md#requirements) lists the versions
of Minecraft, NeoForge, Cobblemon and the optional mods.

## Quick start

1. Place a Cobblemon pasture block.
2. Place the blocks that you want worked near the pasture block. The [work area](#work-area) reaches 8
   blocks out on the x and z axes, and 6 blocks up and down.
3. Put your Cobblemon in the pasture.
4. Right-click the pasture block to open its panel.
5. Click the **Work Mode: Off** button above **Recall**. The button then reads **Work Mode: On**.
6. Place beds in the same area, so that Tired workers recover faster.

Each Cobblemon then walks to a block that one of its roles fits, and works it.

In creative mode, the **Cobblemon Dynamics** tab of the creative inventory holds every item of the mod: the Work
Leash, the four beds and the [Charge Cell](#charge-cell).

## Work Mode

Work Mode is a setting of each pasture. A new pasture starts with Work Mode off. The pasture keeps the
setting when the world saves.

### Turn Work Mode on or off

1. Right-click the pasture block to open its panel.
2. Click the Work Mode button. It sits directly above the **Recall** button.

The button shows the current state: **Work Mode: On** or **Work Mode: Off**. It works only while you stay
less than 10 blocks from the pasture block. The pasture's owner is the player who placed it. Only the owner
and operators can click the button. Other players see the button disabled, with the tooltip "Only the
pasture's owner can change Work Mode". A pasture with no recorded owner, for example one set with
`/setblock`, accepts changes from operators only. Break it and place it again to become its owner.

### Workers

While Work Mode is on, each Cobblemon in the pasture is a worker, if it has an owner and has not fainted. Wild
Cobblemon never work. Cobblemon in a party, or sent out from one, never work. Cobblemon's own limit of Cobblemon
per pasture applies, 16 by default.

A worker's target is the block that it works on, for example a furnace. A worker with no Work Leash binding
is an auto-assigned worker: the mod picks its targets. A worker with a binding is a bound worker: you pick
its target with the [Work Leash](#work-leash).

### Work area

Each pasture has a work area: a box around the pasture block. By default, it reaches 8 blocks out on the x
and z axes, and 6 blocks up and down. Workers look for targets and beds only inside it. The server config
can change the size, but never beyond Cobblemon's `pastureMaxWanderDistance`.

### How a worker finds its target

- Every 2 seconds, the pasture scans its work area. Each Idle auto-assigned worker takes the nearest free
  target that one of its roles fits.
- A Cobblemon fits a role through its first or its second type. A Cobblemon with two types can do the roles of
  both types, one role at a time.
- Only one worker can work a machine at a time, across all pastures and all players. The [Magnet](#magnet) and
  [Telekinesis](#telekinesis) share their containers instead, as [What a binding does](#what-a-binding-does) explains.
- The worker walks to a work spot. A work spot is a cell in the work area where the worker can stand and
  see the center of the target. The spot is at most 6 blocks from the target, or 2 blocks for the Cranker.
- A worker stands beside its target, on the floor that the target stands on, when it has room there and can
  walk there. It never stands on top of its target, and it uses a one-block pit beside the target only when
  no other spot exists.
- Keep some open floor around each target. A target with no work spot gets no worker, and the worker skips
  it for 5 minutes. A target with solid blocks on all six sides never has a work spot.
- A worker works only while its target needs work, for example while a furnace has an item to smelt.
  Otherwise it waits at the target in Standby.
- After 10 seconds in Standby, an auto-assigned worker moves to another free target that needs work, when a
  scan finds one.
- If an auto-assigned worker cannot reach its target within 30 seconds, it gives the target up and skips
  it for 5 minutes.
- When two roles fit one target, the role with the higher priority wins. For example, the Brew Stoker wins
  over the Brewer.
- A Fairy-type never takes a target on its own. With no binding, it is a [healer](#healers). The
  [Pump](#pump) and the [Muse](#muse) work only through the Work Leash.

### Turn Work Mode off

When you turn Work Mode off, the workers stop at once and give up their targets, beds and heals. The
Cobblemon then act as normal pastured Cobblemon. Their bindings stay, and the bound workers go back to their
targets when you turn Work Mode on again. When the pasture block breaks, the bindings of its Cobblemon clear.

### Worker states

Each worker is in one of these states:

| State | Meaning |
| --- | --- |
| Idle | The worker has no target. It acts as a normal pastured Cobblemon. |
| Traveling | The worker walks to its target. |
| Working | The worker is at its work spot, and the target needs work. Only this state uses Work Stamina. |
| Standby | The worker waits at its target, which needs no work now. A bound worker also waits in Standby after it fails to reach its target. |
| To Bed | The worker is Tired and walks to a bed. |
| Sleeping | The worker sleeps on a bed. |
| Resting in Place | The worker is Tired and sleeps where it stands, because no bed is free. |
| Healing | A healer walks to an ally, or heals it. |

The pasture panel and the head bars show an icon for each state. To Bed and Resting in Place share the
Tired icon, so seven icons cover the eight states.

### Work effects

A Working worker faces its target and plays its role's move. The move's animation plays to its end, and the
worker then stands idle for 2 seconds before it plays the move again. A worker whose model has no animation
for the move shows the move's particles every 2 seconds. The move's particles fly to the center of the
target. A player sees the effects while the player is within 64 blocks of the worker and the player's game shows
the worker. Work effects play no sound.

## Work Leash

The Work Leash binds one of your pastured Cobblemon to a target that you choose.

**Recipe:** shapeless, 1 Lead and 1 Poké Ball.

### Bind a Cobblemon to a target

1. Hold the Work Leash in your main hand.
2. Right-click your Cobblemon in a pasture with Work Mode on. The action bar shows "Selected {Cobblemon}", and
   the leash's tooltip names the Cobblemon.
3. Right-click the target. The action bar shows "Bound {Cobblemon} to {target}". The target does not open its
   own screen.

The leash forgets its selection after the bind. To move a bound worker to a new target, bind it again. The
new binding replaces the old one. A click on any block of a large water wheel binds the whole wheel.

When a bind fails, the action bar shows why:

| Message | Cause |
| --- | --- |
| "Not in a Work Mode pasture" | The Cobblemon is not in a pasture with Work Mode on. |
| "No role fits" | None of the Cobblemon's roles works on the block. |
| "Outside its pasture's work area" | The block is outside the work area of the Cobblemon's pasture. |
| "Already assigned to {Cobblemon}" | Another of your Cobblemon has a binding of a machine role on the block. Bindings of the Magnet and Telekinesis never cause this message. |
| "Already assigned to another player's {species}" | A Cobblemon of another player has a binding of a machine role on the block. The message names its species, never its nickname. |
| "{target} already has 5 workers" | The container already has as many Magnet and Telekinesis Cobblemon as `logistics.maxWorkersPerContainer` allows, 5 by default. With the key set to 1, the message is "{target} already has a worker". |
| "Select a Cobblemon first" | The selected Cobblemon is no longer yours, for example after a trade or a release. |
| "Destination must differ from the source" | The second click of a [Telekinesis](#telekinesis) binding is on its source container. |
| "Too far from its pasture" | The destination of a Telekinesis binding is more than 32 blocks from the pasture block on an axis. |

### What a binding does

- The binding of a machine reserves the target for its worker. The reservation holds while the worker sleeps
  and while Work Mode is off.
- A new binding of a machine takes the target away from an auto-assigned worker at once.
- The [Magnet](#magnet) and [Telekinesis](#telekinesis) share their containers. Up to 5 Cobblemon of these two
  roles together can use one container, for example a Magnet that fills a chest and a Telekinesis worker that
  empties it. Each binding keeps its own source and destination, and it takes one place at each container
  that it names. Such a binding reserves nothing, and it never blocks the worker of a machine on the same
  block. For example, a Stoker heats a furnace while a Telekinesis worker empties it. Each worker still needs
  its own free work spot. The server config key `logistics.maxWorkersPerContainer` changes the 5.
- A bound worker never moves to another target.
- The binding stays with the Cobblemon through server restarts.
- A bound worker that is Tired starts on its target after it has recovered. After each rest, a bound worker
  goes back to its target.
- If a bound worker cannot reach its target within 30 seconds, the action bar shows "{Cobblemon} cannot
  reach {target}". The worker keeps its binding, waits 5 minutes in Standby and then tries again.

### Clear a binding

Sneak and right-click your bound Cobblemon with the Work Leash. The action bar shows "Cleared the binding of
{Cobblemon}". A click with the leash on your own Cobblemon never opens Cobblemon's interaction wheel and never
mounts the Cobblemon.

A binding also clears in the cases below. The player who bound the Cobblemon then gets the chat message
"{Cobblemon} lost its work binding: {reason}". If that player is offline, the message waits for their next login. The
server keeps up to 10 such messages per player, the newest, and a server restart drops them.

| Case | Reason in the message |
| --- | --- |
| The target breaks or is replaced, is picked up with a Create wrench, or moves into a Create contraption. | its target was broken or replaced |
| The destination of a Telekinesis binding breaks or is replaced. | its destination was broken or replaced |
| The Cobblemon goes back to the PC or the party. | it left its pasture |
| The Cobblemon moves to another pasture. | it moved to another pasture |
| The Cobblemon is released. | it was released |
| The Cobblemon goes to a new owner, for example in a trade. | it has a new owner |
| The Cobblemon evolves into a form that no role on its target fits. | its new form cannot do the job |
| The pasture block breaks. | its pasture block is gone |
| An operator runs [`/cobblemondynamics unbind`](#commands) on its target. | an operator cleared it |

A binding stays when its target is in an unloaded chunk.

In a shared pasture, another player can bind one of their own Cobblemon to your block, and the binding then
reserves that block. Cobblemon lets only a Cobblemon's owner take it out of a pasture, so if that player does not
come back, ask an operator to run `/cobblemondynamics unbind` on the block.

### Flip a water wheel or a hand crank

Hold the Work Leash, then sneak and right-click a water wheel or a hand crank that a worker turns. Its
direction reverses at once. The player who bound the worker, the owner of the worker's pasture and operators
can flip it. Each wheel and each crank keeps its own direction, also through restarts and when another
worker takes over.

The same click switches a fan that a Fan Catalyst works between blasting and smoking (see [Fan Catalyst
Fire](#fan-catalyst-fire)).

### While you hold the Work Leash

- A Work Stamina bar and a state icon show over each worker within 16 blocks of you. The client config key
  `hud.headBarRange` changes this distance.
- The targets of your own bound workers show an outline. The targets of other players' workers show none.

## Quick Work

A party Cobblemon can work one block on demand, with no pasture and no Work Leash.

1. Select a party Cobblemon whose type fits a role, for example a Fire-type for a furnace.
2. Look at the block, up to 16 blocks away.
3. Press **R** to send the Cobblemon out, as you always do.

The Cobblemon walks to the block and works it, as a worker in a pasture does. Its Work Stamina drains while it works. The
action bar tells you when the work starts, and when and why it stops.

Quick Work uses the roles that a pasture gives out on its own: the Stoker, Wheel Driver, Cranker, Superheater,
Generator, Crystal Tender, Brew Stoker and Brewer. It also uses the Muse, whose role file opts in to Quick Work. When only a
Work Leash role of the Cobblemon fits the block, for example Telekinesis for a Psychic-type at a chest, the action bar shows
"Chest needs the Work Leash". A Cobblemon with a Fairy type does Quick Work only as a Muse, because it is otherwise a healer.

### Quick Work at an enchanting table

Send out a Fairy-type while you look at an enchanting table. The Cobblemon waits by the table. While you have the table's
menu open, it adds its bonus, as a Muse with a Work Leash binding does (see [Muse](#muse)). It stays while you close
the table to fetch lapis or books, and it does not leave because the table needs no work. It stops for the other reasons
of [When Quick Work stops](#when-quick-work-stops), for example when you go more than 16 blocks away or recall it.

### When Quick Work does not start

The Cobblemon comes out as usual, with no work, and the action bar tells you why, when:

- another Cobblemon works the block already, or a Work Leash binding holds it
- the Cobblemon is Tired
- the block needs no work now, for example a furnace with nothing to smelt. A Muse at an enchanting table starts anyway
  and waits for the menu.
- the Cobblemon cannot reach the block
- you may not use the block, for example inside spawn protection, or in a land claim that does not let you right-click
  the block (see [Land claims](#land-claims))

### When Quick Work stops

The work stops for good when:

- you go more than 16 blocks from the block
- you recall the Cobblemon, or it faints
- the Cobblemon gets Tired. It then recovers in your party at the rate with no bed.
- the block breaks or changes, or a Work Leash binding takes it
- the block needs no work for 10 seconds, except at an enchanting table
- the Cobblemon cannot reach the block within 30 seconds
- you log out or go to another dimension
- the Cobblemon starts a battle
- you may no longer use the block, for example when you leave the party that owns its land claim, or when another player
  claims its chunk

The Cobblemon then follows you again. It does not look for other work. To work the block again, recall the Cobblemon and send
it out again while you look at the block.

## Land claims

Cobblemon Dynamics follows the land claims of three claim mods: FTB Chunks, Open Parties and Claims and MineColonies.
Each claim mod decides with its own rules who may use a block. A Cobblemon works a block in a land claim only if the claim
lets the right player right-click that block with an empty hand:

- **Quick Work** asks about you, the owner of the Cobblemon that you send out.
- **A Work Mode pasture** asks about the owner of the pasture, the player who placed the pasture block. Its workers work
  a machine only if the claim lets that player right-click the machine. This holds also while you are offline: your
  pasture keeps your rights when you log out, and it never gets more rights than you have.

### What each claim mod allows

- **FTB Chunks.** The members of the team that owns the claim can use its blocks. Its allies can too, while the team's
  setting for block interaction is "allies", which is the default. A team can set it to "public" for every player, or to
  "private" for its members only.
- **Open Parties and Claims.** The owner of the claim can, and by default the members of the owner's party can too. The
  members of an allied party can only if the owner sets `claims.protection.exceptions.fullAccess` to `A` in the owner's
  player config.
- **MineColonies.** The ranks of the colony decide. By default, an Officer can use the blocks of the colony. A Friend can
  use a block with no inventory, but not a container such as a furnace. A player outside the colony has the Neutral rank.
  The Neutral and the Hostile rank can use neither. Huts need the rank's permission to access huts. An operator in
  creative mode can Quick Work in any colony, as that player's own click can, if the operator's permission level is at
  least the colony setting `permissionEventMinBypassPermLevel` (2 by default). A pasture gets no such exception.

Each claim mod applies its own rules, so allies differ between them. By default, an ally can use the machines in a land
claim of FTB Chunks, but not in a land claim of Open Parties and Claims.

### What a refusal does

- **Quick Work.** A refused send-out stays a normal one, and the action bar shows a message such as "You may not use
  Furnace". While the Cobblemon works, Quick Work checks your right again every 20 ticks (1 second), and the work stops
  when the right is gone.
- **Pastures.** A worker skips a machine that its pasture's owner may not use. Nobody gets a message for that. When the
  right ends, a worker lets go of the machine or the bed that it holds within 20 ticks. A Tired worker takes no bed that
  the owner may not use, and rests where it stands if no other bed is free.
- **Work Leash bindings.** A binding to a block that the pasture's owner may not use ends within 20 ticks, and the binder
  gets a chat message such as "Charmander lost its work binding: the owner of its pasture may not use its target". For
  Telekinesis, the destination counts too.

### Limits

- A pasture with no owner works no block in a land claim.
- If a claim mod fails with an error, for example after an update that changes its code, Quick Work refuses every order,
  and the pastures work no target, also outside land claims. The log holds one warning for each kind of error. Install a
  tested version of the claim mod, or a release of Cobblemon Dynamics that supports the installed one.
  [Modpack integration](modpack-integration.md#claim-mods) lists the tested versions.
- Other protection mods that only stop right-clicks do not see Quick Work or the pastures, because nobody clicks the
  block. A server owner can turn Quick Work off with `quickWork.enabled`. The keys `quickWork.targetRange` and
  `quickWork.leashRange` change the two distances of 16 blocks.

## Work Stamina

Work Stamina measures how long a worker can keep working. It goes from 0% to 100%.

### Drain and recovery

- A worker loses Work Stamina only while it is Working. In every other state, it recovers.
- A full drain takes 3 minutes of Working per level. A level 10 worker works for 30 minutes, and a level 100
  worker works for 5 hours. Legendary and mythical Cobblemon get no extra time.
- A worker recovers as fast as it drains: from 0% to 100% in 3 minutes per level. A [bed](#beds) makes the
  recovery faster.
- Healers recover by their own rule (see [Healer recovery](#healer-recovery)).
- A Cobblemon starts at 100% when it first enters a pasture with Work Mode on. A level-up keeps the percentage.
- Work Stamina stays with the Cobblemon. Time in the PC, in a party or in an unloaded chunk counts as rest,
  at the rate with no bed. A change of the time of day changes no Work Stamina value.

### Tired

At 0%, a worker is Tired. A Tired worker takes no target until it is back at 100%.

- It walks to the nearest free bed in its work area and sleeps there.
- If no bed is free, or it cannot reach its bed within 30 seconds, it rests where it stands. It then
  recovers at the rate with no bed.
- An auto-assigned worker gives up its target at once, so another worker can take it.
- A bound worker keeps its binding. Its target stays reserved, and the worker goes back to it at 100%.
- A heal ends the rest early, because it sets the worker to 100% at once.

| Level | Working time from 100% to 0% | Recovery with no bed | Hay Bed | Wool Bed | Plush Bed | Starlit Bed |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 3 min | 3 min | 1.5 min | 1 min | 45 s | 22.5 s |
| 10 | 30 min | 30 min | 15 min | 10 min | 7.5 min | 3.75 min |
| 50 | 150 min | 150 min | 75 min | 50 min | 37.5 min | 18.75 min |
| 100 | 300 min | 300 min | 150 min | 100 min | 75 min | 37.5 min |

With no bed and no healer, a worker works half of the time at most. With a Starlit Bed, it works up to 8/9
of the time (88.9%).

### See Work Stamina

- **Pasture panel.** Each Cobblemon's row shows a Work Stamina bar and a state icon. The bar is green from
  50%, yellow from 25% and red below 25%. Hover over the bar or the icon to see "{N}% Work Stamina" and the
  state name. The panel updates the values every second. The client config key `hud.panelBars` hides the
  bars.
- **Head bars.** Hold the Work Leash in your main hand (see [Work Leash](#while-you-hold-the-work-leash)).
- **Command.** Operators can run `/cobblemondynamics stamina` (see [Commands](#commands)).

## Beds

A bed lets a Tired worker recover faster. A bed is a low block, 6/16 of a block high. A Cobblemon of any size
can lie on it.

| Bed | Tier | Recovery time | Recipe |
| --- | --- | --- | --- |
| Hay Bed | 1 | 1/2 | Shaped: 3 hay bales in a row over 3 planks of any wood. |
| Wool Bed | 2 | 1/3 | Shaped: a Hay Bed in the center, 4 wool blocks of any color on the edges and 4 iron ingots in the corners. |
| Plush Bed | 3 | 1/4 | Shaped: a Wool Bed in the center, 4 gold ingots on the edges and 4 diamonds in the corners. |
| Starlit Bed | 4 | 1/8 | Shapeless: 1 Plush Bed, 1 Nether Star and 4 Rare Candy. |

- Place beds inside the pasture's work area. A worker never uses a bed outside it.
- A Tired worker takes the nearest free bed. When two free beds are at the same distance, it takes the
  higher tier.
- A bed holds one worker at a time.
- A bed speeds up the recovery only while the worker lies on it.
- At 100%, the worker gets up and frees the bed.
- If a bed breaks under a sleeping worker, the worker rests where it stands, at the rate with no bed.
- Only Tired workers of pastures with Work Mode on use beds.
- A sleeping worker shows Cobblemon's sleep particles every 2 seconds, with no sound. A worker that rests
  where it stands shows them too.

The server config key `beds.speedFactors` changes the factor of each tier.

## Healers

Each Fairy-type in a pasture with Work Mode on is a healer, unless it has a binding. A healer restores the
Work Stamina of the other workers in its pasture.

### How a healer picks an ally

- It heals only Cobblemon in its own pasture. It never heals itself or a Fairy-type.
- In a pasture that several players share, it heals the other players' workers too, because a shared pasture
  works as one team.
- It picks the nearest Tired ally first.
- When no ally is Tired, it picks the ally with the lowest Work Stamina under 25%.
- A healer heals one ally at a time, and an ally gets one healer at a time.

### A heal

- The healer walks to within 2 blocks of the ally and heals it. The ally goes to 100% at once, whatever the
  levels of the two Cobblemon.
- Each heal costs the healer 25 points of its own Work Stamina. A healer starts a heal only with at least
  25%, and never while it is Tired.
- If the ally stops needing the heal before the heal completes, for example because it leaves the pasture,
  the heal stops at no cost.
- If the healer cannot reach the ally within 30 seconds, the heal stops at no cost. The healer then skips
  that ally for 5 minutes.
- A heal that takes the healer to 0% makes it Tired. It then goes to a bed like any worker.
- If Cobblemon has a Floral Healing action effect, the heal plays it. Otherwise, as on Cobblemon 1.8.4, the
  healer plays its special pose, if its model has one, and heal circles show on the ally. The heal plays no
  sound.

### Healer recovery

A healer recovers by the healer rule: from 0% to 100% in 4 × (300 − 270 × (L − 1) ÷ 99) minutes, where L is
its level. The time falls in a straight line from 1,200 minutes at level 1 to 120 minutes at level 100. A bed
divides it, as it does for any worker.

| Level | From 0% to 100% | One heal (25 points) |
| --- | --- | --- |
| 1 | 1,200 min | 300 min |
| 25 | about 938 min | about 235 min |
| 50 | about 665 min | about 166 min |
| 75 | about 393 min | about 98 min |
| 100 | 120 min | 30 min |

A Fairy-type with a binding, for example a [Muse](#muse), is a normal worker. It recovers by the worker rule
and heals no ally.

## Charge Cell

The Charge Cell is a block that stores Forge Energy (FE). It holds up to 50,000 FE.

- Each face of the cell takes FE. A [Generator](#generator) works a cell as it works any block that takes FE, so a
  Generator next to a cell charges it.
- Every tick, the cell pushes its FE into the blocks around it that take FE. Each of them gets an equal share. When a
  block takes less than its share, the others get the rest.
- Two cells side by side share their FE until both hold the same amount. FE never moves from a cell into a cell that
  holds more, so it does not flow back and forth between cells.
- A comparator reads how full the cell is: 0 when the cell is empty, 1 to 14 while it holds some FE, and 15 when it is
  full.
- When you break a cell, its item keeps the FE. The tooltip of the item shows it, for example "12,345 / 50,000 FE". The
  cell that you place from the item holds that FE again.

| Item | Recipe |
| --- | --- |
| Charge Cell | Shaped: a Thunder Stone (`cobblemon:thunder_stone`) in the center, redstone dust above and below it, and 6 copper ingots in the side columns. |

## Farming

Two roles work crops. A Ground-type is a Cultivator: it grows crops with the effect of bone meal. A Grass-type is a
Harvester: it harvests fully grown crops and replants them. Both work vanilla crops, Cobblemon's crops and berry trees,
and the crops of many other mods. All farming work is silent.

### The Cultivator

The Cultivator uses no bone meal item. Each growth use costs 100 ticks of Work Stamina, on top of the drain of work, so
a Cultivator tires faster than other workers. A worker applies ceil(30 × L ÷ 100) uses per minute, where L is its level:
1 at level 1, 15 at level 50 and 30 at level 100. Green sparkles show each use. The Cultivator stops when the crop is
fully grown, where the Harvester starts. When 20 uses in a row change nothing, for example on rice under a blocked
cell, that worker leaves the crop alone for 5 minutes.

### The Harvester

The Harvester harvests a fully grown crop 20 ticks after it starts work. The harvest drops on the ground beside the
crop, on the side that faces the worker. A Steel-type [Magnet](#magnet) bound to a chest of the same pasture collects
the drops. A crop that the harvest breaks is replanted from its own drops: one seed or one crop item leaves the harvest.
With no such item in the drops, the cell stays empty and every item drops. A bush, a vine or a tree fruit keeps its
plant, and only the fruit comes off. A Harvester never takes an apricorn.

### Crop to crop, or one crop with the Work Leash

With no binding, a farming worker goes crop to crop. It leaves a crop as soon as its work there is done, and a later
scan gives it the nearest crop that needs it. To keep a worker on one crop, bind it to the crop with the Work Leash. A
bound Cultivator waits in Standby while its crop is fully grown, and grows the crop again after the harvest. A bound
Harvester waits until the crop is fully grown again. One crop takes one Cultivator and one Harvester at the same time,
auto-assigned or bound. A harvest by hand leaves the cell empty above the farmland. The binding stays, so the worker
goes on when you plant the crop again.

A Cobblemon of both types, such as Torterra, binds to a crop in the role of its primary type. A Quick Work order of such
a Cobblemon takes the role that the aimed crop needs. That is the Harvester at a fully grown crop, and the Cultivator at a
growing crop.

A worker that the work engine directs never tramples farmland. A Harvester also asks the land claim checks in the tick
of each harvest. A harvest that breaks a block also asks each claim mod whether its player may break that block. See
[Land claims](#land-claims).

### Which crops

The block tag `#cobblemon_dynamics:cultivator_targets` names the blocks that a Cultivator grows, and
`#cobblemon_dynamics:harvester_targets` names the blocks that a Harvester harvests. Both hold `#minecraft:crops`, so the
crops of each mod that tags its crops are in them. A server can add blocks to the tags with a data pack. The
[modpack integration page](modpack-integration.md#farming-roles) lists the crops of each supported mod and the adapter
tags that decide how a crop is harvested.

## Roles

A role is a job that Cobblemon of some types do on one kind of block. Auto-assigned workers take the roles
marked "Auto". The roles marked "Work Leash" need a binding. The numbers below are the defaults of the
built-in role files. A server can change them with a data pack (see [Role files](#role-files)).

| Role | Role id | Types | Target | Assignment | Needs |
| --- | --- | --- | --- | --- | --- |
| [Stoker](#stoker) | `cobblemon_dynamics:stoker` | Fire | Furnace, smoker, blast furnace | Auto | |
| [Wheel Driver](#wheel-driver) | `cobblemon_dynamics:wheel_driver` | Water | Water wheel, large water wheel | Auto | Create |
| [Cranker](#cranker) | `cobblemon_dynamics:cranker` | Fighting, Normal | Hand crank | Auto | Create |
| [Superheater](#superheater) | `cobblemon_dynamics:superheater` | Dragon | Blaze Burner with a blaze | Auto | Create |
| [Generator](#generator) | `cobblemon_dynamics:generator` | Electric | A block that accepts Forge Energy | Auto | |
| [Crystal Tender](#crystal-tender) | `cobblemon_dynamics:crystal_tender` | Rock | Budding amethyst, AE2 budding certus quartz | Auto | |
| [Brew Stoker](#brew-stoker) | `cobblemon_dynamics:brew_stoker` | Fire | Brewing stand | Auto | |
| [Brewer](#brewer) | `cobblemon_dynamics:brewer` | Poison | Brewing stand | Auto | |
| [Cultivator](#cultivator) | `cobblemon_dynamics:cultivator` | Ground | A crop that bone meal grows | Auto | |
| [Harvester](#harvester) | `cobblemon_dynamics:harvester` | Grass | A fully grown crop | Auto | |
| [Pump](#pump) | `cobblemon_dynamics:pump` | Water | A block that accepts water, cauldron | Work Leash | |
| [Muse](#muse) | `cobblemon_dynamics:muse` | Fairy | Enchanting table | Work Leash | |
| [Telekinesis](#telekinesis) | `cobblemon_dynamics:telekinesis` | Psychic | Two containers: a source and a destination | Work Leash | |
| [Magnet](#magnet) | `cobblemon_dynamics:magnet` | Steel | A container | Work Leash | |
| [Fan Catalyst Fire](#fan-catalyst-fire) | `cobblemon_dynamics:fan_catalyst_fire` | Fire | Encased fan that faces sideways | Work Leash | Create |
| [Fan Catalyst Water](#fan-catalyst-water) | `cobblemon_dynamics:fan_catalyst_water` | Water | Encased fan that faces sideways | Work Leash | Create |
| [Fan Catalyst Ghost](#fan-catalyst-ghost) | `cobblemon_dynamics:fan_catalyst_ghost` | Ghost | Encased fan that faces sideways | Work Leash | Create |
| [Tailwind](#tailwind) | `cobblemon_dynamics:tailwind` | Flying | Windmill bearing | Work Leash | Create |

Level changes the output of the Generator, the Pump, the Muse, Telekinesis, the Magnet, Tailwind and the
Cultivator. The
Superheater superheats only from level 50. Every other role works the same at every level.

### Stoker

**Types:** Fire. **Level:** any. **Move:** Ember.

**Target:** a furnace, a smoker, a blast furnace, or a modded furnace that extends the vanilla furnace.

The Stoker keeps its furnace lit, as if it burned coal, while the furnace has an item to smelt and room for
the result. It lights an unlit furnace. It never takes fuel from the fuel slot, and the flame gauge stays
full while the furnace cooks. When the Stoker stops, the furnace goes back to its own fuel within 2 ticks.

### Wheel Driver

**Types:** Water. **Level:** any. **Move:** Water Gun, aimed at the wheel's axle. **Needs:** Create.

**Target:** a water wheel or a large water wheel. A large water wheel is one target.

The Wheel Driver turns a wheel that has no water, as if water flowed through it. A small wheel turns at
8 RPM with 256 SU, and a large wheel at 4 RPM with 512 SU. Create's goggles show the capacity. The Wheel
Driver works only while no real water turns the wheel. Next to a wheel that real water turns, it waits in
Standby and uses no Work Stamina. When the Wheel Driver stops, the wheel stops within 2 ticks.

A new wheel turns in Create's positive direction. To reverse it, [flip it with the Work
Leash](#flip-a-water-wheel-or-a-hand-crank).

### Cranker

**Types:** Fighting, Normal. **Level:** any. **Move:** Pound. **Needs:** Create.

**Target:** a hand crank. A valve handle is not a target, because Create turns it only by a fixed angle.

The Cranker turns its crank every 5 ticks, so the crank never stops. The crank gives 32 RPM with 256 SU. A
crank always needs work, so a Cranker works without a break until it is Tired. Its work spot is at most 2
blocks from the crank.

A new crank turns in Create's positive direction. To reverse it, [flip it with the Work
Leash](#flip-a-water-wheel-or-a-hand-crank).

### Superheater

**Types:** Dragon. **Level:** kindles below level 50, superheats from level 50. **Move:** Dragon Breath.
**Needs:** Create.

**Target:** a Blaze Burner that holds a blaze. An empty Blaze Burner is not a target.

Below level 50, the Superheater keeps the burner kindled, as coal does. Under a boiler, the burner then
counts 1 heat. From level 50, the Superheater keeps the burner superheated, as a Blaze Cake does. Under a
boiler, the burner then counts 2 heat.

The Superheater works only while a basin or a fluid tank sits directly on top of the burner. It never works
a creative Blaze Burner, whose heat a worker cannot change. It never changes the burner's own fuel. When it
stops, the burner goes back to the heat of its own fuel within 2 ticks.

Only Dragon-types heat Blaze Burners. A Fire-type with no Dragon type never works a burner, and the Work Leash
cannot bind it to one.

**Role file parameters:** `heat` (`seething`), `min_level` (50) and `below_min_level_heat` (`kindled`).

### Generator

**Types:** Electric. **Level:** the output grows with level. **Move:** Thunder Shock.

**Target:** any block that accepts Forge Energy through NeoForge's energy capability. This includes the AE2
machines that accept FE, such as the Energy Acceptor.

The Generator pushes round(m × 10 × 500^((L − 1) ÷ 99)) FE per tick into its target, where L is its level.
The factor m is 2 for a legendary or mythical Cobblemon, and 1 for any other.

| Level | FE per tick | Legendary or mythical |
| --- | --- | --- |
| 1 | 10 | 20 |
| 25 | 45 | 90 |
| 50 | 217 | 433 |
| 75 | 1,041 | 2,082 |
| 100 | 5,000 | 10,000 |

The Generator pushes through the face of the target nearest to it that accepts energy. It stores no energy,
so any FE that the target refuses is lost. To keep FE for later, let the Generator charge a
[Charge Cell](#charge-cell). It works only while the target can take more energy. Next to a
full target, it waits in Standby. An AE2 Energy Acceptor converts the FE at AE2's own ratio.

**Role file parameters:** `base_fe_per_tick` (10), `max_fe_per_tick` (5000), `rare_multiplier` (2.0) and
`rare_labels` (`legendary`, `mythical`).

### Crystal Tender

**Types:** Rock. **Level:** any. **Move:** Rock Throw.

**Target:** a block in the tag `#c:budding_blocks`. This includes budding amethyst and, with AE2, budding
certus quartz.

The Crystal Tender gives its target 1 extra random tick every 10 ticks, as one AE2 Growth Accelerator does.
It works only while a face of the block can still grow a bud.

**Role file parameter:** `interval_ticks` (10).

### Brew Stoker

**Types:** Fire. **Level:** any. **Move:** Ember.

**Target:** a brewing stand.

The Brew Stoker keeps the stand fueled, as if it held blaze powder. It works only while the stand holds an
ingredient that can brew at least one of its bottles. It never takes blaze powder from the fuel slot. When
it stops, the stand never has more fuel than it had before the Brew Stoker started.

A brewing stand takes one worker: a Brew Stoker or a Brewer. A Cobblemon that fits both roles, for example a
Fire and Poison type, works a stand as a Brew Stoker.

### Brewer

**Types:** Poison. **Level:** any. **Move:** Poison Gas.

**Target:** a brewing stand.

The Brewer makes each brew finish in 200 ticks (10 seconds), not 400 ticks. It works only while a brew runs.
It never changes the stand's fuel, so the stand needs its own blaze powder.

### Cultivator

**Types:** Ground. **Level:** the pace grows with level. **Move:** Mud Sport.

**Target:** a crop in the tag `#cobblemon_dynamics:cultivator_targets` that bone meal can grow. See [Farming](#farming).

A Ground-type gives its crop the effect of bone meal, with no bone meal item. Each use costs 100 ticks of Work
Stamina, on top of the drain of work. A worker applies ceil(30 × L ÷ 100) uses per minute, where L is its
level: 1 at level 1, 15 at level 50 and 30 at level 100. Green sparkles show each use, with no sound. The
Cultivator stops when the crop is fully grown.

**Role file parameters:** `uses_per_minute_at_level_100` (30) and `stamina_ticks_per_use` (100).

### Harvester

**Types:** Grass. **Level:** any. **Move:** Razor Leaf.

**Target:** a fully grown crop in the tag `#cobblemon_dynamics:harvester_targets`. A Harvester never takes an
apricorn. See [Farming](#farming).

A Grass-type harvests its crop 20 ticks after it starts work. The harvest drops on the ground, on the side of
the crop that faces the worker. A crop that the harvest breaks is replanted from its own drops: one seed, or one
crop item, leaves the harvest. A bush or a vine keeps its plant, and only its fruit comes off. The harvest makes
no sound.

**Role file parameter:** `harvest_ticks` (20).

### Pump

**Types:** Water. **Level:** the rate grows with level. **Move:** Water Gun. **Assignment:** Work Leash only.

**Target:** any block that accepts water through NeoForge's fluid capability, for example a Create fluid
tank or basin, and a vanilla cauldron.

The Pump fills its target with 5 × L mB of water per tick, where L is its level. A level 1 Pump gives 5 mB
per tick, and a level 100 Pump gives 500 mB per tick. In a cauldron, each 333 mB raises the water by one
level, up to a full cauldron. The Pump works only while the target accepts water. A tank that holds another
fluid does not, and a cauldron accepts water only while it is empty or holds water below the top level.

The Pump needs a binding, so that it never fills a tank that you keep for another fluid.

**Role file parameter:** `mb_per_level` (5).

### Muse

**Types:** Fairy. **Level:** the bonus grows with level. **Move:** Fairy Wind. **Assignment:** Work Leash,
or Quick Work at an enchanting table.

**Target:** an enchanting table.

While a player has the table's enchanting menu open, the Muse adds ceil(15 × L ÷ 100) bookshelves to the
table, where L is its level. The table never counts more than 15 bookshelves in all. While nobody uses the
table, the Muse waits in Standby. The open menu shows the new offers as soon as the Muse starts, also for an
item that the player put in first.

With Apothic Enchanting installed, the Muse adds Quanta and no bookshelves, because Apothic Enchanting's table
reads no bookshelf count. The Muse adds ceil(45 × L ÷ 100) Quanta to the table's stats, as a Quanta shelf does.
A level 100 Muse takes a table without Quanta shelves from 15% to 60% Quanta. The table never counts more than
100% Quanta.

| Level | Bookshelves | Quanta with Apothic Enchanting |
| --- | --- | --- |
| 1 | 1 | 1 |
| 10 | 2 | 5 |
| 25 | 4 | 12 |
| 50 | 8 | 23 |
| 75 | 12 | 34 |
| 100 | 15 | 45 |

Fairy-types heal by default, so the Muse needs a binding or a Quick Work order. A Muse follows the worker
rule of Work Stamina and heals no ally.

**Role file parameters:** `bookshelves_at_level_100` (15) and `quanta_at_level_100` (45).

### Telekinesis

**Types:** Psychic. **Level:** the rate grows with level. **Move:** Kinesis. **Assignment:** Work Leash only.

**Target:** a source and a destination: two containers that hold items through NeoForge's item capability,
such as chests, barrels, furnaces, hoppers, Create's item vaults, depots and chutes, and AE2's ME Interfaces.
A double chest or a Create item vault is one container.

A Psychic-type moves items from one container to another. Telekinesis is leash only, so a worker never starts
it on its own. Its binding takes three clicks:

1. Select the Cobblemon with the Work Leash.
2. Right-click the source container on the face to take items from. The source must sit inside the pasture's
   work area.
3. Right-click the destination container on the face to put items into. The destination may sit anywhere
   within 32 blocks of the pasture block on each axis. It must be another container than the source.

The action bar asks for the destination after the first click. A right-click on air, or 30 seconds, clears
the first click. The worker stands at the source and moves ceil(64 × L ÷ 100) items every second, where L is
its level: 1 at level 1, 32 at level 50 and 64 at level 100. Several workers can share a source or a
destination, up to 5 Cobblemon of the Magnet and Telekinesis roles on each container. So several workers can
fill one AE2 ME Interface, and a worker can empty the chest that a [Magnet](#magnet) fills. Each worker keeps
its own source and destination. The faces count: a furnace takes fuel through its sides and input through
its top. A worker waits in Standby while the source is empty or the destination is
full. The binding clears when the source or the destination is broken or replaced.

**Role file parameters:** `items_per_second_at_level_100` (64) and `destination_range` (32).

### Magnet

**Types:** Steel. **Level:** the rate grows with level. **Move:** the `special` pose, with metal sparks at the
container. **Assignment:** Work Leash only.

**Target:** a container that holds items through NeoForge's item capability. A double chest or a Create item
vault is one container.

A Steel-type collects dropped items into a container. The Magnet is leash only: select the Cobblemon with the
Work Leash, then right-click the container. The worker stands at the container and collects from the whole
work area of its pasture, ceil(64 × L ÷ 100) items every second, where L is its level: 1 at level 1, 32 at
level 50 and 64 at level 100. It leaves an item alone while the item still has a pickup delay, so a drop that
a player just threw stays theirs for a moment. What does not fit stays on the ground. A worker waits in
Standby while no item lies in range or the container is full.

Up to 5 Cobblemon of the Magnet and Telekinesis roles can share one container. Bind a second Magnet to the
same chest to collect faster, or bind a [Telekinesis](#telekinesis) worker with that chest as its source to
move the loot on to another container. Each Cobblemon needs its own free work spot beside the container.

**Role file parameter:** `items_per_second_at_level_100` (64).

### Fan Catalyst

**Types:** Fire, Water or Ghost. **Level:** any. **Move:** Fire Spin, Bubble or Will-O-Wisp. **Assignment:**
Work Leash only. **Needs:** Create.

**Target:** an encased fan that faces sideways. A fan that faces up or down cannot take a worker, because a
worker in its air current would block it.

A Fire-, Water- or Ghost-type stands in the block in front of the fan and takes the place of the lava, the
water or the soul fire there. The fan then processes the items in its air current as Create does, at
Create's own speed. Select the Cobblemon with the Work Leash, then right-click the fan. The block in front of
the fan must have room for the Cobblemon. If it has none, or if the fan faces up or down, the action bar shows
"No room in front of the fan". No fan pushes, burns or harms the worker while it holds its claim. A worker
waits in Standby while the fan stands still or no item is in its air current.

Each type has its own role file. A Cobblemon with two of these types works as the catalyst of its primary
type: a Fire and Ghost type blasts or smokes, and a Ghost and Fire type haunts. A role file with a higher
`priority` wins before the type order.

**Role file parameter:** `catalyst` (`fire`, `water` or `ghost`).

#### Fan Catalyst Fire

Blasting by default. To switch the fan to smoking, hold the Work Leash, then sneak and right-click the fan
while its worker Works. Do it again to switch it back. The action bar shows the new mode. The player who
bound the worker, the owner of the worker's pasture and operators can switch it. Each fan keeps its own
mode, also through restarts.

#### Fan Catalyst Water

Washing, as Create's bulk washing does.

#### Fan Catalyst Ghost

Haunting, as Create's bulk haunting does.

### Tailwind

**Types:** Flying. **Level:** the bonus grows with level. **Move:** Gust. **Assignment:** Work Leash only.
**Needs:** Create.

**Target:** a windmill bearing.

A Flying-type adds wind to a Create windmill. Select the Cobblemon with the Work Leash, then right-click the
windmill bearing. While the worker Works, the windmill counts floor(S × L ÷ 100) more sails, where S is its
number of real sails and L the worker's level. Real and added sails together stay at 128 or less. So a
64-sail windmill turns at 12 RPM with a level 50 worker, and at 16 RPM, Create's maximum, with a level 100
worker. The added sails end within 2 ticks of the end of the work. A windmill still needs 8 real sails to
assemble.

The worker stands beside or behind the bearing, never where the sails turn. Under a windmill that faces up,
the worker needs room below the sails, so assemble the windmill before you bind. A worker waits in Standby
while the windmill is not assembled.

**Role file parameters:** `bonus_per_level_of_real_sails` (0.01) and `max_total_sails` (128).

## Configuration

A key such as `workArea.horizontalRadius` is the key `horizontalRadius` in the section `[workArea]` of the
file.

### Server config

The server config is `config/cobblemon_dynamics-server.toml`. NeoForge writes it at the first start. A copy
in the `serverconfig/` folder of a world overrides it for that world.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `workArea.horizontalRadius` | 8 | 2 to 32 | Blocks that the work area reaches from the pasture block on the x and z axes. Never more than Cobblemon's `pastureMaxWanderDistance`. |
| `workArea.verticalRadius` | 6 | 2 to 32 | Blocks that the work area reaches up and down from the pasture block. Never more than Cobblemon's `pastureMaxWanderDistance`. |
| `engine.scanIntervalTicks` | 40 | 20 to 1,200 | Ticks between the starts of two scans of one pasture. |
| `engine.maxBlockChecksPerTick` | 512 | 64 to 4,096 | Most block positions that the scan of one pasture checks in one tick. |
| `engine.travelTimeoutTicks` | 600 | 100 to 6,000 | Ticks that a worker has to reach its target, its bed or the ally that it heals. |
| `engine.retryWaitTicks` | 6,000 | 0 to 72,000 | Ticks that a worker skips a target after a missed arrival, and that a bound worker waits before it tries again. |
| `engine.standbyMoveTicks` | 200 | 20 to 6,000 | Ticks that an auto-assigned worker stays in Standby before it can move to a target that needs work. |
| `engine.effectBroadcastRadius` | 64 | 16 to 128 | Blocks around a worker within which players receive its work effects. |
| `workMode.toggleCooldownTicks` | 10 | 0 to 200 | Ticks after a player's accepted Work Mode change during which the server ignores that player's next change. |
| `leash.pendingSelectionTicks` | 600 | 100 to 6,000 | Ticks that the Work Leash keeps a pending source before it clears it. A [Telekinesis](#telekinesis) binding has a pending source between its source click and its destination click. |
| `logistics.maxWorkersPerContainer` | 5 | 1 to 16 | Most Cobblemon of the [Magnet](#magnet) and [Telekinesis](#telekinesis) roles that use one container at once, together. A double chest or a Create item vault is one container. A Telekinesis destination counts only when you bind. A lower value clears no binding: a Cobblemon above the new limit keeps its binding, and it waits for a free place before it starts again. |
| `quickWork.enabled` | `true` | `true`, `false` | Turns [Quick Work](#quick-work) on or off. With `false`, a send-out never starts Quick Work. |
| `quickWork.targetRange` | 16 | 2 to 24 | Blocks that your view reaches to find the block for Quick Work. Never more than `quickWork.leashRange`. |
| `quickWork.leashRange` | 16 | 2 to 24 | Blocks between your eyes and the block at which Quick Work stops. |
| `stamina.ticksPerLevel` | 3,600 | 20 to 72,000 | Ticks of Working per level from 100% to 0%. The worker rule takes the same time to recover. |
| `stamina.healerRecoveryMinutesAtLevel1` | 1,200 | 1 to 10,000 | Minutes that a level 1 healer takes from 0% to 100%. |
| `stamina.healerRecoveryMinutesAtLevel100` | 120 | 1 to 10,000 | Minutes that a level 100 healer takes from 0% to 100%. |
| `stamina.flushIntervalTicks` | 600 | 20 to 12,000 | Ticks between two saves of a worker's Work Stamina into the Cobblemon's data. A change of state also saves it. |
| `beds.speedFactors` | `[2, 3, 4, 8]` | 4 values, each 1 to 64 | The number that each bed tier divides the recovery time by, Hay Bed first. A list with the wrong number of values gives the defaults. |
| `heal.cost` | 0.25 | 0.01 to 1.0 | Share of its Work Stamina that a heal costs the healer. A healer needs at least this share to start a heal. |
| `heal.lowStaminaThreshold` | 0.25 | 0.0 to 1.0 | Share of Work Stamina under which an ally that is not Tired needs a heal. |
| `roles.disabled` | `[]` | role ids | Roles to turn off, for example `["cobblemon_dynamics:stoker"]`. The [Roles](#roles) table lists the ids. |
| `compat.cobbleworkers` | `true` | `true`, `false` | With Cobbleworkers installed, mark the workers of Work Mode pastures as resting for Cobbleworkers. |
| `compat.cobblebase` | `true` | `true`, `false` | With Cobblebase installed, stop its pasture behavior in Work Mode pastures. |

### Client config

The client config is `config/cobblemon_dynamics-client.toml`.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `hud.headBarRange` | 16 | 4 to 64 | Blocks within which workers show their head bars. |
| `hud.panelBars` | `true` | `true`, `false` | Show the Work Stamina bars and the state icons in the pasture panel. |

### Role files

Each role is a JSON file in a data pack, at `data/<namespace>/cobblemon_dynamics/roles/<role>.json`. The
built-in files use the namespace `cobblemon_dynamics`, and a data pack file at the same path replaces one.
A role file holds the role's types, its auto-assign flag, its priority, its range, its handler, the handler's
parameters and its effect. The range is the largest distance from the work spot to the target. The role
sections above name each parameter. The optional key `quick_work` (default `false`) lets a role whose
`auto_assign` is false take Quick Work orders, if the role has one target. Such an order starts while the target
needs no work, and its Cobblemon waits there with no time limit. Only the Muse's file sets it.

The `effect` object holds these keys:

| Key | Default | Meaning |
| --- | --- | --- |
| `animations` | none | The animation names to try, in order. The worker plays the first one that its model has with no sound. A file that names `cry` does not load, because the cry animation plays the Cobblemon's cry. |
| `actor_particles` | `[]` | Cobblemon particle ids that fly from the worker to the target. |
| `target_particles` | `[]` | Cobblemon particle ids that burst at the target. |
| `idle_ticks` | 40 | The ticks that the worker stands idle after its animation ends, before it plays the move again. |
| `loop_ticks` | 40 | The interval of a worker whose model has none of the animations. It shows only the particles. |

Work is silent, so an effect has no sound key. A file whose effect names a `sound` does not load.

- `/reload` applies changed role files without a restart.
- A file that does not parse is skipped. The log then holds one error that names the file.
- A file that names an unknown handler turns its role off. The log then holds one line that names the role.
- If a role's code fails in the game, the mod turns off every role that uses the same code, and the log holds
  one warning. `/reload` or a server restart turns those roles on again.

## Commands

All three commands need permission level 2 (operator).

| Command | Result |
| --- | --- |
| `/cobblemondynamics claims` | Lists each claim and each binding. A claim line reads `claim <kind> <target> <worker> (<UUID>)`, where the kind is `work`, `bed` or `heal`. A binding line reads `binding <target> <worker> (<UUID>)`. With neither, the reply is "No claims and no leash bindings". |
| `/cobblemondynamics stamina <target>` | Shows one Cobblemon's Work Stamina, state and recovery rule, for example "Charmander: 42.5% Work Stamina, Working, worker rule". |
| `/cobblemondynamics unbind <pos>` | Clears every binding on the block at `<pos>` in your dimension. Any block of a large water wheel names the whole wheel, as with the Work Leash. Each line reads "Cleared the binding of {Cobblemon} at {target}", and the player who bound the Cobblemon gets the chat message "{Cobblemon} lost its work binding: an operator cleared it". With no binding there, the reply is "No leash binding at {target}". |

A claim gives one worker one target for now: a block to work, a bed or an ally to heal. Claims are never
saved, so the workers make them again after a restart. In a line, a block target is its dimension and its
position, and an ally is its name.

The `<target>` of the stamina command is one entity, for example
`@e[type=cobblemon:pokemon,sort=nearest,limit=1]`. The percentage is rounded down to one decimal. For a
Cobblemon that is not a worker, the state reads "not in Work Mode". The value is then its saved value plus the
recovery of its time away.

The `<pos>` of the unbind command is a block position, for example the coordinates of the furnace that a worker
is bound to. The command works in the dimension that it runs in, so from the server console a block in the
Nether needs `/execute in minecraft:the_nether run cobblemondynamics unbind <pos>`.

## Compatibility

- **Cobbleworkers.** In a pasture with Work Mode on, the mod marks each worker as resting for
  Cobbleworkers, so Cobbleworkers gives it no job. The mark clears when Work Mode turns off or the Cobblemon
  leaves. A rest mark that a player set stays. The server config key `compat.cobbleworkers` turns this off.
- **Cobblebase.** In a pasture with Work Mode on, Cobblebase's pasture behavior stops. This covers its idle
  and sleep behavior, wandering, moves into water, jobs, teleports back to the pasture and passive
  experience. Pastures with Work Mode off keep Cobblebase as usual. If a Cobblebase version lacks a method
  that the mod hooks, the log holds one warning, and the game goes on. The server config key
  `compat.cobblebase` turns this off.
- **FTB Chunks, Open Parties and Claims and MineColonies.** Quick Work asks the claim mod whether you may
  right-click the block, before the work starts and every 20 ticks while it runs. The workers of a Work Mode
  pasture ask whether the pasture's owner may right-click each machine, also while that player is offline.
  See [Land claims](#land-claims). If the claim mod fails with an error, Quick Work and the pastures refuse
  the work, and the log holds one warning.
- **Other mods.** The Stoker works any furnace that extends the vanilla furnace. The Generator works any
  block that accepts Forge Energy, and the Pump any block that accepts water, through NeoForge's
  capabilities.
