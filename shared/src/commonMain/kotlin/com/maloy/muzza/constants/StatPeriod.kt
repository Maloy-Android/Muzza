package com.maloy.muzza.constants

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

enum class StatPeriod {
    `1_WEEK`, `1_MONTH`, `3_MONTH`, `6_MONTH`, `1_YEAR`, ALL;

    fun toTimeMillis(): Long {
        val utc = TimeZone.UTC
        val now = Clock.System.now().toLocalDateTime(utc).date
        return when (this) {
            `1_WEEK` -> now.minus(1, DateTimeUnit.WEEK).atStartOfDayIn(utc).toEpochMilliseconds()
            `1_MONTH` -> now.minus(1, DateTimeUnit.MONTH).atStartOfDayIn(utc).toEpochMilliseconds()
            `3_MONTH` -> now.minus(3, DateTimeUnit.MONTH).atStartOfDayIn(utc).toEpochMilliseconds()
            `6_MONTH` -> now.minus(6, DateTimeUnit.MONTH).atStartOfDayIn(utc).toEpochMilliseconds()
            `1_YEAR` -> now.minus(12, DateTimeUnit.MONTH).atStartOfDayIn(utc).toEpochMilliseconds()
            ALL -> 0
        }
    }
}