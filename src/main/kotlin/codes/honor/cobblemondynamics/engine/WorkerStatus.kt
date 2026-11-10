package codes.honor.cobblemondynamics.engine

/** A worker is in exactly one of these states on every tick (CD-ASG-18). */
enum class WorkerStatus { IDLE, TRAVELING, WORKING, STANDBY, TO_BED, SLEEPING, RESTING_IN_PLACE, HEALING }
