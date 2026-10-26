package com.nexusstream.app.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Timing for the daily bedtime reminder. Pure logic. */
object Bedtime {
    /** Milliseconds from [nowMs] until the next time the clock shows [minuteOfDay] (0..1439). */
    fun delayUntilMs(nowMs: Long, minuteOfDay: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val target = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var next = now.toLocalDate().atTime(target).atZone(zone)
        if (!next.isAfter(now)) next = LocalDate.from(now).plusDays(1).atTime(target).atZone(zone)
        return next.toInstant().toEpochMilli() - nowMs
    }

    fun label(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
