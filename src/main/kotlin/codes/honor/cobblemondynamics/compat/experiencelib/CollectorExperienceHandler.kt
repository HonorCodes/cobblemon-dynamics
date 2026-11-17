package codes.honor.cobblemondynamics.compat.experiencelib

import codes.honor.cobblemondynamics.xp.CollectorStore
import es.degrassi.experiencelib.api.capability.IExperienceHandler
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

/**
 * CD-XPC-11: experiencelib's XP capability on the outputs of a collector. It has one tank and only drains: an extract takes up to the
 * stored XP and honours its simulate flag, every insert takes 0, and `setExperience` and `setCapacity` change nothing. Applied
 * Experienced's import bus reads [getExperience] and calls [extractExperience] (RES13). A real extract calls [changed]. While [live]
 * is false, the collector is gone and its drop carries the XP, so the handler holds and gives nothing.
 */
class CollectorExperienceHandler(
    private val store: CollectorStore,
    private val live: () -> Boolean = { true },
    private val changed: () -> Unit,
) : IExperienceHandler {
    override fun getTanks(): Int = 1

    override fun canAcceptExperience(tank: Int, amount: Long): Boolean = false

    override fun canProvideExperience(tank: Int, amount: Long): Boolean = extractExperience(tank, amount, true) > 0

    override fun getMaxExtract(tank: Int): Long = if (tank == 0) getExperience() else 0

    override fun getMaxReceive(tank: Int): Long = 0

    override fun setExperience(tank: Int, experience: Long) {}

    override fun setCapacity(tank: Int, capacity: Long) {}

    override fun receiveExperience(tank: Int, amount: Long, simulate: Boolean): Long = 0

    override fun extractExperience(tank: Int, amount: Long, simulate: Boolean): Long {
        if (tank != 0 || amount <= 0 || !live()) return 0
        val given = store.extract(amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), simulate)
        if (!simulate && given > 0) changed()
        return given.toLong()
    }

    override fun receiveExperienceRecipe(tank: Int, amount: Long, simulate: Boolean): Long = 0

    /** experiencelib's recipe extract skips the tank's transfer limit. This tank has none, so it is the plain extract. */
    override fun extractExperienceRecipe(tank: Int, amount: Long, simulate: Boolean): Long = extractExperience(tank, amount, simulate)

    override fun getExperience(): Long = if (live()) store.stored.toLong() else 0

    /** A collector above a lowered capacity keeps its XP, so it reports at least what it holds (CD-XPC-03). */
    override fun getExperienceCapacity(): Long = maxOf(store.capacity(), store.stored).toLong()

    /** The block entity saves the XP, so this view saves nothing. */
    override fun serializeNBT(provider: HolderLookup.Provider): CompoundTag = CompoundTag()

    override fun deserializeNBT(provider: HolderLookup.Provider, nbt: CompoundTag) {}
}
