package kr.roadcanvas.app.drawing

/**
 * 도전 모드. [key]는 저장소에 기록하는 값이므로 바꾸지 않는다.
 * [nameRequired]이면 완료 기록을 저장하기 전에 이름을 입력해야 한다.
 */
enum class DrawingMode(val key: String, val nameRequired: Boolean) {
    CIRCLE("circle", nameRequired = false);

    companion object {
        fun fromKey(key: String): DrawingMode = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("알 수 없는 도전 모드: $key")
    }
}
