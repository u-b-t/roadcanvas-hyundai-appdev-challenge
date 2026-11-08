package kr.roadcanvas.app.mydrawings

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MyDrawingsFormatTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `완료 일시는 기기 시간대의 날짜와 시각으로 표시한다`() {
        val millis = LocalDateTime.of(2026, 11, 8, 14, 5).atZone(seoul).toInstant().toEpochMilli()

        assertEquals("2026.11.08 14:05", formatDateTime(millis, seoul))
        assertEquals("2026.11.08 05:05", formatDateTime(millis, ZoneId.of("UTC")))
    }

    @Test
    fun `1km 미만 거리는 미터, 그 이상은 킬로미터로 표시한다`() {
        assertEquals("314 m", formatDistance(314.2))
        assertEquals("999 m", formatDistance(999.4))
        assertEquals("1.0 km", formatDistance(999.6))
        assertEquals("1.3 km", formatDistance(1_250.0))
        assertEquals("0 m", formatDistance(-3.0))
    }

    @Test
    fun `소요 시간은 큰 단위 두 개까지 표시한다`() {
        assertEquals("3초", formatDuration(3_900))
        assertEquals("1분 35초", formatDuration(95_000))
        assertEquals("2시간 5분", formatDuration((2 * 3_600 + 5 * 60 + 9) * 1_000L))
        assertEquals("0초", formatDuration(-1))
    }
}
