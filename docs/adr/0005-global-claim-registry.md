# ADR-0005: Global claim registry

Status: Accepted (2026-11-09). [Amendment 1](#amendment-1-shared-logistics-claims-2026-11-11) lets the logistics roles share
their containers. [Amendment 2](#amendment-2-farming-claims-2026-11-12) gives each farming role its own claim on a crop.

## Context

The operator's rule: only one Cobblemon may perform an action on any individual block or entity,
across all pastures. For example, two Cobblemon cannot power the same water wheel, even from two
different pastures. Wild Cobblemon never act.

The prior art handles this in a weaker way:

- **Cobbleworkers** keeps a global map from block position to Cobblemon UUID, with a 30 s expiry. The
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
- A leash binding persists in two places. The Cobblemon's `persistentData` holds the binding, and a
  per-level index maps the target to the Cobblemon UUID.
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
  - its Cobblemon is recalled to the PC or the party, moves to another pasture, is released or traded,
    or loses its pasture block
  - an evolution leaves no role that fits the Cobblemon
  - the owner clears it with the Work Leash
- A Cobblemon that reaches a new owner carries no binding data.
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

## Amendment 1: shared logistics claims (2026-11-11)

Status: Accepted (2026-11-11)

### Context of the amendment

The approver's ruling of 2026-11-11 found a bug in the one-claim rule. The ideal flow for item transfer needs at least
two claims on one chest: a Steel-type Magnet collects ground loot into the chest, and a Psychic-type Telekinesis worker
moves it from the same chest to another container. The approver added that a logistics container needs no claim cap, or
a cap of 3 to 5 that a container does not reach in practice. The coordinator ruled the details under the approver's
delegation. Input R66 of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md) holds the ruling and the rulings on it.

### Decision of the amendment

- A handler marks its targets as shared. The Magnet and Telekinesis handlers do, so every role file that names one of
  them is a logistics role. Every other role is an exclusive role, as before.
- The work claim of a logistics role is a shared claim. One canonical target holds up to
  `logistics.maxWorkersPerContainer` shared claims (default 5, 1 to 16), counted across the logistics roles together.
  The next worker gets no claim.
- Exclusive claims block only exclusive claims. A shared claim never blocks an exclusive claim, and an exclusive claim
  never blocks a shared claim. A binding of an exclusive role reserves its target only against exclusive claims.
- A binding of a logistics role reserves nothing. It takes one place at each container that it names: the Magnet's
  container, or the Telekinesis source and destination. The Work Leash refuses a bind when another binding already
  takes every place of a container, with "{target} already has {N} workers". A destination holds no claim, so it counts
  only at bind time.
- A worker still holds at most one claim, and each worker still needs its own free work spot.

### Consequences of the amendment

- One worker per block still holds for every machine. A logistics container takes up to the cap of logistics workers,
  and the one exclusive worker of the same block works beside them. For example, a Stoker heats a furnace while a
  Telekinesis worker empties it, and a Pump fills a Create basin while a Telekinesis worker moves its items.
- Quick Work starts on a block that only logistics workers hold, because their shared claims never block its claim.
- Many Telekinesis workers can still feed one destination, such as an ME Interface, up to the cap.
- A lower `logistics.maxWorkersPerContainer` clears no binding. A shared claim that a worker already holds stays, and a
  worker above the new cap waits for a free place before it claims again. Bindings at a destination above the new cap
  keep working, because a destination counts only at bind time.

## Amendment 2: farming claims (2026-11-12)

Status: Accepted (2026-11-12), with [ADR-0015](0015-farming-roles-and-crop-adapters.md).

The farming roles of 1.1 add a third kind of sharing beside the exclusive claims and the shared logistics claims. The
claim registry keys a work claim of a farming role by the crop and the role's claim lane. So one crop holds at most one
Cultivator claim and one Harvester claim at once. A binding of a farming role reserves the crop only against claims of its
own role. A Cultivator and a Harvester, auto-assigned or bound, share a crop, and two workers of one role do not.
Every other role claims in the null lane, which is the key of 1.0, so nothing else changes. ADR-0015, decision 9, holds the
rule, and the rows CD-CRP-06 to CD-CRP-08 and CD-CRP-13 of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md) test it.
