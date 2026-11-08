package kr.roadcanvas.app.challenge

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.recording.distanceTo

/** 한 바퀴를 돈 뒤 출발점 근처로 보는 거리(m). 이 안에 들어오면 정차 후 종료하라고 안내한다. */
const val NEAR_START_METERS = 20.0

/** 진행 방향을 정할 때 쓰는 최소 이동 거리(m). 이보다 짧은 움직임은 GPS 흔들림으로 보고 방향을 세지 않는다. */
const val MIN_HEADING_SEGMENT_METERS = 3.0

/**
 * 한 바퀴를 돈 것으로 보는 회전 각도. 출발점 근처에 들어온 때는 출발점까지 남은 구간만큼 덜 돌았으므로
 * 한 바퀴(360도)보다 조금 작게 잡는다. 반지름 50m 원이면 출발점 20m 앞에서 약 337도다.
 */
const val LAP_TURN_DEGREES = 300.0

private const val FULL_TURN_DEGREES = 360.0

/**
 * 출발점 기준 진행 상황.
 * [distanceToStartMeters]는 현재 위치에서 출발점까지 거리이며 위치가 없으면 null이다.
 * [turnDegrees]는 진행 방향이 바뀐 각도의 합이다. 시계 방향으로 한 바퀴 돌면 360도, 반시계 방향이면 -360도이고
 * 반대 방향 회전은 서로 상쇄한다.
 * [headingDegrees]는 현재 진행 방향(북쪽 0도, 시계 방향)이며 충분히 움직이지 않았으면 null이다.
 */
data class LapProgress(
    val distanceToStartMeters: Double?,
    val turnDegrees: Double,
    val headingDegrees: Double?
) {
    /** 한 바퀴 기준 회전 진행률(%). 한 바퀴를 넘으면 100에서 멈춘다. */
    val turnPercent: Int
        get() = (abs(turnDegrees) / FULL_TURN_DEGREES * 100).coerceAtMost(100.0).roundToInt()

    /** 한 바퀴를 돌았는지. 출발점 근처의 남은 구간을 감안해 [LAP_TURN_DEGREES] 이상이면 돈 것으로 본다. */
    val completedLap: Boolean
        get() = abs(turnDegrees) >= LAP_TURN_DEGREES

    val nearStart: Boolean
        get() = distanceToStartMeters != null && distanceToStartMeters <= NEAR_START_METERS

    /** 한 바퀴 이상 돌고 출발점 근처로 돌아와 종료해도 되는 때 */
    val readyToFinish: Boolean
        get() = completedLap && nearStart

    companion object {
        val EMPTY = LapProgress(null, 0.0, null)
    }
}

/** 기록한 경로로 출발점까지 거리, 회전 각도와 현재 진행 방향을 계산한다. */
fun lapProgressOf(path: List<RoutePoint>): LapProgress {
    if (path.isEmpty()) return LapProgress.EMPTY
    // 최소 거리 이상 떨어진 점만 남겨 정차 중 흔들림이 방향 변화로 쌓이지 않게 한다.
    val moved = mutableListOf(path.first())
    path.drop(1).forEach {
        if (moved.last().distanceTo(it) >=
            MIN_HEADING_SEGMENT_METERS
        ) {
            moved += it
        }
    }
    val bearings = moved.zipWithNext { a, b -> a.bearingTo(b) }
    val turn = bearings.zipWithNext { a, b -> normalizeTurn(b - a) }.sum()
    return LapProgress(
        distanceToStartMeters = path.last().distanceTo(path.first()),
        turnDegrees = turn,
        headingDegrees = bearings.lastOrNull()
    )
}

/** 이 점에서 [other]로 향하는 방위각(북쪽 0도, 시계 방향, 0 이상 360 미만). 짧은 거리라 평면으로 근사한다. */
fun RoutePoint.bearingTo(other: RoutePoint): Double {
    val north = other.latitude - latitude
    val east = (other.longitude - longitude) * cos(Math.toRadians((latitude + other.latitude) / 2))
    val degrees = Math.toDegrees(atan2(east, north))
    return (degrees + FULL_TURN_DEGREES) % FULL_TURN_DEGREES
}

/** 방위각 차이를 -180도 초과 180도 이하로 바꾼다. */
private fun normalizeTurn(degrees: Double): Double {
    var turn = degrees % FULL_TURN_DEGREES
    if (turn > FULL_TURN_DEGREES / 2) turn -= FULL_TURN_DEGREES
    if (turn <= -FULL_TURN_DEGREES / 2) turn += FULL_TURN_DEGREES
    return turn
}
