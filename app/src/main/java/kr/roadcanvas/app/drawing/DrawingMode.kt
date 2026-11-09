package kr.roadcanvas.app.drawing

/**
 * 도전 모드. [key]는 저장소에 기록하는 값이므로 바꾸지 않는다.
 * [scored]가 아닌 모드는 점수를 계산하지 않으며 완료 기록의 점수는 0으로 저장하고 보여주지 않는다.
 * [nameRequired]이면 완료 기록에 이름을 입력해야 한다.
 */
enum class DrawingMode(val key: String, val scored: Boolean, val nameRequired: Boolean) {
    CIRCLE("circle", scored = true, nameRequired = false),

    /** 점수 없이 주행 경로 자체를 그림으로 남긴다. 무엇을 그렸는지 이름을 붙여야 한다. */
    FREE_DRAWING("free_drawing", scored = false, nameRequired = true),

    /** 주행으로 둘러싼 영역을 차지한다. 점수 대신 면적을 판정한다(`kr.roadcanvas.app.score.TerritoryCalculator`). */
    TERRITORY("territory", scored = false, nameRequired = false);

    companion object {
        fun fromKey(key: String): DrawingMode = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("알 수 없는 도전 모드: $key")
    }
}
