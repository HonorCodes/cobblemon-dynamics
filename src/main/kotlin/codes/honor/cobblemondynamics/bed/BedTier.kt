package codes.honor.cobblemondynamics.bed

import net.minecraft.util.StringRepresentable

/** The four beds (CD-BED-01). [tier] picks the bed's recovery factor from the server config (CD-BED-03). */
enum class BedTier(val tier: Int) : StringRepresentable {
    HAY(1),
    WOOL(2),
    PLUSH(3),
    STARLIT(4),
    ;

    /** The block and item id path. */
    val id: String get() = "${name.lowercase()}_bed"

    override fun getSerializedName(): String = name.lowercase()

    companion object {
        val CODEC: StringRepresentable.EnumCodec<BedTier> = StringRepresentable.fromEnum(BedTier::values)

        fun of(tier: Int): BedTier? = entries.firstOrNull { it.tier == tier }
    }
}
