package codes.honor.cobblemondynamics.handler.farming

/** The pure rules of the Picker (R95). The handler reads every input from the level. */
object PickerRules {
    /**
     * CD-PCK-05: a Picker target is a block of the class `ApricornBlock` or of `#cobblemon_dynamics:picker_targets` ([inTag]) that has an
     * `age` property, which tells when its fruit is ripe.
     */
    fun isTarget(apricorn: Boolean, inTag: Boolean, hasAge: Boolean): Boolean = (apricorn || inTag) && hasAge

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
