package codes.honor.cobblemondynamics.handler.vanilla

object CampfirePotRule {
    const val DEFAULT_STEP = 2

    fun hasDemand(
        progress: Int,
        totalTime: Int,
        lidOpen: Int,
        cooking: Boolean,
        never: Boolean,
        canStartRecipe: Boolean = false,
    ): Boolean = !never &&
        lidOpen == 0 &&
        totalTime > 0 &&
        ((cooking && progress in 1 until totalTime) || (progress == 0 && canStartRecipe))

    fun nextProgress(progress: Int, totalTime: Int, step: Int = DEFAULT_STEP): Int {
        if (step <= 0 || totalTime <= step || progress <= 0 || progress >= totalTime) return progress
        val maxProgress = totalTime - step
        val aligned = progress - (progress % step)
        return minOf(aligned + step, maxProgress)
    }
}
