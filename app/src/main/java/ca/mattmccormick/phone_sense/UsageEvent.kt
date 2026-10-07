package ca.mattmccormick.phone_sense

data class UsageEvent(
    val type: Int,
    val packageName: String,
    val className: String,
    val timestampMs: Long,
)
