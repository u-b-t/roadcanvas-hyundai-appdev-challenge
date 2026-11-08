package kr.roadcanvas.app.mydrawings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.ROOT)

/** 완료 일시를 기기 시간대 기준 `2026.11.08 14:05` 형식으로 보여준다. */
fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatScore(score: Int): String = "${score}점"

/** 1km 미만은 미터 단위 정수, 그 이상은 소수 첫째 자리까지 킬로미터로 보여준다. */
fun formatDistance(meters: Double): String {
    val rounded = meters.coerceAtLeast(0.0).roundToLong()
    return if (rounded < 1_000) {
        "$rounded m"
    } else {
        String.format(Locale.ROOT, "%.1f km", meters / 1_000)
    }
}

/** 소요 시간을 `1시간 2분`, `1분 35초`, `3초`처럼 큰 단위 두 개까지 보여준다. */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}시간 ${minutes}분"
        minutes > 0 -> "${minutes}분 ${seconds}초"
        else -> "${seconds}초"
    }
}
