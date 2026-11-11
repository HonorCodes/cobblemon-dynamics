# ADR-0013: Quick Work for party Pokémon

Status: Accepted (2026-11-11)

## Context

The approver's request of 2026-11-11:

> the ability for a user to throw out a cobblemon with R as they normally do, but if their crosshair is targeting something
> that the selected cobblemon/that they are putting out can work, then it will start to work it. For example, if I have
> charmander selected, look at a smelter, then press R to bring it out, charmander should attempt to power it; with it
> leashing away from the smelter if I walk too far away. it shouldn't keep assessing for work after being leashed away and/or
> recalled; so it shouldn't take TPS for that. this is basically an on-demand use of this mod with in-party cobblemon that
> are summoned while targeting an applicable machine/thing.

> it also shouldn't work with machines that require the cobblemon dynamics leash item (like more complex behaviors, like item
> transfers, etc.)

The approver delegated the open points ("go with your recommendations all the way through"). The coordinator ruled on six of
them, and input R63 of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md) holds those rulings. Until now only pastured
Pokémon worked, and the spec sheet listed work by party Pokémon as a non-goal.

Code references below use these roots. Cobblemon is the Cobblemon 1.8.4 sources jar under `com/cobblemon/mod/`, and its data
files are in the `neoforge-1.8.4+1.21.1.jar`. Minecraft is the NeoForge 21.1.265 patched Minecraft source (the NeoForm
`sourcesWithNeoForge` output). Our code is under `src/main/kotlin/codes/honor/cobblemondynamics/`.

### Cobblemon 1.8.4: the send-out

- **Client key.** `PartySendBinding` is the key `key.cobblemon.throwpartypokemon`, R by default
  (`common/client/keybind/keybinds/PartySendBinding.kt:36-41`). It acts when the key is released (`onRelease`, lines 65-94). It
  does nothing for a spectator (line 73), toggles the battle screen in a battle (lines 75-79), and does nothing while a screen
  is open (line 81). Otherwise it traces for an entity in view (lines 103-108). With no entity to battle or talk to, it sends
  `SendOutPokemonPacket(selectedSlot)` (line 110). The packet carries only the party slot (`SendOutPokemonPacket.kt:28`).
- **Server thread.** Cobblemon registers its client-to-server payloads on a registrar that runs on the main thread
  (`neoforge/net/CobblemonNeoForgeNetworkManager.kt:27-30`, line 47). The registered handler calls the packet handler with the
  sender (`neoforge/net/NeoForgePacketInfo.kt:40-46`).
- **Packet handler.** `SendOutPokemonHandler.handle(packet, server, player)`
  (`common/net/serverhandling/storage/SendOutPokemonHandler.kt:30-51`) returns for a fainted Pokémon. For a Pokémon that is not out, it finds a position with
  `player.raycastSafeSendout(pokemon, 12.0, 5.0, ClipContext.Fluid.ANY)` (line 39) and calls
  `pokemon.sendOutWithAnimation(player, level, position)` (line 42). For a Pokémon that is out, it recalls it (lines 44-50).
- **Cobblemon's own trace.** `raycastSafeSendout` (`common/util/PlayerExtensions.kt:340-417`) traces from the player's eye
  position along the server's `xRot` and `yRot`, with `ClipContext.Block.OUTLINE`, up to 12 blocks (lines 342-352). It puts the
  Pokémon in front of the face that it hits, on the top of the block, or below the aim, and returns null when no place is safe.
- **The events.** `Pokemon.sendOut` (`common/pokemon/Pokemon.kt:799-813`) posts `CobblemonEvents.POKEMON_SENT_PRE` with
  `PokemonSentEvent.Pre(pokemon, level, position)` (line 800) before the entity exists. The event is cancelable
  (`common/api/events/CobblemonEvents.kt:160`), and a cancelable observable stops at the first subscriber that cancels it
  (`common/api/reactive/EventObservables.kt:23-38`). `sendOutWithAnimation` (`Pokemon.kt:815-926`) calls `sendOut` at once
  for a player, because only an `NPCEntity` is a `PokemonSender` (lines 832-836, `common/entity/npc/NPCEntity.kt:100`). So
  `POKEMON_SENT_PRE` fires inside the packet handler, at the key press. After `SEND_OUT_DURATION`, 1.5 s
  (`SendOutPokemonHandler.kt:26`), Cobblemon posts `CobblemonEvents.POKEMON_SENT_POST` (`CobblemonEvents.kt:163`) with
  `PokemonSentEvent.Post(pokemon, level, position, pokemonEntity)` (`Pokemon.kt:898-909`). A recall during the animation skips
  it (lines 901-904). A send-out from the shoulder posts it too (lines 964-966). The event's documentation reads "Event fired
  after a [PokemonEntity] is spawned from a player's party and after its animations are finished."
  (`common/api/events/pokemon/PokemonSentEvent.kt:62-79`).
- **Battles.** Battles send Pokémon out through the same `sendOutWithAnimation`, with a battle id
  (`common/battles/interpreter/instructions/SwitchInstruction.kt:80`, line 192, `Pokemon.kt:888`). `PokemonEntity.isBattling`
  reads that id (`common/entity/pokemon/PokemonEntity.kt:283-289`).
- **Recall.** `recallWithAnimation` sets the entity's `BEAM_MODE` to 3 at once and recalls the Pokémon 1.5 s later
  (`PokemonEntity.kt:863-905`, lines 876 and 885-889). `Pokemon.entity` is null once the Pokémon is not out
  (`Pokemon.kt:444-445`).
- **Owner lookup.** `Pokemon.getOwnerEntity` finds a player through `PlayerList.getPlayer` (`Pokemon.kt:1537-1549`). A
  NeoForge `FakePlayer` is not in that list, which matters for the GameTests.

### Cobblemon 1.8.4: following the owner

- The behaviour `cobblemon:pokemon_owned` applies `cobblemon:pokemon_follows_owner` to every Pokémon that is not wild
  (`data/cobblemon/behaviours/pokemon/auto/pokemon_owned.json:4-9` in the jar).
- `pokemon_follows_owner.json` (lines 7-17) adds the task `move_to_owner` to the `idle` activity at priority 4. For a party
  Pokémon its `teleportDistance` is 32 and its `maxDistance` is 5, for a species that can move.
- `MoveToOwnerTask` (`common/entity/pokemon/ai/tasks/MoveToOwnerTask.kt:57-67`) teleports the Pokémon next to its owner when the
  distance between them, less the Pokémon's horizontal hitbox radius, is more than `teleportDistance`. Above `maxDistance` it
  sets a walk target on the owner.
- The work activity runs in place of Cobblemon's idle activity (CD-AI-02, `ai/BrainBridge.kt:60-68`). While a Pokémon holds a
  work order, `move_to_owner` therefore never runs: the follow distance of 5 blocks and the teleport distance of 32 blocks do not
  apply. When the work order clears, the idle activity and the follow come back (CD-AI-03).
- Our work behaviour has no condition (`src/main/resources/data/cobblemon_dynamics/behaviours/pokemon/auto/work.json`), so the
  Brain of a sent-out party Pokémon has the work activity and the work order memory too.

### The engine today

`WorkEngine.tickLevel` (`engine/WorkEngine.kt:98-119`) runs each loaded pasture in Work Mode through `PastureRuntime`
(`engine/PastureRuntime.kt:16-79`). These parts assume a pasture:

- `tickLevel` returns at once when the level has no indexed pasture and no runtime (line 101). It makes a runtime for each
  position in the level's `work_pastures` index, and removes every runtime whose position is not indexed (lines 103-107). A
  runtime with no pasture block would be removed on its first tick.
- The poll (lines 239-279) reads the pasture block entity, its Work Mode flag, its tethered list, the bindings that name the
  pasture, and the recovery rule. It drops each worker that left the pasture.
- The scan schedule, the scan passes and the assignment (`scan`, `assign` and `assignHeals`) work on the runtime's work area,
  which is centered on the pasture block and clamped to Cobblemon's `pastureMaxWanderDistance` (`area`, line 872).
- `Worker.pasture` (`engine/Worker.kt:12`) and `Claim.pasture` (`claim/Claim.kt:8`) hold a `PastureKey`. Work Mode off and a
  removed pasture release claims by that key (`WorkEngine.kt:129`, line 788). The flip permission reads the placer of the
  claim's pasture (`leash/LeashInteractions.kt:252-256`). `WorkContext.pasture` is the pasture block (`WorkEngine.kt:848-870`),
  and only the Magnet reads it (`handler/item/MagnetHandler.kt:75`).
- A worker that gets Tired claims a bed in its pasture's work area (`goToRest`, lines 612-619). A healer heals allies of its
  own runtime (`tickHeal`).
- `drop` removes the worker from its runtime's slots (lines 798-806).

The other per-worker steps need no pasture: the claim with its spot search and path check (`claim` and `spotFor`, lines
464-515), the worker states (`tickWorker`, lines 518-577, through `WorkerMachine`), Work Stamina (`tickStamina`, lines
585-606), the Brain bridge and the effects (`direct`), and the release path (`release` and `clear`). The claim registry, the
handlers and `WorkerMachine` know nothing of pastures, apart from the key in a claim.

### Protection without a click

- The Work Leash honors protection mods through the event order (CD-SEC-11). NeoForge posts
  `PlayerInteractEvent.RightClickBlock` and returns when a listener cancels it
  (`net/minecraft/server/level/ServerPlayerGameMode.java:346-347`), before `onItemUseFirst` runs (lines 358-361). `WorkLeashItem.onItemUseFirst` (`leash/WorkLeashItem.kt:24`) binds, so a
  cancelled click never binds (`leash/LeashInteractions.kt:93-96`).
- A ray-traced target makes no click and no event. Vanilla gates every right-click on a block with
  `ServerLevel.mayInteract(player, pos)` (`net/minecraft/server/network/ServerGamePacketListenerImpl.java:1141`), which checks
  spawn protection and the world border (`net/minecraft/server/level/ServerLevel.java:806-808`).
- `ServerPlayer.mayInteract(level, pos)` (`net/minecraft/server/level/ServerPlayer.java:2036-2038`) asks
  `Entity.mayInteract` and then `Level.mayInteract`. `Entity.mayInteract` returns true in vanilla
  (`net/minecraft/world/entity/Entity.java:3683-3685`), so it is a hook that a mod can change. Vanilla asks the same question
  of a player's projectile that acts on a block for that player (`net/minecraft/world/entity/projectile/Projectile.java:301-306`),
  for example a burning arrow that lights TNT (`net/minecraft/world/level/block/TntBlock.java:130`).

## Decision

1. **Detection at the key press, on the server.** Quick Work subscribes to `POKEMON_SENT_PRE` at priority `LOWEST`, which
   runs after every other priority (`common/api/PrioritizedList.kt:27-31`), so another subscriber that cancels the send-out
   comes first. For a
   Pokémon in a `PlayerPartyStore` whose owner is online, in the event's level and in no battle, it traces from the owner's eye
   position along `calculateViewVector(xRot, yRot)`, with `ClipContext.Block.OUTLINE` and `ClipContext.Fluid.NONE`, as the
   crosshair does. The trace reaches the smaller of `quickWork.targetRange` and `quickWork.leashRange`. The first block that it
   hits is the aimed block, which the engine keeps as an aim for that Pokémon. Each send-out replaces the Pokémon's old aim,
   and a send-out with no block in view leaves none. No client code changes.
2. **The order starts when Cobblemon ends the send-out.** On `POKEMON_SENT_POST` the engine takes the aim out. It drops an aim
   older than 100 ticks, an aim of another level, and a send-out whose entity battles. The entity then exists, and its send-out
   animation has ended, so a Brain order does not fight the animation.
3. **Eligible roles.** The order takes only a role that fits the Pokémon's types, whose handler matches the aimed block, whose
   `auto_assign` is true, whose handler a pasture scan visits (a scan kind other than `NONE`) and whose handler names one
   target (`BindingShape.SINGLE`). `RoleSelection.best` picks between them, as an auto claim does. A Pokémon with a Fairy type
   gets no order, because the engine treats it as a healer (CD-ASG-04). A block that only leash-only or two-target roles fit
   gets the message "{target} needs the Work Leash". Any other block gets no order and no message, so a normal send-out stays
   quiet.
4. **Permission.** The order needs an owner who is not a spectator and whom `ServerPlayer.mayInteract(level, canonical)`
   allows. That is the strongest check with no side effect: it covers spawn protection and the world border, and the
   `Entity.mayInteract` hook that vanilla also asks for a player's projectiles. The engine does not post a made-up
   `RightClickBlock` event. Its listeners act on clicks, so a made-up click could make a block pick-up or a right-click harvest
   happen, or advance a quest.
5. **Claims and refusals.** After the permission, a Tired Pokémon, a target that a claim or a binding holds, and a target with
   no demand each refuse the order with an action-bar message. The checks run in that order. Then the order takes a normal work
   claim through the engine's own `claim` step, with its spot search and path check. A claim with no reachable spot refuses
   with "{Pokémon} cannot reach {target}". A refused send-out stays a normal one.
6. **A solo runtime.** A sealed interface `WorkSite` covers a pasture runtime and a new `QuickRuntime`. The engine's
   per-worker steps take a `WorkSite` where they took a `PastureRuntime`: the claim, the worker tick, the stamina step and the
   drop. The pasture steps keep `PastureRuntime`: the poll, the scan, the assignment, the heals, the beds and the bindings. A
   `QuickRuntime` holds one worker slot, its owner's UUID, the aimed block, the target's name and a work area around the
   target. That area holds every cell within the role's range of the target center, which are the cells that the spot search
   reads. `Worker.pasture` and `Claim.pasture` become nullable, and a Quick Work claim has no pasture. The engine keeps the
   orders in a map by Pokémon UUID and ticks them in `tickLevel`, after the pasture workers and in the same level loop. A
   `QuickRuntime` has no scan schedule and no poll.
7. **End.** Before its worker's tick, each order checks, in this order: the entity is gone (removed, dead, in the recall beam or
   no longer the Pokémon's entity), the entity battles, the owner is offline or in another level, the owner's eye position is
   farther than `quickWork.leashRange` from the box of the aimed block, the work claim is gone, and the worker has been in
   Standby for `engine.standbyMoveTicks`. The worker's own tick then ends the order on a travel timeout, and the stamina step
   ends it when the worker is Tired. The end is the engine's `drop`: the release path, with `onStop` for a Working worker, a
   flush of Work Stamina into the party Pokémon's data, and the removal of the stamina view and the slot. The map then forgets
   the order, and the owner gets the reason on the action bar. The Brain bridge has cleared the work order, so Cobblemon's
   follow-owner task takes over. Nothing searches for new work.
8. **Cost.** With no order, Quick Work adds one empty-map check to each level tick. A running order costs one pasture worker's
   steps, one player lookup by UUID and one distance check per tick. It never starts a scan pass.
9. **Multiplayer.** Orders and aims are keyed by the Pokémon's UUID and hold the owner's UUID, never a player object. The order
   looks the owner up by UUID on each tick. Messages go to the owner only. A logout drops the owner's aims, and the owner's
   orders end on the next tick. The engine instance holds every order and aim, so a server stop drops them. The server stop
   first ends the handlers' work and flushes Work Stamina for every slot, orders included.
10. **Config.** The server config gains `quickWork.enabled` (default `true`), `quickWork.targetRange` and
    `quickWork.leashRange` (2 to 24 blocks, default 16 each). A role's range is at most 6 blocks in the default role files, so a
    worker at a target 24 blocks away stays inside the teleport distance of 32 blocks that Cobblemon gives party Pokémon.

## Consequences

- A party Pokémon can work one block at a time, and only while its owner stays near. The output caps of the role files and
  Work Stamina still apply, so the balance rule ([ADR-0010](0010-balance-rule.md)) holds.
- The non-goal of work by party Pokémon now excepts Quick Work. CD-ASG-01 gives claims to Pokémon on a Quick Work order too.
- A protection mod that vetoes only `RightClickBlock` does not see Quick Work. A Quick Worker only adds work to a block (fuel,
  heat, rotation, FE, brewing speed or crystal growth), but a Wheel Driver that turns a stranger's water wheel can still change
  that player's Create network. A server owner turns Quick Work off with `quickWork.enabled`.
- A Quick Work claim has no pasture, so no binder and no placer stands behind it. While a Quick Worker drives a wheel or a crank,
  only an operator can flip it (CD-SEC-10).
- A Quick Worker keeps the parts of the engine that need no pasture: head bars while a player holds the Work Leash, the
  `/cobblemondynamics claims` list, the effects, the work focus entity and the push exemption between directed workers.
- The engine refactor changes the types of `Worker.pasture`, `Claim.pasture` and the per-worker steps. Every pasture GameTest
  runs again to show that pastures behave as before.
- The GameTests need fake owners that `PlayerList.getPlayer` finds. A test adds its fake player to the player list's UUID map
  for its own run and removes it at the end.

## Alternatives considered

- **Trace at `POKEMON_SENT_POST`.** One event and no aim to keep. But that event comes 30 ticks after the key press, and a player
  who turns to a second machine in that time would send the first Pokémon to it.
- **A mixin on `SendOutPokemonHandler.handle`.** It gives the key press and the player directly. `POKEMON_SENT_PRE` gives the
  same moment through Cobblemon's API, and a mixin on a packet handler breaks when Cobblemon changes it.
- **A target sent by the client.** It needs a client change and a payload to check on the server, and the server has the
  player's rotation already (ruling 1).
- **A temporary pasture or a tether.** Cobblemon recalls or teleports a tethered Pokémon outside its pasture's roam box, and a
  tether needs the Pokémon in the PC.
- **A pasture runtime with scans for the order.** It would keep searching for work, which the approver ruled out.
- **A made-up `RightClickBlock` event as the permission check.** It honors more protection mods, but every listener acts as if
  the player clicked the block.
- **The claim APIs of Open Parties and Claims or FTB Chunks.** They honor those mods exactly, but each needs its own optional
  compat code. They can follow if the approver wants them.
