package codes.honor.cobblemondynamics.compat.botanypots

import codes.honor.cobblemondynamics.OnceLogger
import net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * The one class of the pot roles that names Botany Pots' classes. Botany Pots has no API for a pot's growth or harvest, so these calls
 * name its implementation classes of 21.1.44 (ADR-0020). A later Botany Pots that changes them breaks this adapter only: the engine's
 * guard then turns off the pot handlers that called it, with one warning each, and the farming roles keep working.
 */
object PotAdapter {
    /** The one block entity type of every Botany Pots pot, the 36 brick pots outside `#botanypots:botany_pots` too. */
    private val POT: ResourceLocation = ResourceLocation.fromNamespaceAndPath("botanypots", "botany_pot")

    private val warnings = OnceLogger()

    /** Whether [blockEntity] is a Botany Pot, by its type's id, with no class of Botany Pots. */
    fun isPot(blockEntity: BlockEntity): Boolean = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.type) == POT

    fun facts(blockEntity: BlockEntity): PotFacts? {
        val pot = blockEntity as? BotanyPotBlockEntity ?: return null
        return PotFacts(pot.potType.name, pot.canBonemeal(), pot.canHarvest(), pot.growthTime(), pot.requiredGrowthTicks)
    }

    /** CD-FRM-04: the growth of one use and the bone meal cooldown after it, as Botany Pots' own bone meal sets them. */
    fun grow(blockEntity: BlockEntity, growth: Float, cooldown: Int) {
        val pot = blockEntity as? BotanyPotBlockEntity ?: return
        pot.updateGrowthTime(growth)
        pot.setBonemealCooldown(cooldown)
    }

    /**
     * CD-FRM-05: a player's harvest of a basic pot. It rolls the crop's harvest once, resets the growth, and drops the harvest out of
     * [face] of the pot. The seed and the soil stay in the pot. A crop of a data pack can throw while it rolls, so a throw leaves the pot
     * unharvested and returns false, with one warning per seed, instead of turning off the handler on the whole server.
     */
    fun harvest(level: ServerLevel, blockEntity: BlockEntity, face: Direction): Boolean {
        val pot = blockEntity as? BotanyPotBlockEntity ?: return false
        val crop = pot.orInvalidateCrop ?: return false
        val drops = ArrayList<ItemStack>()
        try {
            crop.onHarvest(pot.recipeContext, level) { drops.add(it) }
        } catch (e: RuntimeException) {
            val seed = BuiltInRegistries.ITEM.getKey(pot.seedItem.item)
            warnings.warn(seed) { "The Botany Pots crop of $seed threw on a harvest, so the Harvester leaves its pots: $e" }
            return false
        }
        pot.growthTime.reset()
        pot.markUpdated()
        for (stack in drops) if (!stack.isEmpty) Block.popResourceFromFace(level, pot.blockPos, face, stack)
        return true
    }
}
