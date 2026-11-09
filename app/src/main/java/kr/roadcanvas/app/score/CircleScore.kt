package kr.roadcanvas.app.score

/** 시각 순서대로 기록된 위치값. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 동그라미 점수 계산 결과. 0점 사유가 있으면 [metrics]는 null이다.
 * [closureDistanceMeters]는 [ZeroScoreReason.NOT_CLOSED]일 때 출발점까지 거리(m),
 * [rotationDegrees]는 [ZeroScoreReason.INCOMPLETE_TURN]일 때 돈 각도(도)이며 그 밖에는 null이다.
 */
data class CircleScoreResult(
    val score: Int,
    val metrics: CircleScoreMetrics?,
    val zeroReason: ZeroScoreReason?,
    val closureDistanceMeters: Double? = null,
    val rotationDegrees: Double? = null
)

/** 점수를 구성하는 세부 지표. 비율 지표는 0~1 범위다. [center]와 [radiusMeters]는 점수 계산에 쓴 맞춤 원이다. */
data class CircleScoreMetrics(
    val circularity: Double,
    val closure: Double,
    val rotationCompleteness: Double,
    val radiusMeters: Double,
    val radialRmsRatio: Double,
    val closureDistanceMeters: Double,
    val rotationRadians: Double,
    val pathLengthMeters: Double,
    val center: GeoPoint
)

enum class ZeroScoreReason {
    /** 유효한 위치값이 최소 개수보다 적다. */
    INSUFFICIENT_POINTS,

    /** 총 이동 거리가 기준보다 짧다. */
    NO_MOVEMENT,

    /** 경로가 직선 위에 있어 원을 맞출 수 없다. */
    NOT_CIRCULAR,

    /** 맞춘 원의 지름이 최소 크기보다 작다. */
    TOO_SMALL,

    /** 끝점이 출발점에서 닫힘 허용 거리보다 멀어 원이 닫히지 않았다. */
    NOT_CLOSED,

    /** 맞춘 원 중심을 한 바퀴 다 돌지 않았다. */
    INCOMPLETE_TURN;

    /** 닫힌 원을 그리지 않아 점수를 인정하지 않는 사유인지. 이 사유의 기록은 저장하지 않는다. */
    val notClosedCircle: Boolean get() = this == NOT_CLOSED || this == INCOMPLETE_TURN
}
