package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.fx.EffectPlayer
import codes.honor.cobblemondynamics.fx.JarParticleCatalog
import codes.honor.cobblemondynamics.fx.WorkFocusEntity
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.registry.DynamicsEntities
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.vehicle.Boat
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The work effects against the real Cobblemon jar, and the work focus entity in a real level. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class EffectTests {
    /** A Work Mode pasture at [pastureRel], and one Lv10 Charmander for each furnace at [furnaces], each with [input] raw iron. */
    private fun stokers(helper: GameTestHelper, pastureRel: BlockPos, furnaces: List<BlockPos>, input: Int = 8): List<PokemonEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, pastureRel, owner)
        TestKit.workMode(pasture, true)
        furnaces.forEach { TestKit.furnace(helper, it, ItemStack(Items.RAW_IRON, input)) }
        return furnaces.map { TestKit.tether(helper, pasture, owner, "charmander level=10") }
    }

    private fun isWorking(helper: GameTestHelper, entity: PokemonEntity) = TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING

    /** Every focus entity in this test's structure. A worker's focus sits at its target's center, inside the structure. */
    private fun focuses(helper: GameTestHelper): List<WorkFocusEntity> =
        helper.level.getEntitiesOfClass(WorkFocusEntity::class.java, helper.bounds)

    /** The focus entities that touch the block at [pos]. */
    private fun focusesAt(helper: GameTestHelper, pos: BlockPos): List<WorkFocusEntity> =
        helper.level.getEntitiesOfClass(WorkFocusEntity::class.java, AABB(pos).inflate(0.25))

    /** CD-FX-11 against the real Cobblemon jar: the Stoker's particle ids are in its particle files, and an unknown id is not. */
    @GameTest(template = "floor_9x5x9")
    fun cdFx11CatalogReadsCobblemonParticles(helper: GameTestHelper) {
        val stoker = RoleRegistry.current.get(id("stoker"))?.definition ?: throw GameTestAssertException("no Stoker role")
        for (particle in stoker.effect.actorParticles + stoker.effect.targetParticles) {
            helper.assertTrue(JarParticleCatalog.contains(particle), "$particle is not in the loaded Cobblemon")
        }
        val unknown = ResourceLocation.fromNamespaceAndPath("cobblemon", "no_such_particle")
        helper.assertFalse(JarParticleCatalog.contains(unknown), "the catalog holds $unknown")
        helper.succeed()
    }

    /**
     * No focus entity exists before the Stoker Works. For its first 80 Working ticks exactly one does, within 0.01 blocks of the
     * furnace's center (CD-FX-04), invisible, not pickable and with no collision box (CD-FX-05). The furnace's center lies inside the
     * block, so a player walks through a focus entity in open air, and the same walk stops at a boat, which shows that it checks the
     * entities in its way.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable(helper: GameTestHelper) {
        val furnaceRel = BlockPos(7, 1, 3)
        val fire = stokers(helper, BlockPos(7, 1, 7), listOf(furnaceRel)).single()
        val center = Vec3.atCenterOf(helper.absolutePos(furnaceRel))
        var since = -1L
        helper.onEachTick {
            if (!isWorking(helper, fire)) {
                helper.assertTrue(since < 0, "the Stoker stopped Working ${helper.tick - since} ticks after it started")
                helper.assertTrue(focuses(helper).isEmpty(), "CD-FX-07: a focus entity exists before the Stoker Works")
                return@onEachTick
            }
            if (since < 0) since = helper.tick
            val all = focuses(helper)
            helper.assertTrue(all.size == 1, "CD-FX-04: ${all.size} focus entities while one Stoker Works")
            val focus = all.single()
            val off = focus.position().distanceTo(center)
            helper.assertTrue(off <= 0.01, "CD-FX-04: the focus entity is $off blocks from the furnace's center")
            helper.assertTrue(focus.isInvisible, "CD-FX-05: the focus entity is visible")
            helper.assertFalse(focus.isPickable(), "CD-FX-05: the focus entity is pickable")
            helper.assertFalse(focus.canBeCollidedWith(), "CD-FX-05: the focus entity has a collision box")
            if (helper.tick - since < 80) return@onEachTick
            assertWalksThroughAFocus(helper)
            helper.succeed()
        }
    }

    /**
     * A new player, which is not on the ground and so never steps up, walks 2 blocks along the floor through a focus entity that
     * [EffectPlayer.spawnFocus] made. The same walk into a boat, whose collision box blocks players, stops before it.
     */
    private fun assertWalksThroughAFocus(helper: GameTestHelper) {
        val start = helper.absoluteVec(Vec3(2.5, 2.0, 10.5))
        val walk = Vec3(2.0, 0.0, 0.0)
        val focus = EffectPlayer.spawnFocus(helper.level, start.add(1.0, 0.5, 0.0))
        val walker = TestKit.owner(helper, "walker")
        try {
            walker.moveTo(start.x, start.y, start.z)
            walker.move(MoverType.SELF, walk)
            val walked = walker.x - start.x
            val through = walked > 1.99 && !walker.horizontalCollision
            helper.assertTrue(through, "CD-FX-05: a focus entity stopped the walker after $walked blocks")
        } finally {
            EffectPlayer.removeFocus(focus)
        }
        val boat = Boat(helper.level, start.x + 1.0, start.y, start.z)
        helper.level.addFreshEntity(boat)
        val control = TestKit.owner(helper, "control")
        try {
            control.moveTo(start.x, start.y, start.z)
            control.move(MoverType.SELF, walk)
            val walked = control.x - start.x
            helper.assertTrue(walked < 1.0, "the walk went $walked blocks through a boat, so it checks no entity in its way")
        } finally {
            boat.discard()
        }
    }

    /**
     * Five Stokers Work, so five focus entities exist. The save of their chunks writes what `EntityStorage.storeEntities` writes: the tag
     * of each entity whose `save` returns true. That holds the five Stokers and no focus entity (CD-FX-06).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 800)
    fun cdFx06FocusIsNeverSaved(helper: GameTestHelper) {
        val furnaces = listOf(BlockPos(5, 1, 5), BlockPos(11, 1, 5), BlockPos(17, 1, 5), BlockPos(5, 1, 17), BlockPos(17, 1, 17))
        val fire = stokers(helper, BlockPos(11, 1, 11), furnaces)
        val level = helper.level
        helper.onEachTick {
            if (!fire.all { isWorking(helper, it) }) return@onEachTick
            val all = focuses(helper)
            helper.assertTrue(all.size == furnaces.size, "${all.size} focus entities for ${furnaces.size} Working Stokers")
            helper.assertFalse(DynamicsEntities.WORK_FOCUS.get().canSerialize(), "CD-FX-06: the focus entity type is saved")
            val chunks = (all + fire).map { ChunkPos(it.blockPosition()) }.toSet()
            val saved = chunks.flatMap { chunk ->
                val column = AABB(
                    chunk.minBlockX.toDouble(),
                    level.minBuildHeight.toDouble(),
                    chunk.minBlockZ.toDouble(),
                    chunk.maxBlockX + 1.0,
                    level.maxBuildHeight.toDouble(),
                    chunk.maxBlockZ + 1.0,
                )
                level.getEntities(null as Entity?, column) { true }.mapNotNull { entity -> CompoundTag().takeIf { entity.save(it) } }
            }
            val ids = saved.map { it.getString("id") }
            val stokersSaved = saved.count { it.getString("id") == "cobblemon:pokemon" }
            helper.assertTrue(stokersSaved >= fire.size, "the save of the chunks holds $ids, not the ${fire.size} Stokers")
            helper.assertFalse("$MOD_ID:work_focus" in ids, "CD-FX-06: the save of the chunks holds a focus entity: $ids")
            helper.succeed()
        }
    }

    /**
     * Two Stokers Work. One furnace runs out of input, so its Stoker goes to Standby, and the other Stoker's claim is released. One tick
     * later neither focus entity remains, and the Standby worker has none for the next 40 ticks (CD-FX-07).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdFx07FocusLeavesWithWorking(helper: GameTestHelper) {
        val fire = stokers(helper, BlockPos(7, 1, 7), listOf(BlockPos(3, 1, 3), BlockPos(11, 1, 3)))
        val (waiting, released) = fire
        var changedAt = -1L
        var waitingAt = BlockPos.ZERO
        var releasedAt = BlockPos.ZERO
        helper.onEachTick {
            if (changedAt < 0) {
                if (!fire.all { isWorking(helper, it) }) return@onEachTick
                waitingAt = TestKit.workerOf(helper, waiting)?.target ?: throw GameTestAssertException("a Working Stoker with no target")
                releasedAt = TestKit.workerOf(helper, released)?.target ?: throw GameTestAssertException("a Working Stoker with no target")
                for (target in listOf(waitingAt, releasedAt)) {
                    helper.assertTrue(focusesAt(helper, target).size == 1, "CD-FX-04: no single focus entity at the furnace at $target")
                }
                (helper.level.getBlockEntity(waitingAt) as AbstractFurnaceBlockEntity).setItem(0, ItemStack.EMPTY)
                TestKit.release(helper, released, releasedAt)
                changedAt = helper.tick
                return@onEachTick
            }
            val elapsed = helper.tick - changedAt
            val status = TestKit.workerOf(helper, waiting)?.status
            helper.assertTrue(status == WorkerStatus.STANDBY, "the Stoker whose furnace ran out is $status $elapsed ticks later")
            helper.assertTrue(focusesAt(helper, waitingAt).isEmpty(), "CD-FX-07: the Standby worker has a focus $elapsed ticks later")
            helper.assertTrue(focusesAt(helper, releasedAt).isEmpty(), "CD-FX-07: a focus entity remains $elapsed ticks after its release")
            if (elapsed >= 40) helper.succeed()
        }
    }

    /**
     * Ten Stokers claim ten furnaces, and Work Mode turns off, which releases the ten claims, and on again: five rounds. On every tick
     * the test's focus entities number at most its work claims (CD-PERF-04). Each round ends with every Working Stoker holding one focus
     * entity, so focus entities exist when the claims end.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 3_000)
    fun cdPerf04NoFocusOutlivesItsClaim(helper: GameTestHelper) {
        val rels = listOf(3, 7, 11, 15, 19).flatMap { x -> listOf(BlockPos(x, 1, 5), BlockPos(x, 1, 17)) }
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        rels.forEach { TestKit.furnace(helper, it, ItemStack(Items.RAW_IRON, 64)) }
        val fire = rels.map { TestKit.tether(helper, pasture, owner, "charmander level=10") }
        val ids = fire.map { it.pokemon.uuid }.toSet()
        val engine = TestKit.engine(helper)
        TestKit.workMode(pasture, true)
        var round = 1
        var claimedAt = -1L
        var offAt = -1L
        helper.onEachTick {
            val claims = engine.claims.all().count { it.kind == ClaimKind.WORK && it.worker in ids }
            val focus = focuses(helper).size
            helper.assertTrue(focus <= claims, "CD-PERF-04: $focus focus entities for $claims work claims in round $round")
            if (offAt >= 0) {
                helper.assertTrue(claims == 0, "$claims work claims remain with Work Mode off in round $round")
                if (helper.tick - offAt < 5) return@onEachTick
                if (round == ROUNDS) {
                    helper.succeed()
                    return@onEachTick
                }
                round++
                offAt = -1
                claimedAt = -1
                TestKit.workMode(pasture, true)
                return@onEachTick
            }
            if (claimedAt < 0) {
                if (claims == rels.size) claimedAt = helper.tick
                return@onEachTick
            }
            val working = fire.count { isWorking(helper, it) }
            if (working < rels.size && helper.tick - claimedAt < 200) return@onEachTick
            helper.assertTrue(working > 0 && focus == working, "round $round: $focus focus entities for $working Working Stokers")
            TestKit.workMode(pasture, false)
            offAt = helper.tick
        }
    }

    private companion object {
        const val ROUNDS = 5
    }
}
