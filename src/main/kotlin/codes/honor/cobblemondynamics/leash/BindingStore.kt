package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.MOD_ID
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag

/**
 * A Pokémon's binding, in `Pokemon.persistentData` under `cobblemon_dynamics` → `binding`, so it is saved with the Pokémon in the
 * PC and the party (CD-LSH-07). Write to the store's own instance: a pastured entity shares it, and `onChange` saves it.
 */
object BindingStore {
    private const val KEY = "binding"

    fun read(pokemon: Pokemon): Binding? {
        val root = pokemon.persistentData.getCompound(MOD_ID)
        if (!root.contains(KEY, Tag.TAG_COMPOUND.toInt())) return null
        return Binding.CODEC.parse(NbtOps.INSTANCE, root.get(KEY)).result().orElse(null)
    }

    fun write(pokemon: Pokemon, binding: Binding) {
        val root = pokemon.persistentData.getCompound(MOD_ID)
        root.put(KEY, Binding.CODEC.encodeStart(NbtOps.INSTANCE, binding).getOrThrow())
        pokemon.persistentData.put(MOD_ID, root)
        pokemon.onChange()
    }

    fun clear(pokemon: Pokemon) {
        val data = pokemon.persistentData
        if (!data.contains(MOD_ID, Tag.TAG_COMPOUND.toInt())) return
        val root: CompoundTag = data.getCompound(MOD_ID)
        if (!root.contains(KEY)) return
        root.remove(KEY)
        if (root.isEmpty) data.remove(MOD_ID)
        pokemon.onChange()
    }
}
