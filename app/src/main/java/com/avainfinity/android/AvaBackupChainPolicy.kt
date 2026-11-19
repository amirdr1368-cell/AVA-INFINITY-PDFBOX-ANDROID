package com.avainfinity.android

object AvaBackupChainPolicy {
    const val MAX_BASE_AGE_MILLIS = 24L * 60L * 60L * 1000L
    const val MAX_EVENT_DELTA_COUNT = 100
    const val MAX_EVENT_DELTA_RATIO_PERCENT = 25L

    fun shouldCreateFull(
        baseAgeMillis: Long?,
        eventDeltaCount: Int,
        eventDeltaBytes: Long,
        baselineBytes: Long
    ): Boolean {
        if (baseAgeMillis == null || baseAgeMillis < 0L || baseAgeMillis >= MAX_BASE_AGE_MILLIS) return true
        if (eventDeltaCount >= MAX_EVENT_DELTA_COUNT) return true
        if (baselineBytes <= 0L || eventDeltaBytes <= 0L) return false
        val thresholdBytes =
            (baselineBytes / 100L) * MAX_EVENT_DELTA_RATIO_PERCENT +
                ((baselineBytes % 100L) * MAX_EVENT_DELTA_RATIO_PERCENT + 99L) / 100L
        return eventDeltaBytes >= thresholdBytes.coerceAtLeast(1L)
    }

    fun isEmptyDelta(changesCount: Int, removedKeysCount: Int): Boolean =
        changesCount == 0 && removedKeysCount == 0
}
