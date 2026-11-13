package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.Worker
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.pasture.WorkModeService
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.registry.DynamicsItems
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket
import com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.authlib.GameProfile
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.network.Connection
import net.minecraft.network.PacketSendListener
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.players.PlayerList
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.schedule.Activity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.items.IItemHandler
import java.lang.reflect.Field
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
    fun clickBlock(
        helper: GameTestHelper,
        player: ServerPlayer,
        rel: BlockPos,
        face: Direction = Direction.UP,
        sneak: Boolean = false,
    ): InteractionResult {
        val pos = helper.absolutePos(rel)
        val hit = BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false)
        player.isShiftKeyDown = sneak
        try {
            return player.gameMode.useItemOn(player, helper.level, player.mainHandItem, InteractionHand.MAIN_HAND, hit)
        } finally {
            player.isShiftKeyDown = false
        }
    }

    /** Selects [entity] with a new Work Leash, then clicks the block at [rel]. Returns the binding that the clicks made. */
    fun bind(helper: GameTestHelper, player: ServerPlayer, entity: PokemonEntity, rel: BlockPos): Binding? {
        leash(player)
        clickEntity(player, entity)
        clickBlock(helper, player, rel)
        return BindingStore.read(entity.pokemon)
    }

    /** Selects [entity] with a new Work Leash, clicks [source], then [destination]. Returns the binding that the clicks made. */
    fun bindTransfer(
        helper: GameTestHelper,
        player: ServerPlayer,
        entity: PokemonEntity,
        source: BlockPos,
        destination: BlockPos,
        sourceFace: Direction = Direction.UP,
        destinationFace: Direction = Direction.UP,
    ): Binding? {
        leash(player)
        clickEntity(player, entity)
        clickBlock(helper, player, source, sourceFace)
        clickBlock(helper, player, destination, destinationFace)
        return BindingStore.read(entity.pokemon)
    }

    /** A double chest whose left half sits at [rel], facing [facing]: one 54-slot item handler. */
    fun doubleChest(helper: GameTestHelper, rel: BlockPos, facing: Direction): IItemHandler {
        val chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)
        helper.setBlock(rel, chest.setValue(ChestBlock.TYPE, ChestType.LEFT))
        helper.setBlock(rel.relative(facing.clockWise), chest.setValue(ChestBlock.TYPE, ChestType.RIGHT))
        val handler = items(helper, rel) ?: throw GameTestAssertException("no item handler at the double chest")
        if (handler.slots != 54) throw GameTestAssertException("the double chest has ${handler.slots} slots, not 54")
        return handler
    }

    /** The unsided item handler of the block at [rel], or null. */
    fun items(helper: GameTestHelper, rel: BlockPos): IItemHandler? =
        helper.level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(rel), null)

    /** How many of [item] the block at [rel] holds. */
    fun countIn(helper: GameTestHelper, rel: BlockPos, item: Item): Int {
        val handler = items(helper, rel) ?: return 0
        return (0 until handler.slots).sumOf { slot -> handler.getStackInSlot(slot).takeIf { it.`is`(item) }?.count ?: 0 }
    }

    /**
     * How many of [item] lie on the ground of the test, as item entities. Only the test's own structure counts, with a margin for
     * items that fly out of it, because the tests of one batch run side by side.
     */
    fun countOnTheGround(helper: GameTestHelper, item: Item): Int =
        helper.level.getEntitiesOfClass(ItemEntity::class.java, helper.bounds.inflate(2.0)) { it.item.`is`(item) }.sumOf { it.item.count }

    /** Every message that the mod sent [player], action bar and chat, in order, as text. Fake players drop the messages themselves. */
    fun messages(player: ServerPlayer): List<String> = Feedback.recorded[player.uuid].orEmpty().map { it.string }

    /** The last message that the mod sent [player], such as the action bar reply to a leash click. */
    fun lastMessage(player: ServerPlayer): String? = messages(player).lastOrNull()

    /** The first leash binding on the block target at [pos], as the level's leash index holds it. Only a container holds more. */
    fun reservationAt(helper: GameTestHelper, pos: BlockPos): LeashIndex.Ref? =
        helper.level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX)?.at(pos)?.firstOrNull()

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

    /**
     * A fake owner at [rel] that `PlayerList.getPlayer` finds, as Cobblemon's owner lookup and Quick Work need. It joins only the player
     * list's UUID map, so it gets no broadcast, no spawns and no ticks, and it leaves the map when the test ends.
     */
    fun listedOwner(helper: GameTestHelper, rel: BlockPos, name: String = "owner"): ServerPlayer = listed(helper, owner(helper, name), rel)

    /** Puts [player] at [rel] and into the player list's UUID map, which it leaves when the test ends, as for [listedOwner]. */
    fun <P : ServerPlayer> listed(helper: GameTestHelper, player: P, rel: BlockPos): P {
        val pos = helper.absoluteVec(rel.bottomCenter)
        place(player, pos.x, pos.y, pos.z)
        val byUuid = playersByUuid(helper)
        byUuid[player.uuid] = player
        afterTest(helper) { byUuid.remove(player.uuid) }
        return player
    }

    /** A player of the exact class ServerPlayer and the packets that the server sent it (CD-QWK-31, CD-QWK-32, CD-LND-10). */
    class PlainOwner(val player: ServerPlayer, val packets: MutableList<Packet<*>>)

    /**
     * An owner at [rel] that `PlayerList.getPlayer` finds until the test ends, as [listedOwner], but of the exact class ServerPlayer,
     * which Architectury and FTB Chunks judge as a player and not as a fake player. Its connection has no Netty channel and keeps every
     * packet, as the listener of `EffectPacketTests` does. It claims every channel, because FTB Library asks before it sends.
     */
    fun plainOwner(helper: GameTestHelper, rel: BlockPos, name: String = "owner", permissionLevel: Int = 0): PlainOwner {
        val uuid = UUID.randomUUID()
        val profile = GameProfile(uuid, "${name}_${uuid.toString().take(6)}")
        val server = helper.level.server
        val player = if (permissionLevel > 0) {
            LeveledPlayer(server, helper.level, profile, permissionLevel)
        } else {
            ServerPlayer(server, helper.level, profile, ClientInformation.createDefault())
        }
        val packets = ArrayList<Packet<*>>()
        // The constructor sets player.connection to this listener.
        object : ServerGamePacketListenerImpl(
            server,
            Connection(PacketFlow.CLIENTBOUND),
            player,
            CommonListenerCookie.createInitial(profile, false),
        ) {
            override fun send(packet: Packet<*>) {
                packets += packet
            }

            override fun send(packet: Packet<*>, listener: PacketSendListener?) {
                packets += packet
            }

            override fun hasChannel(payloadId: ResourceLocation): Boolean = true
        }
        return PlainOwner(listed(helper, player, rel), packets)
    }

    /** A plain owner with a permission level, such as an operator of level 2. The GameTest server gives every player level 0. */
    private class LeveledPlayer(server: MinecraftServer, level: ServerLevel, profile: GameProfile, private val permission: Int) :
        ServerPlayer(server, level, profile, ClientInformation.createDefault()) {
        override fun getPermissionLevel(): Int = permission
    }

    /** The system chat packets that [owner] got: action-bar messages with [overlay] true, chat messages with it false. */
    fun systemMessages(owner: PlainOwner, overlay: Boolean): List<String> =
        owner.packets.filterIsInstance<ClientboundSystemChatPacket>().filter { it.overlay() == overlay }.map { it.content().string }

    /** The last action-bar message that [owner] got, from Quick Work or from a claim mod. */
    fun lastActionBar(owner: PlainOwner): String? = systemMessages(owner, overlay = true).lastOrNull()

    /** Takes [player] out of the player list's UUID map, so that `PlayerList.getPlayer` no longer finds it, as after a logout. */
    fun unlist(helper: GameTestHelper, player: ServerPlayer) {
        playersByUuid(helper).remove(player.uuid)
    }

    @Suppress("UNCHECKED_CAST")
    private fun playersByUuid(helper: GameTestHelper): MutableMap<UUID, ServerPlayer> =
        PLAYERS_BY_UUID.get(helper.level.server.playerList) as MutableMap<UUID, ServerPlayer>

    private val PLAYERS_BY_UUID: Field = PlayerList::class.java.getDeclaredField("playersByUUID").apply { isAccessible = true }

    /** Turns [player]'s eyes to the center of the block at [rel], as a client's rotation packets do on a real server. */
    fun lookAt(helper: GameTestHelper, player: ServerPlayer, rel: BlockPos) =
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(Vec3.atCenterOf(rel)))

    /** A new Pokémon in the first free slot of [player]'s party. */
    fun partyPokemon(player: ServerPlayer, spec: String): Pokemon = pokemon(spec).also { Cobblemon.storage.getParty(player).add(it) }

    /** The R key: Cobblemon's own server handler of the send-out packet for party slot [slot]. It recalls a Pokémon that is out. */
    fun pressSendOut(player: ServerPlayer, slot: Int = 0) = SendOutPokemonHandler.handle(SendOutPokemonPacket(slot), player.server, player)

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

    /** Another mod's block by its id, so that a test places it with no compile dependency on that mod, such as AE2. */
    fun ae2Block(id: String): Block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow {
        GameTestAssertException("no block $id is registered")
    }

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

    /** [lane] is the claim lane of a farming role (CD-CRP-06), and null for every other role. */
    fun keyOf(helper: GameTestHelper, pos: BlockPos, lane: String? = null): TargetKey.Block =
        TargetKey.block(helper.level.dimension().location(), pos, lane)

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
