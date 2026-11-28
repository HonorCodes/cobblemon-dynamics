package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.IdleReason
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.net.AreaBoundsPayload
import codes.honor.cobblemondynamics.net.AreaWatchPayload
import codes.honor.cobblemondynamics.net.DynamicsPayloads
import codes.honor.cobblemondynamics.net.PanelViewers
import codes.honor.cobblemondynamics.net.PastureSettingsPayload
import codes.honor.cobblemondynamics.net.WorkModeTogglePayload
import codes.honor.cobblemondynamics.pasture.AreaWatchers
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.WorkModePolicy.Decision
import codes.honor.cobblemondynamics.pasture.WorkModeService
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.stamina.TraitFlags
import com.cobblemon.mod.common.api.pasture.PastureLink
import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class PastureShelfServerTests {
    private fun openPanel(helper: GameTestHelper, player: ServerPlayer, pastureRel: BlockPos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        TestKit.clickBlock(helper, player, pastureRel)
        val linked = PastureAdapter.linkedPasture(player)
        helper.assertTrue(linked == helper.absolutePos(pastureRel), "Cobblemon linked ${player.gameProfile.name} to $linked")
        DynamicsPayloads.onPanelOpen(player)
        TestKit.afterTest(helper) {
            PastureLinkManager.links.remove(player.uuid)
            PanelViewers.forget(player)
            AreaWatchers.forget(player.uuid)
            WorkModeService.forget(player.uuid)
            DynamicsPayloads.forget(player.uuid)
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 400)
    fun cdShf02Shf03PastureRadiusFiltersTargetsAndRejectsInvalidRequests(helper: GameTestHelper) {
        val placer = TestKit.owner(helper, "placer")
        val contributor = TestKit.owner(helper, "contrib")
        val stranger = TestKit.owner(helper, "stranger")
        val pastureRel = BlockPos(11, 1, 11)
        val pasture = TestKit.placePasture(helper, pastureRel, placer)
        TestKit.standBeside(placer, pasture)
        TestKit.standBeside(contributor, pasture)
        TestKit.standBeside(stranger, pasture)

        val charmander = TestKit.tether(helper, pasture, placer, "charmander level=20")
        TestKit.tether(helper, pasture, contributor, "rattata level=5")
        val furnaceRel = BlockPos(21, 1, 11)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 32)).blockPos

        openPanel(helper, placer, pastureRel)
        openPanel(helper, contributor, pastureRel)
        openPanel(helper, stranger, pastureRel)

        helper.assertTrue(
            WorkModeService.requestSettings(stranger, pasture.blockPos, radius = 8, locked = null) is Decision.Reject,
            "stranger changed pasture radius",
        )
        helper.assertTrue(
            WorkModeService.requestSettings(contributor, pasture.blockPos, radius = 8, locked = true) is Decision.Reject,
            "contributor locked pasture via combined settings request",
        )
        helper.assertFalse(PastureAdapter.isLocked(pasture), "pasture became locked after rejected contributor request")
        helper.assertTrue(
            WorkModeService.requestSettings(placer, pasture.blockPos, radius = 7, locked = null) is Decision.Reject,
            "unlisted radius step 7 was accepted",
        )
        helper.assertTrue(
            WorkModeService.requestSettings(placer, pasture.blockPos, radius = 20, locked = null) is Decision.Reject,
            "over-cap radius 20 was accepted",
        )

        val farCaller = TestKit.owner(helper, "far_placer")
        TestKit.place(farCaller, pasture.blockPos.x + 11.5, pasture.blockPos.y.toDouble(), pasture.blockPos.z + 0.5)
        helper.assertTrue(
            WorkModeService.requestSettings(farCaller, pasture.blockPos, radius = 12, locked = null) is Decision.Reject,
            "distant caller (>10 blocks) changed radius",
        )

        DynamicsPayloads.onSettings(placer, PastureSettingsPayload(radius = 4))
        helper.assertTrue(PastureAdapter.radius(pasture) == 4, "placer radius 4 did not apply")
        TestKit.workMode(pasture, true)

        var raisedToTwelve = false
        helper.onEachTick {
            if (!raisedToTwelve && helper.tick >= 60) {
                helper.assertTrue(
                    TestKit.claimOf(helper, charmander) == null,
                    "Charmander claimed furnace at offset 10 while pasture radius was 4",
                )
                DynamicsPayloads.onSettings(placer, PastureSettingsPayload(radius = 12))
                helper.assertTrue(PastureAdapter.radius(pasture) == 12, "placer radius 12 did not apply")
                raisedToTwelve = true
                return@onEachTick
            }
            if (raisedToTwelve) {
                val claim = TestKit.claimOf(helper, charmander)
                if (claim?.kind == ClaimKind.WORK &&
                    claim.key == TestKit.keyOf(helper, furnace) &&
                    TestKit.workerOf(helper, charmander)?.status == WorkerStatus.WORKING
                ) {
                    helper.succeed()
                }
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 120)
    fun cdShf05AreaWatchPersistsAfterPanelCloseAndClearsOnDisable(helper: GameTestHelper) {
        val placer = TestKit.owner(helper, "placer")
        val viewer = TestKit.listedOwner(helper, BlockPos(9, 1, 7), "viewer")
        val pastureRel = BlockPos(7, 1, 7)
        val pasture = TestKit.placePasture(helper, pastureRel, placer)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(placer, pasture)

        openPanel(helper, viewer, pastureRel)
        DynamicsPayloads.onAreaWatch(viewer, AreaWatchPayload(pasture.blockPos, watching = true))

        val initialBounds = AreaWatchers.lastSent(viewer.uuid)
        helper.assertTrue(
            initialBounds != null &&
                initialBounds.active &&
                initialBounds.minX == pasture.blockPos.x - 8 &&
                initialBounds.maxX == pasture.blockPos.x + 8,
            "initial AreaBoundsPayload mismatch: $initialBounds",
        )

        PanelViewers.close(viewer)
        PastureLinkManager.links.remove(viewer.uuid)

        WorkModeService.applyRadius(helper.level, pasture.blockPos, 4)
        val shrunkBounds = AreaWatchers.lastSent(viewer.uuid)
        helper.assertTrue(
            shrunkBounds != null &&
                shrunkBounds.active &&
                shrunkBounds.minX == pasture.blockPos.x - 4 &&
                shrunkBounds.maxX == pasture.blockPos.x + 4,
            "post-close live radius update mismatch: $shrunkBounds",
        )

        TestKit.workMode(pasture, false)
        val deactivated = AreaWatchers.lastSent(viewer.uuid)
        helper.assertTrue(
            deactivated != null && !deactivated.active && deactivated.pos == pasture.blockPos,
            "Work Mode off did not push inactive AreaBoundsPayload: $deactivated",
        )
        helper.succeed()
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdShf06Shf07Shf10SyncedIdleReasonsAndTraitFlags(helper: GameTestHelper) {
        val placer = TestKit.owner(helper, "placer")
        val pastureRel = BlockPos(7, 1, 7)
        val pasture = TestKit.placePasture(helper, pastureRel, placer)
        TestKit.standBeside(placer, pasture)
        helper.setBlock(BlockPos(9, 1, 7), Blocks.CHEST)

        val magnemite = TestKit.tether(helper, pasture, placer, "magnemite level=20")
        val squirtle = TestKit.tether(helper, pasture, placer, "squirtle level=20")
        val pikachu = TestKit.tether(helper, pasture, placer, "pikachu level=20 shiny")
        pikachu.pokemon.shiny = true
        pikachu.pokemon.isAlpha = true

        TestKit.workMode(pasture, true)
        openPanel(helper, placer, pastureRel)

        var markedTired = false
        helper.onEachTick {
            val squirtleWorker = TestKit.workerOf(helper, squirtle)
            if (!markedTired && squirtleWorker != null) {
                squirtleWorker.stamina.fraction = 0.0
                squirtleWorker.tired = true
                markedTired = true
                return@onEachTick
            }
            if (!markedTired || helper.tick < 50) return@onEachTick

            PanelViewers.send(placer, helper.level, pasture.blockPos)
            val panel = PanelViewers.lastSent(placer.uuid) ?: return@onEachTick
            val byId = panel.entries.associateBy { it.pokemonId }

            val magView = magnemite.getExistingDataOrNull(DynamicsAttachments.STAMINA_VIEW)
            val sqView = squirtle.getExistingDataOrNull(DynamicsAttachments.STAMINA_VIEW)
            val pikaView = pikachu.getExistingDataOrNull(DynamicsAttachments.STAMINA_VIEW)

            val magEntry = byId[magnemite.pokemon.uuid]
            val sqEntry = byId[squirtle.pokemon.uuid]
            val pikaEntry = byId[pikachu.pokemon.uuid]
            val expectedTraits = TraitFlags.SHINY or TraitFlags.ALPHA

            if (magView?.idleReason == IdleReason.NEEDS_LEASH &&
                magEntry?.idleReason == IdleReason.NEEDS_LEASH &&
                sqView?.idleReason == IdleReason.RESTING &&
                sqEntry?.idleReason == IdleReason.RESTING &&
                pikaView != null &&
                (pikaView.traitFlags and expectedTraits) == expectedTraits &&
                pikaEntry != null &&
                (pikaEntry.traitFlags and expectedTraits) == expectedTraits
            ) {
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 100)
    fun cdSec04Shf03PacketBoundaryAcceptsExactTenBlocksRejectsTenPointOneAndCrossDimensionLink(helper: GameTestHelper) {
        val placer = TestKit.owner(helper, "placer")
        val pastureRel = BlockPos(7, 1, 7)
        val pasture = TestKit.placePasture(helper, pastureRel, placer)
        TestKit.standBeside(placer, pasture)
        openPanel(helper, placer, pastureRel)
        val link = PastureLinkManager.getLinkByPlayerId(placer.uuid)!!

        TestKit.place(placer, pasture.blockPos.x + 10.5, pasture.blockPos.y + 0.5, pasture.blockPos.z + 0.5)
        WorkModeService.forget(placer.uuid)
        DynamicsPayloads.onSettings(placer, PastureSettingsPayload(radius = 8))
        helper.assertTrue(PastureAdapter.radius(pasture) == 8, "exact 10.0-block settings request was rejected")
        WorkModeService.forget(placer.uuid)
        DynamicsPayloads.onToggle(placer, WorkModeTogglePayload(true))
        helper.assertTrue(PastureAdapter.isWorkMode(pasture), "exact 10.0-block toggle request was rejected")

        TestKit.place(placer, pasture.blockPos.x + 10.6, pasture.blockPos.y + 0.5, pasture.blockPos.z + 0.5)
        WorkModeService.forget(placer.uuid)
        DynamicsPayloads.onSettings(placer, PastureSettingsPayload(radius = 12))
        DynamicsPayloads.onToggle(placer, WorkModeTogglePayload(false))
        helper.assertTrue(
            PastureAdapter.radius(pasture) == 8 && PastureAdapter.isWorkMode(pasture),
            "10.1-block request modified pasture",
        )

        TestKit.standBeside(placer, pasture)
        val crossDimensions = listOf(
            Level.NETHER.location(),
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "mirror_overworld"),
        )
        for (dim in crossDimensions) {
            WorkModeService.forget(placer.uuid)
            PastureLinkManager.createLink(
                placer.uuid,
                PastureLink(link.linkId, link.pcId, dim, pasture.blockPos, link.permissions),
            )
            DynamicsPayloads.onSettings(placer, PastureSettingsPayload(radius = 16, locked = true))
            DynamicsPayloads.onToggle(placer, WorkModeTogglePayload(false))
            helper.assertTrue(
                PastureAdapter.radius(pasture) == 8 &&
                    !PastureAdapter.isLocked(pasture) &&
                    PastureAdapter.isWorkMode(pasture),
                "cross-dimension PastureLink ($dim) modified Overworld pasture",
            )
        }
        helper.succeed()
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 100)
    fun cdShf05AreaWatchEvictsOldestAtSeventeenAndSurvivesRapidOffOn(helper: GameTestHelper) {
        val placer = TestKit.owner(helper, "placer")
        val viewer = TestKit.listedOwner(helper, BlockPos(11, 1, 14), "viewer")
        TestKit.afterTest(helper) { AreaWatchers.forget(viewer.uuid) }

        val pastures = buildList {
            for (z in listOf(2, 6, 10)) {
                for (x in listOf(2, 5, 8, 11, 14, 17)) {
                    if (size < 17) {
                        val be = TestKit.placePasture(helper, BlockPos(x, 1, z), placer)
                        TestKit.workMode(be, true)
                        add(be)
                    }
                }
            }
        }
        val first = pastures.first().blockPos
        DynamicsPayloads.onAreaWatch(viewer, AreaWatchPayload(first, watching = true))
        val historyBeforeOff = AreaWatchers.historyOf(viewer.uuid).size
        DynamicsPayloads.onAreaWatch(viewer, AreaWatchPayload(first, watching = false))
        helper.assertTrue(
            AreaWatchers.historyOf(viewer.uuid).size == historyBeforeOff,
            "client-initiated unwatch emitted an inactive AreaBoundsPayload",
        )
        helper.assertFalse(AreaWatchers.isWatching(viewer.uuid, helper.level, first), "unwatch kept server watch")

        DynamicsPayloads.onAreaWatch(viewer, AreaWatchPayload(first, watching = true))
        val rewatched = AreaWatchers.lastSent(viewer.uuid)
        helper.assertTrue(
            AreaWatchers.isWatching(viewer.uuid, helper.level, first) &&
                rewatched != null &&
                rewatched.active &&
                rewatched.pos == first,
            "rapid OFF -> ON failed to keep active watch: $rewatched",
        )

        for (i in 1 until 16) {
            DynamicsPayloads.onAreaWatch(viewer, AreaWatchPayload(pastures[i].blockPos, watching = true))
        }
        helper.assertTrue(
            pastures.take(16).all { AreaWatchers.isWatching(viewer.uuid, helper.level, it.blockPos) },
            "fewer than 16 watches active before 17th",
        )

        val seventeenth = pastures[16].blockPos
        DynamicsPayloads.onAreaWatch(viewer, AreaWatchPayload(seventeenth, watching = true))
        val recent = AreaWatchers.historyOf(viewer.uuid).takeLast(2)
        helper.assertTrue(
            recent.size == 2 &&
                recent[0] == AreaBoundsPayload.inactive(first) &&
                recent[1].pos == seventeenth &&
                recent[1].active,
            "17th watch did not emit inactive(oldest) then active(17th): $recent",
        )
        helper.assertFalse(AreaWatchers.isWatching(viewer.uuid, helper.level, first), "oldest watch remained subscribed at 17")
        helper.assertTrue(
            pastures.drop(1).all { AreaWatchers.isWatching(viewer.uuid, helper.level, it.blockPos) },
            "watches 2..17 did not remain active",
        )
        helper.succeed()
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 300)
    fun cdShf09SleeperAtReportsNullWhileWalkingToBedAndNameWhileSleeping(helper: GameTestHelper) {
        val placer = TestKit.owner(helper, "placer")
        val pasture = TestKit.placePasture(helper, BlockPos(3, 1, 7), placer)
        val bedRel = BlockPos(10, 1, 7)
        helper.setBlock(bedRel, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        val bedPos = helper.absolutePos(bedRel)
        val charmander = TestKit.tether(helper, pasture, placer, "charmander level=20")
        TestKit.workMode(pasture, true)

        val engine = TestKit.engine(helper)
        var tiredSet = false
        var sawToBed = false
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            if (!tiredSet) {
                worker.stamina.fraction = 0.0
                worker.tired = true
                tiredSet = true
                return@onEachTick
            }
            val claim = TestKit.claimOf(helper, charmander)
            if (worker.status == WorkerStatus.TO_BED && claim?.kind == ClaimKind.BED) {
                sawToBed = true
                helper.assertTrue(
                    engine.sleeperAt(helper.level, bedPos) == null,
                    "TO_BED worker was reported as current bed sleeper",
                )
            } else if (worker.status == WorkerStatus.SLEEPING) {
                helper.assertTrue(sawToBed, "worker reached SLEEPING without passing through TO_BED")
                helper.assertTrue(
                    engine.sleeperAt(helper.level, bedPos) == charmander.pokemon.getDisplayName(),
                    "SLEEPING worker was not reported by sleeperAt",
                )
                helper.succeed()
            }
        }
    }
}
