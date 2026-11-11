# Cobblemon Dynamics player guide

Cobblemon Dynamics puts your pastured Pokémon to work. Turn on Work Mode for a pasture. Each of its Pokémon
then finds a block that fits one of its types, walks to it and works it. For example, a Fire-type keeps a
furnace lit, a Water-type turns a Create water wheel, and an Electric-type pushes Forge Energy into a machine.
A party Pokémon that you send out while you look at a block can work that block too, with [Quick Work](#quick-work).

Install the mod on the client and on the server. The [README](../README.md#requirements) lists the versions
of Minecraft, NeoForge, Cobblemon and the optional mods.

## Quick start

1. Place a Cobblemon pasture block.
2. Place the blocks that you want worked near the pasture block. The [work area](#work-area) reaches 8
   blocks out on the x and z axes, and 6 blocks up and down.
3. Put your Pokémon in the pasture.
4. Right-click the pasture block to open its panel.
5. Click the **Work Mode: Off** button above **Recall**. The button then reads **Work Mode: On**.
6. Place beds in the same area, so that Tired workers recover faster.

Each Pokémon then walks to a block that one of its roles fits, and works it.

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

While Work Mode is on, each Pokémon in the pasture is a worker, if it has an owner and has not fainted. Wild
Pokémon never work. Pokémon in a party, or sent out from one, never work. Cobblemon's own limit of Pokémon
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
- A Pokémon fits a role through its first or its second type. A Pokémon with two types can do the roles of
  both types, one role at a time.
- Only one worker can work a target at a time, across all pastures and all players.
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
Pokémon then act as normal pastured Pokémon. Their bindings stay, and the bound workers go back to their
targets when you turn Work Mode on again. When the pasture block breaks, the bindings of its Pokémon clear.

### Worker states

Each worker is in one of these states:

| State | Meaning |
| --- | --- |
| Idle | The worker has no target. It acts as a normal pastured Pokémon. |
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

The Work Leash binds one of your pastured Pokémon to a target that you choose.

**Recipe:** shapeless, 1 Lead and 1 Poké Ball.

### Bind a Pokémon to a target

1. Hold the Work Leash in your main hand.
2. Right-click your Pokémon in a pasture with Work Mode on. The action bar shows "Selected {Pokémon}", and
   the leash's tooltip names the Pokémon.
3. Right-click the target. The action bar shows "Bound {Pokémon} to {target}". The target does not open its
   own screen.

The leash forgets its selection after the bind. To move a bound worker to a new target, bind it again. The
new binding replaces the old one. A click on any block of a large water wheel binds the whole wheel.

When a bind fails, the action bar shows why:

| Message | Cause |
| --- | --- |
| "Not in a Work Mode pasture" | The Pokémon is not in a pasture with Work Mode on. |
| "No role fits" | None of the Pokémon's roles works on the block. |
| "Outside its pasture's work area" | The block is outside the work area of the Pokémon's pasture. |
| "Already assigned to {Pokémon}" | Another of your Pokémon has a binding on the block. |
| "Already assigned to another player's {species}" | A Pokémon of another player has a binding on the block. The message names its species, never its nickname. |
| "Select a Pokémon first" | The selected Pokémon is no longer yours, for example after a trade or a release. |
| "Destination must differ from the source" | The second click of a [Telekinesis](#telekinesis) binding is on its source container. |
| "Too far from its pasture" | The destination of a Telekinesis binding is more than 32 blocks from the pasture block on an axis. |

### What a binding does

- The binding reserves the target for its worker. The reservation holds while the worker sleeps and while
  Work Mode is off.
- A new binding takes the target away from an auto-assigned worker at once.
- A bound worker never moves to another target.
- The binding stays with the Pokémon through server restarts.
- A bound worker that is Tired starts on its target after it has recovered. After each rest, a bound worker
  goes back to its target.
- If a bound worker cannot reach its target within 30 seconds, the action bar shows "{Pokémon} cannot
  reach {target}". The worker keeps its binding, waits 5 minutes in Standby and then tries again.

### Clear a binding

Sneak and right-click your bound Pokémon with the Work Leash. The action bar shows "Cleared the binding of
{Pokémon}". A click with the leash on your own Pokémon never opens Cobblemon's interaction wheel and never
mounts the Pokémon.

A binding also clears in the cases below. The player who bound the Pokémon then gets the chat message
"{Pokémon} lost its work binding: {reason}". If that player is offline, the message waits for their next login. The
server keeps up to 10 such messages per player, the newest, and a server restart drops them.

| Case | Reason in the message |
| --- | --- |
| The target breaks or is replaced, is picked up with a Create wrench, or moves into a Create contraption. | its target was broken or replaced |
| The destination of a Telekinesis binding breaks or is replaced. | its destination was broken or replaced |
| The Pokémon goes back to the PC or the party. | it left its pasture |
| The Pokémon moves to another pasture. | it moved to another pasture |
| The Pokémon is released. | it was released |
| The Pokémon goes to a new owner, for example in a trade. | it has a new owner |
| The Pokémon evolves into a form that no role on its target fits. | its new form cannot do the job |
| The pasture block breaks. | its pasture block is gone |
| An operator runs [`/cobblemondynamics unbind`](#commands) on its target. | an operator cleared it |

A binding stays when its target is in an unloaded chunk.

In a shared pasture, another player can bind one of their own Pokémon to your block, and the binding then
reserves that block. Cobblemon lets only a Pokémon's owner take it out of a pasture, so if that player does not
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

A party Pokémon can work one block on demand, with no pasture and no Work Leash.

1. Select a party Pokémon whose type fits a role, for example a Fire-type for a furnace.
2. Look at the block, up to 16 blocks away.
3. Press **R** to send the Pokémon out, as you always do.

The Pokémon walks to the block and works it, as a worker in a pasture does. Its Work Stamina drains while it works. The
action bar tells you when the work starts, and when and why it stops.

Quick Work uses only the roles that a pasture gives out on its own: the Stoker, Wheel Driver, Cranker, Superheater,
Generator, Crystal Tender, Brew Stoker and Brewer. When only a Work Leash role of the Pokémon fits the block, for example
Telekinesis for a Psychic-type at a chest, the action bar shows "Chest needs the Work Leash". A Pokémon with a Fairy type
does no Quick Work, because it is a healer.

### When Quick Work does not start

The Pokémon comes out as usual, with no work, and the action bar tells you why, when:

- another Pokémon works the block already, or a Work Leash binding holds it
- the Pokémon is Tired
- the block needs no work now, for example a furnace with nothing to smelt
- the Pokémon cannot reach the block
- you may not use the block, for example inside spawn protection, or in a land claim that does not let you right-click
  the block (see [Quick Work in land claims](#quick-work-in-land-claims))

### When Quick Work stops

The work stops for good when:

- you go more than 16 blocks from the block
- you recall the Pokémon, or it faints
- the Pokémon gets Tired. It then recovers in your party at the rate with no bed.
- the block breaks or changes, or a Work Leash binding takes it
- the block needs no work for 10 seconds
- the Pokémon cannot reach the block within 30 seconds
- you log out or go to another dimension
- the Pokémon starts a battle
- you may no longer use the block, for example when you leave the party that owns its land claim, or when another player
  claims its chunk

The Pokémon then follows you again. It does not look for other work. To work the block again, recall the Pokémon and send
it out again while you look at the block.

### Quick Work in land claims

With FTB Chunks or Open Parties and Claims on the server, Quick Work follows their land claims. You can Quick Work a block
in a land claim only if the claim lets you right-click that block with an empty hand. The claim mod decides, with its own
rules:

- **FTB Chunks.** The members of the team that owns the claim can. Its allies can too, while the team's setting for block
  interaction is "allies", which is the default. A team can set it to "public" for every player, or to "private" for its
  members only.
- **Open Parties and Claims.** The owner of the claim can, and by default the members of the owner's party can too. The
  members of an allied party can only if the owner sets `claims.protection.exceptions.fullAccess` to `A` in the owner's
  player config.

A refused send-out stays a normal one, and the action bar shows a message such as "You may not use Furnace". While the
Pokémon works, Quick Work checks your right again every 20 ticks (1 second), and the work stops when the right is gone.

Other protection mods that only stop right-clicks do not see Quick Work, because you never click the block. A server owner
can turn Quick Work off with `quickWork.enabled`. The keys `quickWork.targetRange` and `quickWork.leashRange` change the two
distances of 16 blocks.

## Work Stamina

Work Stamina measures how long a worker can keep working. It goes from 0% to 100%.

### Drain and recovery

- A worker loses Work Stamina only while it is Working. In every other state, it recovers.
- A full drain takes 3 minutes of Working per level. A level 10 worker works for 30 minutes, and a level 100
  worker works for 5 hours. Legendary and mythical Pokémon get no extra time.
- A worker recovers as fast as it drains: from 0% to 100% in 3 minutes per level. A [bed](#beds) makes the
  recovery faster.
- Healers recover by their own rule (see [Healer recovery](#healer-recovery)).
- A Pokémon starts at 100% when it first enters a pasture with Work Mode on. A level-up keeps the percentage.
- Work Stamina stays with the Pokémon. Time in the PC, in a party or in an unloaded chunk counts as rest,
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

- **Pasture panel.** Each Pokémon's row shows a Work Stamina bar and a state icon. The bar is green from
  50%, yellow from 25% and red below 25%. Hover over the bar or the icon to see "{N}% Work Stamina" and the
  state name. The panel updates the values every second. The client config key `hud.panelBars` hides the
  bars.
- **Head bars.** Hold the Work Leash in your main hand (see [Work Leash](#while-you-hold-the-work-leash)).
- **Command.** Operators can run `/cobblemondynamics stamina` (see [Commands](#commands)).

## Beds

A bed lets a Tired worker recover faster. A bed is a low block, 6/16 of a block high. A Pokémon of any size
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

- It heals only Pokémon in its own pasture. It never heals itself or a Fairy-type.
- In a pasture that several players share, it heals the other players' workers too, because a shared pasture
  works as one team.
- It picks the nearest Tired ally first.
- When no ally is Tired, it picks the ally with the lowest Work Stamina under 25%.
- A healer heals one ally at a time, and an ally gets one healer at a time.

### A heal

- The healer walks to within 2 blocks of the ally and heals it. The ally goes to 100% at once, whatever the
  levels of the two Pokémon.
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

## Roles

A role is a job that Pokémon of some types do on one kind of block. Auto-assigned workers take the roles
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
| [Pump](#pump) | `cobblemon_dynamics:pump` | Water | A block that accepts water, cauldron | Work Leash | |
| [Muse](#muse) | `cobblemon_dynamics:muse` | Fairy | Enchanting table | Work Leash | |
| [Telekinesis](#telekinesis) | `cobblemon_dynamics:telekinesis` | Psychic | Two containers: a source and a destination | Work Leash | |
| [Magnet](#magnet) | `cobblemon_dynamics:magnet` | Steel | A container | Work Leash | |
| [Fan Catalyst Fire](#fan-catalyst-fire) | `cobblemon_dynamics:fan_catalyst_fire` | Fire | Encased fan that faces sideways | Work Leash | Create |
| [Fan Catalyst Water](#fan-catalyst-water) | `cobblemon_dynamics:fan_catalyst_water` | Water | Encased fan that faces sideways | Work Leash | Create |
| [Fan Catalyst Ghost](#fan-catalyst-ghost) | `cobblemon_dynamics:fan_catalyst_ghost` | Ghost | Encased fan that faces sideways | Work Leash | Create |
| [Tailwind](#tailwind) | `cobblemon_dynamics:tailwind` | Flying | Windmill bearing | Work Leash | Create |

Level changes the output of the Generator, the Pump, the Muse, Telekinesis, the Magnet and Tailwind. The
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
The factor m is 2 for a legendary or mythical Pokémon, and 1 for any other.

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

A brewing stand takes one worker: a Brew Stoker or a Brewer. A Pokémon that fits both roles, for example a
Fire and Poison type, works a stand as a Brew Stoker.

### Brewer

**Types:** Poison. **Level:** any. **Move:** Poison Gas.

**Target:** a brewing stand.

The Brewer makes each brew finish in 200 ticks (10 seconds), not 400 ticks. It works only while a brew runs.
It never changes the stand's fuel, so the stand needs its own blaze powder.

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

**Types:** Fairy. **Level:** the bonus grows with level. **Move:** Fairy Wind. **Assignment:** Work Leash
only.

**Target:** an enchanting table.

While a player has the table's enchanting menu open, the Muse adds ceil(15 × L ÷ 100) bookshelves to the
table, where L is its level. The table never counts more than 15 bookshelves in all. While nobody uses the
table, the Muse waits in Standby. The open menu shows the new offers as soon as the Muse starts, also for an
item that the player put in first.

| Level | Bookshelves |
| --- | --- |
| 1 | 1 |
| 10 | 2 |
| 25 | 4 |
| 50 | 8 |
| 75 | 12 |
| 100 | 15 |

Fairy-types heal by default, so the Muse needs a binding. A Muse follows the worker rule of Work Stamina and
heals no ally.

**Role file parameter:** `bookshelves_at_level_100` (15).

### Telekinesis

**Types:** Psychic. **Level:** the rate grows with level. **Move:** Kinesis. **Assignment:** Work Leash only.

**Target:** a source and a destination: two containers that hold items through NeoForge's item capability,
such as chests, barrels, furnaces, hoppers, Create's item vaults, depots and chutes, and AE2's ME Interfaces.
A double chest or a Create item vault is one container.

A Psychic-type moves items from one container to another. Telekinesis is leash only, so a worker never starts
it on its own. Its binding takes three clicks:

1. Select the Pokémon with the Work Leash.
2. Right-click the source container on the face to take items from. The source must sit inside the pasture's
   work area.
3. Right-click the destination container on the face to put items into. The destination may sit anywhere
   within 32 blocks of the pasture block on each axis. It must be another container than the source.

The action bar asks for the destination after the first click. A right-click on air, or 30 seconds, clears
the first click. The worker stands at the source and moves ceil(64 × L ÷ 100) items every second, where L is
its level: 1 at level 1, 32 at level 50 and 64 at level 100. Only the source is reserved, so several workers
can fill one destination, such as an AE2 ME Interface. The faces count: a furnace takes fuel through its
sides and input through its top. A worker waits in Standby while the source is empty or the destination is
full. The binding clears when the source or the destination is broken or replaced.

**Role file parameters:** `items_per_second_at_level_100` (64) and `destination_range` (32).

### Magnet

**Types:** Steel. **Level:** the rate grows with level. **Move:** the `special` pose, with metal sparks at the
container. **Assignment:** Work Leash only.

**Target:** a container that holds items through NeoForge's item capability. A double chest or a Create item
vault is one container.

A Steel-type collects dropped items into a container. The Magnet is leash only: select the Pokémon with the
Work Leash, then right-click the container. The worker stands at the container and collects from the whole
work area of its pasture, ceil(64 × L ÷ 100) items every second, where L is its level: 1 at level 1, 32 at
level 50 and 64 at level 100. It leaves an item alone while the item still has a pickup delay, so a drop that
a player just threw stays theirs for a moment. What does not fit stays on the ground. A worker waits in
Standby while no item lies in range or the container is full.

**Role file parameter:** `items_per_second_at_level_100` (64).

### Fan Catalyst

**Types:** Fire, Water or Ghost. **Level:** any. **Move:** Fire Spin, Bubble or Will-O-Wisp. **Assignment:**
Work Leash only. **Needs:** Create.

**Target:** an encased fan that faces sideways. A fan that faces up or down cannot take a worker, because a
worker in its air current would block it.

A Fire-, Water- or Ghost-type stands in the block in front of the fan and takes the place of the lava, the
water or the soul fire there. The fan then processes the items in its air current as Create does, at
Create's own speed. Select the Pokémon with the Work Leash, then right-click the fan. The block in front of
the fan must have room for the Pokémon. If it has none, or if the fan faces up or down, the action bar shows
"No room in front of the fan". No fan pushes, burns or harms the worker while it holds its claim. A worker
waits in Standby while the fan stands still or no item is in its air current.

Each type has its own role file. A Pokémon with two of these types works as the catalyst of its primary
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

A Flying-type adds wind to a Create windmill. Select the Pokémon with the Work Leash, then right-click the
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
| `leash.pendingSelectionTicks` | 600 | 100 to 6,000 | Ticks that the Work Leash keeps a pending source before it clears it. No role in this guide uses a pending source. |
| `quickWork.enabled` | `true` | `true`, `false` | Turns [Quick Work](#quick-work) on or off. With `false`, a send-out never starts Quick Work. |
| `quickWork.targetRange` | 16 | 2 to 24 | Blocks that your view reaches to find the block for Quick Work. Never more than `quickWork.leashRange`. |
| `quickWork.leashRange` | 16 | 2 to 24 | Blocks between your eyes and the block at which Quick Work stops. |
| `stamina.ticksPerLevel` | 3,600 | 20 to 72,000 | Ticks of Working per level from 100% to 0%. The worker rule takes the same time to recover. |
| `stamina.healerRecoveryMinutesAtLevel1` | 1,200 | 1 to 10,000 | Minutes that a level 1 healer takes from 0% to 100%. |
| `stamina.healerRecoveryMinutesAtLevel100` | 120 | 1 to 10,000 | Minutes that a level 100 healer takes from 0% to 100%. |
| `stamina.flushIntervalTicks` | 600 | 20 to 12,000 | Ticks between two saves of a worker's Work Stamina into the Pokémon's data. A change of state also saves it. |
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
sections above name each parameter.

The `effect` object holds these keys:

| Key | Default | Meaning |
| --- | --- | --- |
| `animations` | none | The animation names to try, in order. The worker plays the first one that its model has with no sound. A file that names `cry` does not load, because the cry animation plays the Pokémon's cry. |
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
| `/cobblemondynamics stamina <target>` | Shows one Pokémon's Work Stamina, state and recovery rule, for example "Charmander: 42.5% Work Stamina, Working, worker rule". |
| `/cobblemondynamics unbind <pos>` | Clears every binding on the block at `<pos>` in your dimension. Any block of a large water wheel names the whole wheel, as with the Work Leash. Each line reads "Cleared the binding of {Pokémon} at {target}", and the player who bound the Pokémon gets the chat message "{Pokémon} lost its work binding: an operator cleared it". With no binding there, the reply is "No leash binding at {target}". |

A claim gives one worker one target for now: a block to work, a bed or an ally to heal. Claims are never
saved, so the workers make them again after a restart. In a line, a block target is its dimension and its
position, and an ally is its name.

The `<target>` of the stamina command is one entity, for example
`@e[type=cobblemon:pokemon,sort=nearest,limit=1]`. The percentage is rounded down to one decimal. For a
Pokémon that is not a worker, the state reads "not in Work Mode". The value is then its saved value plus the
recovery of its time away.

The `<pos>` of the unbind command is a block position, for example the coordinates of the furnace that a worker
is bound to. The command works in the dimension that it runs in, so from the server console a block in the
Nether needs `/execute in minecraft:the_nether run cobblemondynamics unbind <pos>`.

## Compatibility

- **Cobbleworkers.** In a pasture with Work Mode on, the mod marks each worker as resting for
  Cobbleworkers, so Cobbleworkers gives it no job. The mark clears when Work Mode turns off or the Pokémon
  leaves. A rest mark that a player set stays. The server config key `compat.cobbleworkers` turns this off.
- **Cobblebase.** In a pasture with Work Mode on, Cobblebase's pasture behavior stops. This covers its idle
  and sleep behavior, wandering, moves into water, jobs, teleports back to the pasture and passive
  experience. Pastures with Work Mode off keep Cobblebase as usual. If a Cobblebase version lacks a method
  that the mod hooks, the log holds one warning, and the game goes on. The server config key
  `compat.cobblebase` turns this off.
- **FTB Chunks and Open Parties and Claims.** Quick Work asks the claim mod whether you may right-click
  the block, before the work starts and every 20 ticks while it runs. See
  [Quick Work in land claims](#quick-work-in-land-claims). If the claim mod fails with an error, Quick
  Work refuses the work, and the log holds one warning.
- **Work Mode pastures and land claims.** The workers of a pasture do not ask claim mods. A pasture near
  the border of another player's claim can work machines inside that claim, so keep its work area out of
  other players' claims.
- **Other mods.** The Stoker works any furnace that extends the vanilla furnace. The Generator works any
  block that accepts Forge Energy, and the Pump any block that accepts water, through NeoForge's
  capabilities.
