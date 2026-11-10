# ADR-0007: Effects through Cobblemon packets

Status: Accepted (2026-11-09)

## Context

The operator wants each worker to loop a move toward its target. For example, a Water-type casts
Water Gun at the center of a water wheel. Sleep must show Cobblemon's sleep visual silently. Work
sounds are off unless the client turns them on. The healer shows heal sparkles on the ally. If
Cobblemon later adds a Floral Healing effect, the healer must use it.

Cobblemon 1.8.4 facts:

- It has 138 move timelines. Each role move has a timeline with particle ids: Water Gun, Thunder
  Shock, Flamethrower, Ember, Dragon Breath, Pound, Rock Throw, Poison Gas and Fairy Wind for Phase 1,
  and Kinesis, Gust, Fire Spin, Bubble and Will-O-Wisp for Phase 2. One gap: `bubble_target` has no
  particle file, so that step of Bubble shows nothing. The integration-points note lists every id.
- No Steel move other than Bullet Punch has a timeline. The Magnet role uses the generic
  `cobblemon:metal_sparks` particle.
- No heal move has a timeline. That includes Floral Healing, Heal Pulse, Life Dew and Wish. The
  generic particles `cobblemon:heal_circles` and `cobblemon:heal_sparkles` exist, but Cobblemon does not
  use them.
- The sleep particles are `cobblemon:sleep_actor` and `cobblemon:sleep_actorresidual`.
- `PlayPosableAnimationPacket` plays the first animation name that the model has. Few models have
  attack poses. Of 759 pose files, 105 define `special`, 116 define `physical`, 616 define `cry` and
  406 define `sleep`.
- An action-effect timeline can run outside battle with a custom context. Its animation and particle
  steps need the Pokémon as the user, and some steps read battle-only queries such as `missed` and
  `target_fainted`. Timelines also carry their own sounds.
- Cobblemon particles can aim only at entities. The client skips an unknown particle id with no error.
  Vanilla Marker entities are never sent to clients (inferred).

## Decision

- Do not run action-effect timelines, with one exception. When Cobblemon's server data holds the
  action effect `cobblemon:floralhealing`, the healer runs that timeline with a custom context: the
  healer is the user and the ally entity is the target. Otherwise the healer plays its `special` pose,
  with `cry` as the fallback, and `heal_circles` and `heal_sparkles` play on the ally.
- On each effect loop (every 40 ticks by default), send `PlayPosableAnimationPacket` with the role's
  ordered animation names, from most specific to `cry`. Then send `SpawnSnowstormEntityParticlePacket`
  with the role's actor particles and target particles.
- Each Working claim on a block target gets a work focus entity: an invisible, client-visible entity
  with no collision at the target's center. Target particles aim at it. It is never saved to disk.
- A sleeping worker gets the sleep pose and the two sleep particles, with no sound.
- Sound plays only for clients with `effects.workSounds` on.
- Effect packets go only to players within 64 blocks.
- The role JSON holds all effect data ([ADR-0006](0006-data-driven-roles.md)).

## Consequences

- Only the Floral Healing exception touches battle-only queries. Its target is an entity, so it needs
  no work focus entity.
- One extra entity per Working claim on a block target.
- A renamed particle id or a missing pose shows a weaker effect, never a crash. The server cannot see
  client particle assets, so the effects checklist catches a renamed id.
- Most Pokémon will play `cry` as their work pose, because few models have attack poses.

## Alternatives considered

- **Run timelines with a custom context for every role.** Most faithful, but it depends on battle-only
  queries and `is_user` rules, and it bakes in sounds.
- **A client packet that builds `ParticleStorm` with a block target.** No extra entity, but
  `ParticleStorm` is Cobblemon client code marked `internal`.
- **Position-only particles.** Simple, but the stream would not aim from the Pokémon to the target.

## Amendment (2026-11-09, M1 Task 10 follow-up)

The build changed one detail of the decision. Actor particles still go out as
`SpawnSnowstormEntityParticlePacket`, aimed at the work focus entity. Target particles go out as
`SpawnSnowstormParticlePacket` at the work focus entity's position. Cobblemon's client spawns an
entity-sourced particle at a locator of its source entity and uses the targeted entity only for the aim,
so a target particle sent that way burst on the worker instead of on the target.

## Amendment (2026-11-10, play-test): no heal sparkles

The healer no longer shows `cobblemon:heal_sparkles` on the ally. Its particle file calls `math.random()`
with no arguments, which Cobblemon 1.8.4's MoLang rejects, and the client crashed with "Ticking Particle"
on the first heal. Only `cobblemon:heal_circles` plays on the ally.

## Amendment (2026-11-10, play-test): each animation plays to its end

A loop every 40 ticks started Cobblemon's attack animations again before they ended, and the approver found
it glitchy. Most `special` and `physical` animations last 3 s, and the longest last 4 s. The server now reads
the length of each animation from Cobblemon's client assets in its jar. The species' resolver picks the
poser, the poser maps an animation name to a bedrock animation, and the animation file gives its
`animation_length`. The effect player sends only the first name of the role's list that the worker's model
has, so the client plays the animation whose length the server knows. The next effect starts when that
animation has ended and the role's `idle_ticks` (default 40) have passed, so the model returns to its idle
pose. The particles go out with each animation. A worker whose model has none of the names gets no
animation, and its particles play every `loop_ticks` (default 40).
