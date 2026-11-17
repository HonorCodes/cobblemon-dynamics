package codes.honor.cobblemondynamics

import net.minecraft.resources.ResourceLocation

/** The INFO lines at load, which a pack maintainer reads in the server log (CD-CMP-08 to CD-CMP-10, R65). */
object LoadLines {
    private const val OTHER_CONDITIONS = "conditions"

    enum class MixinStatus { APPLIED, SKIPPED, NO_INJECTION, NOT_APPLIED_YET }

    data class MixinEntry(val name: String, val status: MixinStatus, val reason: String? = null)

    /** CD-CMP-08: each mixin of the config for this side, under its heading, in name order. */
    fun mixins(entries: List<MixinEntry>): String {
        fun part(status: MixinStatus, heading: String): String {
            val matching = entries.filter { it.status == status }.sortedBy { it.name }
            val names = matching.map { entry -> if (entry.reason == null) entry.name else "${entry.name} (${entry.reason})" }
            return "${names.size} $heading [${names.joinToString(", ")}]"
        }
        val parts = listOf(
            part(MixinStatus.APPLIED, "applied"),
            part(MixinStatus.SKIPPED, "skipped"),
            part(MixinStatus.NO_INJECTION, "applied with no injection"),
            part(MixinStatus.NOT_APPLIED_YET, "not applied yet"),
        )
        return "Mixins: " + parts.joinToString(", ")
    }

    /**
     * The status of each mixin that Mixin offered the plugin. An accessor mixin declares no handler, so it counts as applied once Mixin
     * merged it. A mixin with a handler counts as applied only when a target calls one of its handlers, as `MixinState.applied` reads.
     */
    fun mixinEntries(
        offered: Set<String>,
        skipped: Map<String, String>,
        merged: Set<String>,
        withHandlers: Set<String>,
        applied: (String) -> Boolean,
    ): List<MixinEntry> = offered.sorted().map { name ->
        val reason = skipped[name]
        when {
            reason != null -> MixinEntry(name, MixinStatus.SKIPPED, reason)
            name !in merged -> MixinEntry(name, MixinStatus.NOT_APPLIED_YET)
            name in withHandlers && !applied(name) -> MixinEntry(name, MixinStatus.NO_INJECTION)
            else -> MixinEntry(name, MixinStatus.APPLIED)
        }
    }

    /** CD-CMP-09: the payload version and the id of each payload, by direction, in the order of registration. */
    fun payloads(version: String, toClient: List<ResourceLocation>, toServer: List<ResourceLocation>): String =
        "Payloads, version $version: ${toClient.size} to the client [${toClient.joinToString(", ")}], " +
            "${toServer.size} to the server [${toServer.joinToString(", ")}]"

    /**
     * CD-CRP-05: one farming role's tagged blocks, counted by the crop adapter that reads each one, with the adapters in name order,
     * and the ids of the tagged blocks that no adapter reads.
     */
    fun farming(role: String, byAdapter: Map<String, Int>, unadapted: List<ResourceLocation>): String {
        val total = byAdapter.values.sum() + unadapted.size
        val counts = byAdapter.entries.sortedBy { it.key }.joinToString(", ") { (adapter, count) -> "$adapter $count" }
        return "Farming: $role $total blocks [$counts], no adapter [${unadapted.sortedBy { it.toString() }.joinToString(", ")}]"
    }

    /**
     * CD-OPT-04: each role file that its conditions skipped, in id order, with the absent mods that it needs, or `conditions` for a
     * file that another condition skipped.
     */
    fun skippedRoles(skipped: Map<ResourceLocation, List<String>>): String {
        val names = skipped.entries.sortedBy { it.key.toString() }.joinToString(", ") { (id, mods) ->
            "$id (${if (mods.isEmpty()) OTHER_CONDITIONS else mods.joinToString(", ")})"
        }
        return "Roles not loaded, their mods are absent: ${skipped.size} [$names]"
    }

    /** One adapter of an optional mod, with what it misses (`block`, `mixin` or `class`), or nothing when it was found. */
    data class Adapter(val name: String, val missing: List<String> = emptyList())

    /** CD-OPT-11: one loaded optional mod with its version, and each of its adapters as found or missing, in name order. */
    fun compat(modId: String, version: String, adapters: List<Adapter>): String {
        val names = adapters.sortedBy { it.name }.joinToString(", ") { adapter ->
            if (adapter.missing.isEmpty()) "${adapter.name} found" else "${adapter.name} missing (${adapter.missing.joinToString(", ")})"
        }
        return "Compat: $modId $version [$names]"
    }

    /** CD-CMP-10: the number of roles loaded and active, and each disabled role with its reason, in id order. */
    fun roles(loaded: Int, active: Int, disabled: Map<ResourceLocation, String>): String {
        val names = disabled.entries.sortedBy { it.key.toString() }.joinToString(", ") { (id, reason) -> "$id ($reason)" }
        return "Roles: $loaded loaded, $active active, ${disabled.size} disabled [$names]"
    }
}
