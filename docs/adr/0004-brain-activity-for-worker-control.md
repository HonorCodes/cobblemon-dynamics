# ADR-0004: Brain activity for worker control

Status: Accepted (2026-11-09)

## Context

A worker must walk to a work spot, stay there, face its target, walk to a bed, lie on it, and walk
to an ally to heal it. Cobblemon 1.8 Pokémon use Minecraft's Brain AI, not goals. The goal code in
`PokemonEntity` is commented out. Cobblemon builds each Brain from behaviour JSON. Any namespace can
add files to a `behaviours/pokemon/auto/` folder, and Cobblemon applies them to every Pokémon. Its
source calls this folder "addon-friendly". Task types live in the public map `TaskConfig.types`.

The prior art took two other paths:

- **Cobbleworkers** sends a new path at most every 20 ticks from a server tick. Cobblemon's wander
  task keeps pulling the Pokémon away between calls.
- **Cobblebase** mixes into `PokemonEntity.tick` at HEAD. It forgets `WALK_TARGET`, `LOOK_TARGET` and
  `PATH`, and pins the X and Z position. Its source notes that clearing these from the pasture tick
  lost the race with the Brain.

Two Cobblemon facts shape the design:

- Cobblemon rebuilds a Pokémon's Brain when it replaces `entity.pokemon`. With the owner online, the
  entity swaps to the PC's Pokémon instance every 20 ticks.
- Cobblemon builds the Brain in the entity constructor, before it sets `tethering`. So a behaviour
  condition such as `is_pastured` is false for a Pokémon that was just pastured.

## Decision

The operator chose the Brain approach.

- Register an activity `cobblemon_dynamics:work`, a memory `cobblemon_dynamics:work_order` and our
  task types in `TaskConfig.types`.
- Ship an auto behaviour JSON that adds a task at idle priority 0. When the work order memory exists,
  the task switches to the work activity. A switch-back task restores the normal schedule when the
  memory clears, as `SwitchFromActionEffectTaskConfig` does.
- In the work activity, tasks set `WALK_TARGET` to the work spot, keep `PATH_COOLDOWN` set at the
  spot, and run the bed and heal steps. The work activity has its own look task that turns the
  Pokémon toward the target's center. Cobblemon's `look_at_target` runs only in the idle activity,
  which the work activity replaces.
- The behaviour has no MoLang condition on pasture state. Every Pokémon gets the task, and only a
  Pokémon with a work order acts on it.
- The work engine owns all worker state. It rewrites the memory every tick, so a Brain rebuild loses
  nothing.
- For sleep on a bed, a task sets `enablePoseTypeRecalculation=false` and `POSE_TYPE=SLEEP`. It never
  sets the battle SLEEP status, which persists. It restores the flag on wake, because Cobblemon saves
  it to NBT.

## Consequences

- Wandering pauses on its own while the work activity runs, and normal behaviour returns after it.
- The mod needs no mixin into `PokemonEntity`.
- The mod depends on a part of Cobblemon that still changes: `TaskConfig.types`, the auto folder
  rules, and when Brains rebuild. A future species `baseAI` would skip the auto behaviour. The `ai`
  package isolates the bridge. If task registration fails, workers stay Idle and the mod logs one error.
- Each custom task must declare its memories with `addMemories(...)` (inferred from vanilla Brain rules).

## Amendment (2026-11-09, M1 Task 9)

The build changed three details of the decision. The Brain approach stays.

- The switch task runs at core priority (`minecraft:core`), not idle priority 0. An idle task does not
  run after a Brain rebuild until the idle activity runs again, which breaks CD-AI-04.
- `BrainBridge.write` also switches the Brain to the work activity itself. The Brain ticks before
  `LevelTickEvent.Post`, so the task alone would restore a rebuilt Brain one tick late.
- Entering the work activity calls `brain.stopAll`. A Brain keeps running the old activity's tasks after
  a switch, so Cobblemon's idle look task would go on for 80 to 160 ticks.

## Alternatives considered

- **Steer from a server tick** (Cobbleworkers). Simple, but the Pokémon jitters against the wander AI.
- **Pin the entity** (Cobblebase). Positioning is reliable, but it blocks all Cobblemon behaviour, and
  a mixin into `PokemonEntity.tick` breaks easily on updates.
