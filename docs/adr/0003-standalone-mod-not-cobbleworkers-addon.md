# ADR-0003: Standalone mod, not a Cobbleworkers addon

Status: Accepted (2026-11-09)

## Context

Two mods already turn Cobblemon pastures into job sites:

- **Cobbleworkers 2.0.7** (689,000 downloads, Fabric and NeoForge). It has 32 data-pack jobs that it
  assigns by type, species, move or ability. Since 2.x the source is closed. The GitHub README says
  that the repository holds "the last open-source release" and that pull requests are not accepted.
  The job component set is sealed at the JVM level, so no other mod can add a component type. Jobs
  added through code are wiped on every reload. The mod is server-side only and has no per-pasture
  toggle "by design". The 2.0.7 jar declares MPL-2.0 but ships no license file.
- **Cobblebase 2.0.1** (97,000 downloads, MPL-2.0, Fabric and NeoForge). It assigns 45 skills per
  species from about 1,367 hand-made files. It adds a button to the pasture panel. Its idle behaviour
  wipes the Brain and pins the position of Pokémon it has not assigned.

Neither mod drives Create kinetics, generates FE or touches AE2. Cobblebase's changelog lists
Create's Blaze Burner as "not yet supported". Our design also needs a global claim, the Work Leash,
a per-pasture toggle and Work Stamina, and neither mod offers a hook for those.

## Decision

Build Cobblemon Dynamics as a standalone NeoForge mod with its own scan, claim, navigation and
effect engine. Coexist with both mods through small compatibility shims
([ADR-0009](0009-coexistence-with-pasture-job-mods.md)).

## Consequences

- We rebuild scanning, claiming and navigation. That is a moderate amount of work.
- We control every rule the operator set: one worker per target, the Work Leash, the toggle, and Work
  Stamina with beds and healers.
- We carry compat code for both mods and check it when they update.
- Useful patterns from the prior art carry over:
  - Scan the work area a little each tick.
  - Key claims by dimension as well as position.
  - Play move poses with `PlayPosableAnimationPacket` name lists.
  - Apply movement before Cobblemon's Brain ticks.

## Alternatives considered

- **Addon that depends on Cobbleworkers.** Cheap for vanilla jobs. A data pack cannot express Create
  kinetics, FE or block capabilities, and the sealed component set blocks new types. CobbleMiners, an
  addon that patched Cobbleworkers 1.x internals, most likely broke at 2.x (inferred). The 2.x license
  is unclear.
- **Contribute upstream.** Cobbleworkers refuses pull requests. Cobblebase works by species and has a
  different assignment model, and its activity comes in bursts (77 of its 80 recent commits landed in
  one month).
