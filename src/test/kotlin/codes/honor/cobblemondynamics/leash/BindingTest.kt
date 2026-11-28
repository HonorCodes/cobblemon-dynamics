package codes.honor.cobblemondynamics.leash

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class BindingTest {
    private fun roundTrip(binding: Binding): Binding =
        Binding.CODEC.parse(NbtOps.INSTANCE, Binding.CODEC.encodeStart(NbtOps.INSTANCE, binding).getOrThrow()).getOrThrow()

    @Test
    fun `CD-LSH-07 a single-target binding survives the codec that saves it`() {
        val binding = Binding(
            roleId = ResourceLocation.parse("cobblemon_dynamics:stoker"),
            dimension = ResourceLocation.parse("minecraft:overworld"),
            pasture = BlockPos(10, 64, -3),
            target = BlockPos(14, 64, -3),
            face = Direction.NORTH,
            binder = UUID.randomUUID(),
        )
        assertEquals(binding, roundTrip(binding))
    }

    @Test
    fun `CD-LSH-18 a source-and-destination binding keeps both targets and both faces`() {
        val binding = Binding(
            roleId = ResourceLocation.parse("cobblemon_dynamics:telekinesis"),
            dimension = ResourceLocation.parse("minecraft:the_nether"),
            pasture = BlockPos(0, 70, 0),
            target = BlockPos(2, 70, 0),
            face = Direction.UP,
            binder = UUID.randomUUID(),
            destination = BlockPos(-2, 70, 0),
            destinationFace = Direction.NORTH,
        )
        assertEquals(binding, roundTrip(binding))
    }

    @Test
    fun `CD-TEAM-05 owner round-trips through CODEC and legacy NBT without owner decodes as null`() {
        val withOwner = Binding(
            roleId = ResourceLocation.parse("cobblemon_dynamics:stoker"),
            dimension = ResourceLocation.parse("minecraft:overworld"),
            pasture = BlockPos(10, 64, -3),
            target = BlockPos(14, 64, -3),
            face = Direction.NORTH,
            binder = UUID.randomUUID(),
            owner = UUID.randomUUID(),
        )
        assertEquals(withOwner, roundTrip(withOwner))

        val legacyTag = (Binding.CODEC.encodeStart(NbtOps.INSTANCE, withOwner).getOrThrow() as CompoundTag).copy()
        legacyTag.remove("owner")
        val decoded = Binding.CODEC.parse(NbtOps.INSTANCE, legacyTag).getOrThrow()
        assertNull(decoded.owner)
        assertEquals(withOwner.binder, decoded.binder)
    }
}
