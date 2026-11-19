package codes.honor.cobblemondynamics.handler.farming

/** The pure rules of the Picker (R95). The handler reads every input from the level. */
object PickerRules {
    /**
     * CD-PCK-05: vanilla blocks whose largest `age` means no ripe fruit, which the Picker refuses also when a pack adds them to its tag.
     * A pick would cut, reset or burn them.
     */
    val REFUSED: Set<String> = setOf(
        "minecraft:bamboo",
        "minecraft:cactus",
        "minecraft:cave_vines",
        "minecraft:chorus_flower",
        "minecraft:fire",
        "minecraft:frosted_ice",
        "minecraft:kelp",
        "minecraft:sugar_cane",
        "minecraft:twisting_vines",
        "minecraft:weeping_vines",
    )

    /** What the load line of the Picker's tag says of one member (CD-PCK-05). */
    enum class TagMember { APRICORN, OTHER_FRUIT, NO_AGE, REFUSED }

    /**
     * CD-PCK-05: a Picker target is a block of the class `ApricornBlock`, or of `#cobblemon_dynamics:picker_targets` ([inTag]) and not
     * [refused], with no `half` property ([twoBlock]), so a two-block crop is never picked half by half, and with an `age` property,
     * which tells when its fruit is ripe. The cheap checks come first, and each runs only when the ones before it pass.
     */
    fun isTarget(apricorn: Boolean, inTag: () -> Boolean, refused: () -> Boolean, twoBlock: () -> Boolean, hasAge: () -> Boolean): Boolean =
        (apricorn || inTag()) && (apricorn || !refused()) && !twoBlock() && hasAge()

    /** CD-PCK-05: the kind of a member of the tag for the load line, by the rule of [isTarget]. */
    fun memberOf(apricorn: Boolean, refused: Boolean, twoBlock: Boolean, hasAge: Boolean): TagMember = when {
        (refused && !apricorn) || twoBlock -> TagMember.REFUSED
        !hasAge -> TagMember.NO_AGE
        apricorn -> TagMember.APRICORN
        else -> TagMember.OTHER_FRUIT
    }

    /** CD-PCK-05: the fruit is ripe at the largest value of the block's `age`, as an apricorn at age 3. */
    fun isRipe(age: Int?, maxAge: Int?): Boolean = age != null && maxAge != null && age >= maxAge

    /** CD-PCK-09: a pick needs both land claim answers for its cell. [mayBreak] runs only when [mayChange] allows the pick. */
    fun mayPick(mayChange: () -> Boolean, mayBreak: () -> Boolean): Boolean = mayChange() && mayBreak()

    /** What the area of a pick reads of one cell (CD-PCK-08). Each value is read only when the ones before it in [eligible] pass. */
    interface AreaCell {
        val inArea: Boolean
        val loaded: Boolean
        val stalled: Boolean
        val worked: Boolean
        val denied: Boolean
        val ripe: Boolean
        val laneFree: Boolean
    }

    /**
     * CD-PCK-08: a cell of the area is picked when it lies in the work area and is loaded, the worker has not stalled it after a refusal,
     * no area of a Picker worked it in this tick, its block did not fail in an area, it holds a ripe Picker target, and no other worker
     * holds it. The cheap checks come first, and no block is read in a chunk that is not loaded.
     */
    fun eligible(cell: AreaCell): Boolean =
        cell.inArea && cell.loaded && !cell.stalled && !cell.worked && !cell.denied && cell.ripe && cell.laneFree
}
