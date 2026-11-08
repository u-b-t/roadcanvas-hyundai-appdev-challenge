package kr.roadcanvas.app.vehicle.pleos

/**
 * FusedLocation SDK 오류 판별. SDK v1.1.3은 서비스 오류를 메시지만 담은 [Exception]으로 전달하고
 * 위치 접근 차단을 따로 알리는 API가 없다. 차단 오류 메시지는 확인되지 않아 메시지에 차단·개인정보
 * 보호를 뜻하는 단어가 있으면 차단으로 본다. 실제 메시지를 확인하면 이 판별을 좁힌다.
 */
object FusedLocationErrors {
    private val ACCESS_BLOCKED_KEYWORDS = listOf("block", "privacy")

    fun isAccessBlocked(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: return false
        return ACCESS_BLOCKED_KEYWORDS.any { it in message }
    }
}
