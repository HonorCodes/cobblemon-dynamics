package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.MOD_ID
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/**
 * The Cobbleworkers rest flag on a Pokémon's `persistentData`. Cobbleworkers 2.0.7 gives no job to a Pokémon whose top-level
 * `cobbleworkers.resting` is true (`RestManager.isResting`). Dynamics records the flags it set under `cobblemon_dynamics` →
 * `set_cw_rest` and clears only those (CD-CMP-02). Pure logic: the caller saves the Pokémon when a call returns true.
 */
object CobbleworkersRest {
    const val RESTING = "cobbleworkers.resting"
    const val ROOT = MOD_ID
    const val OWNED = "set_cw_rest"

    /** Sets the flag on a worker of a Work Mode pasture. Takes ownership only of a flag that was not set. True when [data] changed. */
    fun mark(data: CompoundTag): Boolean {
        if (data.getBoolean(RESTING)) return false
        data.putBoolean(RESTING, true)
        val root = data.getCompound(ROOT)
        root.putBoolean(OWNED, true)
        data.put(ROOT, root)
        return true
    }

    /** Clears the flag of a Pokémon that leaves Work Mode, only when Dynamics set it. True when [data] changed. */
    fun unmark(data: CompoundTag): Boolean {
        if (!data.contains(ROOT, Tag.TAG_COMPOUND.toInt())) return false
        val root = data.getCompound(ROOT)
        if (!root.contains(OWNED)) return false
        root.remove(OWNED)
        if (root.isEmpty) data.remove(ROOT)
        data.remove(RESTING)
        return true
    }
}
