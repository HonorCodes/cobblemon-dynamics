package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.Worker
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.pasture.WorkModeService
import codes.honor.cobblemondynamics.registry.DynamicsItems
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.util.OptionalInt
import java.util.UUID
import java.util.function.Consumer

/** Builds GameTest scenes: owners, pastures and Pokémon. */
object TestKit {
    /**
     * A fake owner with a unique profile. Vanilla's mock player joins the player list, and its connection rejects
     * Cobblemon's login payloads, so the tests use NeoForge's FakePlayer, whose connection drops every packet.
     */
    fun owner(helper: GameTestHelper, name: String = "owner"): ServerPlayer {
        val uuid = UUID.randomUUID()
        val player = FakePlayerFactory.get(helper.level, GameProfile(uuid, "${name}_${uuid.toString().take(6)}"))
        val center = helper.absoluteVec(BlockPos(1, 1, 1).bottomCenter)
        player.moveTo(center.x, center.y, center.z)
        return player
    }

    /**
     * A fake player with permission level 2. The GameTest server ops every player at level 0
     * (`getOperatorUserPermissionLevel`), so `PlayerList.op` cannot make one.
     */
    fun operator(helper: GameTestHelper, name: String = "operator"): ServerPlayer {
        val uuid = UUID.randomUUID()
        val player = OperatorPlayer(helper.level, GameProfile(uuid, "${name}_${uuid.toString().take(6)}"))
        val center = helper.absoluteVec(BlockPos(1, 1, 1).bottomCenter)
        player.moveTo(center.x, center.y, center.z)
        return player
    }

    private class OperatorPlayer(level: ServerLevel, profile: GameProfile) : FakePlayer(level, profile) {
        override fun getPermissionLevel(): Int = 2
    }

    /** A fake player that counts the menus that blocks open for it. A plain FakePlayer drops a menu before it opens. */
    class MenuCountingPlayer(level: ServerLevel, profile: GameProfile) : FakePlayer(level, profile) {
        var menusOpened = 0
            private set

        override fun openMenu(menu: MenuProvider?, extraDataWriter: Consumer<RegistryFriendlyByteBuf>?): OptionalInt {
            menusOpened++
            return OptionalInt.empty()
        }
    }

    fun menuCountingOwner(helper: GameTestHelper, name: String = "owner"): MenuCountingPlayer {
        val uuid = UUID.randomUUID()
        val player = MenuCountingPlayer(helper.level, GameProfile(uuid, "${name}_${uuid.toString().take(6)}"))
        val center = helper.absoluteVec(BlockPos(1, 1, 1).bottomCenter)
        player.moveTo(center.x, center.y, center.z)
        return player
    }

    /** Puts a new Work Leash in [player]'s main hand. */
    fun leash(player: ServerPlayer): ItemStack {
        val stack = ItemStack(DynamicsItems.WORK_LEASH.get())
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        return stack
    }

    /** A main-hand right-click on [entity] through `Player.interactOn`, so NeoForge's event and Cobblemon's handlers run. */
    fun clickEntity(player: ServerPlayer, entity: Entity, sneak: Boolean = false): InteractionResult {
        player.isShiftKeyDown = sneak
        try {
            return player.interactOn(entity, InteractionHand.MAIN_HAND)
        } finally {
            player.isShiftKeyDown = false
        }
    }

    /** A main-hand right-click on [face] of the block at [rel], through `ServerPlayerGameMode.useItemOn`, as the packet handler calls it. */
    fun clickBlock(helper: GameTestHelper, player: ServerPlayer, rel: BlockPos, face: Direction = Direction.UP): InteractionResult {
        val pos = helper.absolutePos(rel)
        val hit = BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false)
        return player.gameMode.useItemOn(player, helper.level, player.mainHandItem, InteractionHand.MAIN_HAND, hit)
    }

    /** Selects [entity] with a new Work Leash, then clicks the block at [rel]. Returns the binding that the clicks made. */
    fun bind(helper: GameTestHelper, player: ServerPlayer, entity: PokemonEntity, rel: BlockPos): Binding? {
        leash(player)
        clickEntity(player, entity)
        clickBlock(helper, player, rel)
        return BindingStore.read(entity.pokemon)
    }

    /** Puts [player] 2 blocks east of the pasture block, in reach of its Work Mode button. */
    fun standBeside(player: ServerPlayer, pasture: PokemonPastureBlockEntity) {
        place(player, pasture.blockPos.x + 2.5, pasture.blockPos.y.toDouble(), pasture.blockPos.z + 0.5)
    }

    /** Moves a fake player. Its `teleportTo` does nothing, because NeoForge's fake connection drops every teleport. */
    fun place(player: ServerPlayer, x: Double, y: Double, z: Double) {
        player.moveTo(x, y, z)
    }

    /** Sets Work Mode with no permission check. */
    fun workMode(pasture: PokemonPastureBlockEntity, on: Boolean) {
        WorkModeService.apply(pasture.level as ServerLevel, pasture.blockPos, on)
    }

    fun placePasture(helper: GameTestHelper, rel: BlockPos, owner: ServerPlayer?): PokemonPastureBlockEntity {
        val level = helper.level
        val pos = helper.absolutePos(rel)
        val state = CobblemonBlocks.PASTURE.defaultBlockState()
        level.setBlock(pos, state, 3)
        CobblemonBlocks.PASTURE.setPlacedBy(level, pos, state, owner, ItemStack.EMPTY)
        return level.getBlockEntity(pos) as? PokemonPastureBlockEntity
            ?: throw GameTestAssertException("no pasture block entity at $pos")
    }

    fun pokemon(spec: String): Pokemon = PokemonProperties.parse(spec).create()

    fun tether(helper: GameTestHelper, pasture: PokemonPastureBlockEntity, owner: ServerPlayer, spec: String): PokemonEntity {
        val pokemon = pokemon(spec)
        Cobblemon.storage.getPC(owner).add(pokemon)
        if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for $spec")
        return pasturedEntity(helper, pasture, pokemon.uuid) ?: throw GameTestAssertException("no entity after tether for $spec")
    }

    /** Pokemon.entity is set only for sent-out Pokémon, so a pastured entity is found in the level by its Pokémon UUID. */
    fun pasturedEntity(helper: GameTestHelper, pasture: PokemonPastureBlockEntity, pokemonId: UUID): PokemonEntity? =
        helper.level.getEntitiesOfClass(PokemonEntity::class.java, AABB(pasture.blockPos).inflate(48.0)) {
            it.pokemon.uuid == pokemonId && it.tethering != null
        }.firstOrNull()

    fun wild(helper: GameTestHelper, rel: BlockPos, spec: String): PokemonEntity {
        val entity = PokemonEntity(helper.level, pokemon(spec))
        val pos = helper.absoluteVec(rel.bottomCenter)
        entity.moveTo(pos.x, pos.y, pos.z)
        helper.level.addFreshEntity(entity)
        return entity
    }

    fun engine(helper: GameTestHelper): WorkEngine = WorkEngine.require(helper.level.server)

    fun furnace(
        helper: GameTestHelper,
        rel: BlockPos,
        input: ItemStack?,
        fuel: ItemStack? = null,
        block: Block = Blocks.FURNACE,
    ): AbstractFurnaceBlockEntity {
        helper.setBlock(rel, block)
        val furnace = helper.getBlockEntity<AbstractFurnaceBlockEntity>(rel)
        input?.let { furnace.setItem(0, it) }
        fuel?.let { furnace.setItem(1, it) }
        return furnace
    }

    fun workerOf(helper: GameTestHelper, entity: PokemonEntity): Worker? = engine(helper).worker(entity.pokemon.uuid)

    fun claimOf(helper: GameTestHelper, entity: PokemonEntity): Claim? = engine(helper).claims.claimOf(entity.pokemon.uuid)

    fun keyOf(helper: GameTestHelper, pos: BlockPos): TargetKey.Block = TargetKey.block(helper.level.dimension().location(), pos)

    /**
     * [absolute] relative to the test's origin, the inverse of `helper.absolutePos` for an unrotated test. Vanilla 1.21.1's
     * `GameTestHelper.relativePos` turns the position 180 degrees, so it gives (-x, y, -z).
     */
    fun relative(helper: GameTestHelper, absolute: BlockPos): BlockPos = absolute.subtract(helper.absolutePos(BlockPos.ZERO))

    /** Teleports [entity] to the bottom center of [rel], which must lie inside its roam box. */
    fun moveTo(helper: GameTestHelper, entity: PokemonEntity, rel: BlockPos) {
        val pos = helper.absoluteVec(rel.bottomCenter)
        entity.navigation.stop()
        entity.teleportTo(pos.x, pos.y, pos.z)
    }

    /** Releases [entity] through the engine, and keeps it off [target] for 200 ticks, so that the next scan cannot claim it again. */
    fun release(helper: GameTestHelper, entity: PokemonEntity, target: BlockPos) {
        val worker = workerOf(helper, entity) ?: throw GameTestAssertException("${entity.pokemon.species.name} is no worker")
        worker.skipUntil[keyOf(helper, target)] = helper.level.gameTime + 200
        engine(helper).release(entity.pokemon.uuid)
    }

    /**
     * The GameTest form of a restart: every in-memory claim and worker is gone. Bindings and the leash's selection and pending
     * source are saved data, so they stay.
     */
    fun simulateRestart(helper: GameTestHelper) {
        engine(helper).resetRuntimeForTest()
    }

    /** The activities that [brain] can run. Brain keeps its activity table private, so the tests read it through reflection. */
    fun activitiesOf(brain: Brain<*>): Set<Activity> = brainField<Map<Activity, *>>(brain, "activityRequirements").keys

    /** The tasks that [activity] holds in [brain], at every priority. */
    fun tasksOf(brain: Brain<*>, activity: Activity): List<BehaviorControl<*>> =
        brainField<Map<Int, Map<Activity, Set<BehaviorControl<*>>>>>(brain, "availableBehaviorsByPriority").values.flatMap {
            it[activity].orEmpty()
        }

    /** Vanilla marks this getter as a debug view. */
    @Suppress("DEPRECATION")
    fun runningTasks(brain: Brain<*>): List<BehaviorControl<*>> = brain.runningBehaviors

    @Suppress("UNCHECKED_CAST")
    private fun <T> brainField(brain: Brain<*>, name: String): T =
        Brain::class.java.getDeclaredField(name).apply { isAccessible = true }.get(brain) as T

    /** Runs [action] when the test passes or fails, to put back global state that the test changed. */
    fun afterTest(helper: GameTestHelper, action: () -> Unit) {
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(testInfo: GameTestInfo) {}

                override fun testPassed(test: GameTestInfo, runner: GameTestRunner) = action()

                override fun testFailed(test: GameTestInfo, runner: GameTestRunner) = action()

                override fun testAddedForRerun(oldTest: GameTestInfo, newTest: GameTestInfo, runner: GameTestRunner) {}
            },
        )
    }
}
