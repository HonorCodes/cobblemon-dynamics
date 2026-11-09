# ADR-0002: NeoForge 1.21.1 only

Status: Accepted (2026-11-09)

## Context

On 2026-11-09 the three main dependencies had these releases:

| Mod | Latest release | Minecraft | Loaders |
|---|---|---|---|
| Cobblemon | 1.8.4 (2026-10-27) | 1.21.1 | Fabric, NeoForge |
| Create | 6.0.10 (published on Modrinth 2026-04-21, tag commit dated 2026-04-20) | 1.21.1 | NeoForge |
| Applied Energistics 2 | 19.2.19 (2026-11-09) | 1.21.1 | NeoForge |

Create has no Fabric build for 1.21.1. The Modrinth project `create-fabric` lists only 1.18.2,
1.19.2 and 1.20.1. The Fabric port's `mc1.21.1/fabric/dev` branch had its last commit on
2025-03-11. AE2 publishes alpha and beta builds for Minecraft 26.x, but Cobblemon and Create stay on
1.21.1.

## Decision

- Target Minecraft 1.21.1 and NeoForge only.
- Write the mod in Kotlin on KotlinForForge `[5.10,)`, the same floor as Cobblemon 1.8.4. Write the
  mixins in Java.
- Require Cobblemon `[1.8.4,1.9)` and NeoForge `[21.1.219,)`.
- Make Create `[6.0.10,6.1)` and AE2 `[19.2,20)` optional. Their roles load only when they are present.

## Consequences

- One code path and one test matrix.
- Fabric players cannot use the mod.
- A server with Cobblemon only still gets the vanilla and FE roles.
- A later port past 1.21.1 meets NeoForge's transfer API, which replaces `IEnergyStorage` and the
  fluid and item handlers from NeoForge 21.9. The FE and fluid code sits behind internal interfaces
  to keep that port small.

## Alternatives considered

- **NeoForge and Fabric through Architectury, as Cobblemon does.** It reaches more Cobblemon players.
  The Fabric build would have no Create roles and no AE2 roles on 1.21.1, and the platform work and
  test matrix grow by about 1.5 to 2 times.
- **Fabric only.** It would lose Create and AE2, which are the main reason for the mod.
