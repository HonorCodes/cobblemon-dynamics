package codes.honor.cobblemondynamics.compat

/** Loads a class of an optional mod early, so that Mixin applies its hook before the role files load and read whether it did. */
object CompatClasses {
    /**
     * Loads [name] without initializing it, so no code of the mod runs here. A class that cannot load leaves its hook out, and the role
     * gate then turns its target off with one warning (CD-OPT-09).
     */
    fun load(name: String): Boolean = try {
        Class.forName(name, false, CompatClasses::class.java.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        false
    } catch (e: LinkageError) {
        false
    }
}
