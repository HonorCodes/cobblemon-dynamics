package codes.honor.cobblemondynamics.config

object ConfigRanges {
    val WORK_RADIUS = 2..32
    val SCAN_INTERVAL = 20..1_200
    val BLOCK_CHECKS = 64..4_096
    val TRAVEL_TIMEOUT = 100..6_000
    val RETRY_WAIT = 0..72_000
    val STANDBY_MOVE = 20..6_000
    val EFFECT_RADIUS = 16..128
    val TOGGLE_COOLDOWN = 0..200
    val PENDING_SELECTION = 100..6_000
    val TICKS_PER_LEVEL = 20..72_000
    val HEALER_MINUTES = 1..10_000
    val FLUSH_INTERVAL = 20..12_000
    val BED_FACTOR = 1..64
    val HEAL_COST = 0.01..1.0
    val LOW_STAMINA_THRESHOLD = 0.0..1.0
    val HEAD_BAR_RANGE = 4..64
}
