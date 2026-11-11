# ADR-0005: Global claim registry

Status: Accepted (2026-11-09)

## Context

The operator's rule: only one Pokémon may perform an action on any individual block or entity,
across all pastures. For example, two Pokémon cannot power the same water wheel, even from two
different pastures. Wild Pokémon never act.

The prior art handles this in a weaker way:

- **Cobbleworkers** keeps a global map from block position to Pokémon UUID, with a 30 s expiry. The
  key has no dimension, so the same coordinates in two dimensions collide (inferred). It had a claim
  leak with no expiry (#73), fixed on 2026-07-01.
- **Cobblebase** keeps one soft reservation per executor, with no shared ledger.

Our mod also needs claims for beds (one sleeper per bed) and for healed allies (one healer per ally).
Leash bindings must survive restarts. A leashed worker that gets Tired must claim a bed while its
machine stays reserved for it. Some targets span several blocks. For example, the large water wheel
has eight structural blocks around its controller.

## Decision

- One server-wide `ClaimRegistry` in memory, used on the server thread only.
- A claim key is a dimension plus a canonical block position, or an entity UUID. Each handler maps a
  clicked or scanned block to its canonical position. For example, a click on any block of a large
  wheel's ring maps to the controller.
- One claim per key.
- Claim kinds:
  - **Work claim.** A worker holds it on the target that it works. The engine makes it for an
    auto-assigned worker, and for a leashed worker while it works its bound target.
  - **Bed claim.** A Tired worker holds it on its bed.
  - **Heal claim.** A healer holds it on the UUID of the ally that it heals.
- A worker holds at most one active claim: a work claim, a bed claim or a heal claim.
- A **leash binding** is a reservation, not a claim. It does not count against the one-claim limit. A
  worker holds at most one binding.
  - A binding reserves its target. No other worker can take a work claim on that target, and a second
    binding on it is refused.
  - A binding takes its target from an auto-assigned worker. That worker returns to Idle.
  - A Tired leashed worker keeps its binding and takes a bed claim. Its machine stays reserved while it
    sleeps.
  - A Telekinesis binding reserves its source only. Any number of Telekinesis workers can use one
    destination ([ADR-0011](0011-v2-roles-in-first-release.md)).
- A leash binding persists in two places. The Pokémon's `persistentData` holds the binding, and a
  per-level index maps the target to the Pokémon UUID.
- Work, bed and heal claims are never saved. After a restart, scans rebuild them.
- The registry releases a work, bed or heal claim when:
  - its target is broken, replaced or unloaded
  - Work Mode turns off
  - the worker is unpastured, unloaded or fainted
  - the role no longer fits the worker
  - the claim is an auto work claim and the worker has not arrived 600 ticks after the claim. That
    worker then skips the target for 6,000 ticks.
- An auto work claim moves when its worker has been in Standby for 200 ticks or more and a scan finds
  an unclaimed target with demand for one of its roles in its work area. A leashed worker never moves
  off its bound target.
- A binding clears when:
  - its target is broken, replaced, picked up with a Create wrench, or moved into a Create contraption
  - its Pokémon is recalled to the PC or the party, moves to another pasture, is released or traded,
    or loses its pasture block
  - an evolution leaves no role that fits the Pokémon
  - the owner clears it with the Work Leash
- A Pokémon that reaches a new owner carries no binding data.
- A leashed worker that has not arrived 600 ticks after its work claim keeps the binding. It waits in
  Standby for 6,000 ticks, the owner gets a message, and then it tries again.

## Consequences

- No claim goes stale after a crash, because work, bed and heal claims do not persist.
- One worker per block also means one role per block. A brewing stand takes a Brew Stoker or a
  Brewer. When one worker fits two roles on the same target, the role with the higher `priority` wins
  ([ADR-0006](0006-data-driven-roles.md)).
- A leashed machine stays reserved while its worker sleeps, so no other worker covers it.
- An auto-assigned worker that gets Tired releases its target, so another worker can take over.

## Alternatives considered

- **Persistent auto claims.** They survive restarts, but a crash leaves stale claims that need expiry
  rules.
- **Bindings as claims.** One model for everything, but a Tired leashed worker could not claim a bed
  without breaking the one-claim limit.
- **Claims per pasture.** Simpler, but it breaks the operator's rule across pastures.
- **One ledger per handler** (Cobblebase). It allows two different roles on one block, and nothing
  spans beds and heals.
