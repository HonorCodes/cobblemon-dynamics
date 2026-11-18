package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.gametest.framework.GameTestHelper

/** What a stalled test's worker shows, so that a failure at the timeout says where the work stopped. */
object CompatDiag {
    fun describe(helper: GameTestHelper, entity: PokemonEntity): String {
        val worker = TestKit.workerOf(helper, entity)
        val claim = TestKit.claimOf(helper, entity)
        val binding = BindingStore.read(entity.pokemon)
        val now = helper.level.gameTime
        return "the ${entity.pokemon.species.name} is ${worker?.status} for ${worker?.let { now - it.statusSince }} ticks at " +
            "${TestKit.relative(helper, entity.blockPosition())} (on ground ${entity.onGround()}, no gravity ${entity.isNoGravity}, " +
            "flying ${entity.isFlying()}), leashed ${worker?.leashed}, target ${worker?.target?.let { TestKit.relative(helper, it) }}, " +
            "spot ${worker?.spot?.let { TestKit.relative(helper, it) }}, missed arrival ${worker?.missedArrival}, retry at " +
            "${worker?.retryAt?.let { it - now }}, skip ${worker?.skipUntil?.size}, stalled ${worker?.stalledUntil?.size}, claim " +
            "${claim?.kind} ${claim?.key}, role ${TestKit.engine(helper).roleOf(entity.pokemon.uuid)?.definition?.id}, binding " +
            "${binding?.roleId} at ${binding?.target?.let { TestKit.relative(helper, it) }}"
    }

    /** Fails [ticksBefore] ticks before [timeout] with [describe] of [entity], unless the test has ended. */
    fun failBeforeTimeout(helper: GameTestHelper, timeout: Int, entity: PokemonEntity, ticksBefore: Int = 10) {
        helper.runAtTickTime(timeout - ticksBefore.toLong()) { helper.fail(describe(helper, entity)) }
    }
}
