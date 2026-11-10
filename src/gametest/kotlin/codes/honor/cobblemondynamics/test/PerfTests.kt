package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.engine.EngineTiming
import codes.honor.cobblemondynamics.engine.TickTimes
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** The namespace of the perf run, `runGameTestServerPerf`. NeoForge also reads its templates from this namespace. */
const val PERF_NAMESPACE = "cobblemon_dynamics_perf"

/**
 * CD-PERF-01, CD-PERF-02 and the runtime part of CD-PERF-05 on the reference world: 8 Work Mode pastures with 8 Working Stokers
 * each. The perf run holds this test alone, so no other test shares the server's time.
 */
@GameTestHolder(PERF_NAMESPACE)
@PrefixGameTestTemplate(false)
class PerfTests {
    @GameTest(template = "floor_41x8x41", timeoutTicks = TIMEOUT_TICKS)
    fun cdPerf01And02ReferenceWorld(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val workers = PASTURES.flatMap { center ->
            val pasture = TestKit.placePasture(helper, center, owner)
            TestKit.workMode(pasture, true)
            for (offset in FURNACE_OFFSETS) TestKit.furnace(helper, center.offset(offset), ItemStack(Items.RAW_IRON, 64))
            List(WORKERS_PER_PASTURE) { TestKit.tether(helper, pasture, owner, "charmander level=50") }
        }
        val tap = DynamicsLogTap()
        TestKit.afterTest(helper) {
            EngineTiming.stopRecording()
            tap.detach()
        }
        var allWorkingAt = -1L
        var recording = false
        helper.onEachTick {
            val working = workers.count { TestKit.workerOf(helper, it)?.status == WorkerStatus.WORKING }
            if (!recording) {
                if (allWorkingAt < 0 && working == workers.size) allWorkingAt = helper.tick
                if (allWorkingAt >= 0 && helper.tick - allWorkingAt >= WARM_UP_TICKS) {
                    tap.attach()
                    EngineTiming.startRecording(MEASURED_TICKS)
                    recording = true
                }
                return@onEachTick
            }
            helper.assertTrue(working == workers.size, "only $working of ${workers.size} workers were Working at test tick ${helper.tick}")
            val times = EngineTiming.recorded ?: return@onEachTick
            if (times.count >= MEASURED_TICKS) finish(helper, times, tap, workers.size)
        }
    }

    private fun finish(helper: GameTestHelper, times: TickTimes, tap: DynamicsLogTap, workers: Int) {
        EngineTiming.stopRecording()
        tap.detach()
        val mean = times.meanMillis()
        val p95 = times.percentileMillis(PERCENTILE)
        LOGGER.info(
            "CD-PERF-01 mean {} ms, CD-PERF-02 p95 {} ms of Dynamics server time per tick, over {} ticks with {} Working workers",
            millis(mean),
            millis(p95),
            times.count,
            workers,
        )
        helper.assertTrue(mean <= MEAN_BUDGET_MS, "CD-PERF-01: the mean of ${millis(mean)} ms per tick is above $MEAN_BUDGET_MS ms")
        helper.assertTrue(p95 <= P95_BUDGET_MS, "CD-PERF-02: the p95 of ${millis(p95)} ms per tick is above $P95_BUDGET_MS ms")
        val lines = tap.lines.toList()
        val notWarnings = lines.filter { it.level != Level.WARN }
        helper.assertTrue(notWarnings.isEmpty(), "CD-PERF-05: Dynamics logged lines that are not warnings: $notWarnings")
        val repeated = lines.groupingBy { it.message }.eachCount().filterValues { it > 1 }
        helper.assertTrue(repeated.isEmpty(), "CD-PERF-05: Dynamics logged these warnings more than once: $repeated")
        LOGGER.info("CD-PERF-05 {} Dynamics log lines after the warm-up: {}", lines.size, lines)
        helper.succeed()
    }

    private fun millis(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    /** Collects what the loggers of the main mod write at INFO or above while it is attached (CD-PERF-05). */
    private class DynamicsLogTap : AbstractAppender(TAP_NAME, null, null, true, Property.EMPTY_ARRAY) {
        data class Line(val level: Level, val logger: String, val message: String)

        val lines = CopyOnWriteArrayList<Line>()
        private var context: LoggerContext? = null

        override fun append(event: LogEvent) {
            val logger = event.loggerName ?: return
            if (!logger.startsWith(MOD_LOGGERS) || logger.startsWith(TEST_LOGGERS)) return
            if (!event.level.isMoreSpecificThan(Level.INFO)) return
            lines += Line(event.level, logger, event.message.formattedMessage)
        }

        /** The context of the mod's own class loader, which is the one whose configuration the mod's loggers use. */
        fun attach() {
            val ctx = LogManager.getContext(CobblemonDynamics::class.java.classLoader, false) as LoggerContext
            start()
            ctx.configuration.rootLogger.addAppender(this, null, null)
            ctx.updateLoggers()
            context = ctx
        }

        fun detach() {
            val ctx = context ?: return
            context = null
            ctx.configuration.rootLogger.removeAppender(TAP_NAME)
            ctx.updateLoggers()
            stop()
        }
    }

    private companion object {
        val LOGGER = LogUtils.getLogger()

        const val TAP_NAME = "cobblemon_dynamics_perf_tap"
        const val MOD_LOGGERS = "codes.honor.cobblemondynamics."
        const val TEST_LOGGERS = "codes.honor.cobblemondynamics.test."

        const val WORKERS_PER_PASTURE = 8
        const val WARM_UP_TICKS = 200L
        const val MEASURED_TICKS = 6_000
        const val TIMEOUT_TICKS = 9_000
        const val PERCENTILE = 0.95
        const val MEAN_BUDGET_MS = 1.0
        const val P95_BUDGET_MS = 2.0

        /** A 3 × 3 grid with 16 blocks between pastures, less one corner. No furnace lies in two work areas of radius 8. */
        val PASTURES: List<BlockPos> = listOf(4, 20, 36).flatMap { z -> listOf(4, 20, 36).map { x -> BlockPos(x, 1, z) } }.dropLast(1)

        /** A ring of 8 furnaces 3 blocks out from each pasture, in the floor, so the workers walk over them. */
        val FURNACE_OFFSETS: List<BlockPos> = listOf(-3, 0, 3).flatMap { dz -> listOf(-3, 0, 3).map { dx -> BlockPos(dx, 0, dz) } }
            .filter { it != BlockPos.ZERO }
    }
}
