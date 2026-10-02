package ca.mattmccormick.screenbudget

data class UsageEvent(
    val type: Int,
    val packageName: String,
    val className: String,
    val timestampMs: Long,
)
