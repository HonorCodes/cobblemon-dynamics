package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.xp.ExperienceCollectorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Soul Collector with real XP orbs, their merging and a real Experience Collector. A Gastly binds to the collector with the Work
 * Leash, which is the bind of CD-SOU-02. No player joins the level, so no orb has a player near it.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class SoulCollectorTests {
    /** An orb of [value] XP at the bottom center of [rel], with no motion. */
    private fun orb(helper: GameTestHelper, rel: BlockPos, value: Int): ExperienceOrb {
        val at = helper.absoluteVec(rel.bottomCenter)
        val orb = ExperienceOrb(helper.level, at.x, at.y, at.z, value)
        orb.deltaMovement = Vec3.ZERO
        helper.level.addFreshEntity(orb)
        return orb
    }

    /**
     * CD-SOU-04: orbs at horizontal offsets 7 and 9 from the pasture block, along the x axis. A bound Lv100 Gastly takes the orb at 7,
     * inside the work area, into the collector. The orb at 9, outside the work area, stays on the ground for 200 ticks after that.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_200)
    fun cdSou04OnlyTheOrbsOfTheWorkArea(helper: GameTestHelper) {
        val collectorRel = BlockPos(11, 2, 8)
        helper.setBlock(collectorRel, DynamicsBlocks.EXPERIENCE_COLLECTOR.get())
        val collector = helper.getBlockEntity<ExperienceCollectorBlockEntity>(collectorRel)
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val gastly = TestKit.tether(helper, pasture, owner, "gastly level=100")
        helper.assertTrue(TestKit.bind(helper, owner, gastly, collectorRel) != null, "CD-SOU-02: the bind to a collector was refused")
        val inside = orb(helper, BlockPos(18, 2, 11), 7)
        val outside = orb(helper, BlockPos(20, 2, 11), 7)
        helper.startSequence()
            .thenWaitUntil {
                helper.assertTrue(!inside.isAlive, "CD-SOU-04: the orb at offset 7 is still on the ground")
                helper.assertTrue(collector.store.stored == 7, "CD-SOU-04: the collector holds ${collector.store.stored} XP")
            }
            .thenExecuteFor(200) {
                helper.assertTrue(outside.isAlive && outside.value == 7, "CD-SOU-04: the orb at offset 9 was taken")
                helper.assertTrue(collector.store.stored == 7, "CD-SOU-04: the collector holds ${collector.store.stored} XP")
            }
            .thenSucceed()
    }

    /**
     * CD-SOU-08, CD-SOU-11: a bound Lv100 Gastly at an empty collector waits in Standby while no orb lies in the work area. Then 10 orbs
     * of 7 XP drop beside the pasture: the Gastly Works within 40 ticks, and the collector holds all 70 XP within 400 ticks, never more.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_200)
    fun cdSou08Sou11AGastlyFillsACollectorWithTheOrbsOfItsWorkArea(helper: GameTestHelper) {
        val collectorRel = BlockPos(11, 2, 8)
        helper.setBlock(collectorRel, DynamicsBlocks.EXPERIENCE_COLLECTOR.get())
        val collector = helper.getBlockEntity<ExperienceCollectorBlockEntity>(collectorRel)
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val gastly = TestKit.tether(helper, pasture, owner, "gastly level=100")
        helper.assertTrue(TestKit.bind(helper, owner, gastly, collectorRel) != null, "CD-SOU-02: the bind to a collector was refused")
        fun status() = TestKit.workerOf(helper, gastly)?.status
        helper.startSequence()
            .thenWaitUntil { helper.assertTrue(status() == WorkerStatus.STANDBY, "the Gastly is ${status()}, not in Standby yet") }
            .thenExecuteFor(40) { helper.assertTrue(status() != WorkerStatus.WORKING, "CD-SOU-08: the Gastly Works with no orb") }
            .thenExecute {
                for (i in 0 until 10) orb(helper, BlockPos(9 + i % 5, 2, 13 + i / 5), 7)
                val droppedAt = helper.level.gameTime
                var worked = false
                helper.onEachTick {
                    val age = helper.level.gameTime - droppedAt
                    if (status() == WorkerStatus.WORKING) worked = true
                    if (!worked) helper.assertTrue(age <= 40, "CD-SOU-08: the Gastly is ${status()} $age ticks after the orbs dropped")
                    val stored = collector.store.stored
                    helper.assertTrue(stored <= 70, "CD-SOU-07: the collector holds $stored XP from 70 XP of orbs")
                    if (stored == 70) {
                        helper.succeed()
                    } else {
                        helper.assertTrue(age < 400, "CD-SOU-11: the collector holds $stored XP 400 ticks after the orbs dropped")
                    }
                }
            }
    }
}
