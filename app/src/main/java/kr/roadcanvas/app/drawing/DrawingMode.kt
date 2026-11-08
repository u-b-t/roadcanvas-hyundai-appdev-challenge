package kr.roadcanvas.app.drawing

/** 도전 모드. [key]는 저장소에 기록하는 값이므로 바꾸지 않는다. */
enum class DrawingMode(val key: String) {
    CIRCLE("circle");

    companion object {
        fun fromKey(key: String): DrawingMode = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("알 수 없는 도전 모드: $key")
    }
}
