package codes.honor.cobblemondynamics.leash

import java.util.UUID

/** Who may flip a driven target with the Work Leash: its worker's binder, the placer of the worker's pasture, or an operator (CD-WHL-05, CD-SEC-10). */
object FlipRules {
    fun mayFlip(actor: UUID, isOperator: Boolean, binder: UUID?, placer: UUID?): Boolean = isOperator || actor == binder || actor == placer
}
