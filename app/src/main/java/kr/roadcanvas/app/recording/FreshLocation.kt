package kr.roadcanvas.app.recording

import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.vehicle.LocationSample

/**
 * [sample]을 도전 경로에 기록할 점으로 바꾼다. 도전 시작 전에 측정한 위치면 null이다.
 *
 * 위치를 구독하면 마지막으로 알려진 위치나 GNSS가 늦게 잡히기 전의 위치가 먼저 올 수 있다. 이 점을 첫 점으로 쓰면
 * 실제 위치와 직선으로 이어져 점수가 깎이므로 버린다.
 * - 위치 시각이 [startedAtMillis] 이상이면 위치 시각 그대로 기록한다.
 * - 위치 시각이 [startedAtMillis]보다 이르면 버린다.
 * - 위치 시각이 없으면(0 이하) 받은 시각 [receivedAtMillis]를 위치 시각으로 보고 같은 규칙을 적용한다.
 *
 * 이어서 기록할 때도 원래 시작 시각을 기준으로 하므로 끊긴 동안의 위치는 다음 점과 직선으로 이어진다.
 */
fun recordablePoint(
    sample: LocationSample,
    startedAtMillis: Long,
    receivedAtMillis: Long
): RoutePoint? {
    val timeMillis = sample.timeMillis.takeIf { it > 0 } ?: receivedAtMillis
    if (timeMillis < startedAtMillis) return null
    return RoutePoint(sample.latitude, sample.longitude, timeMillis)
}
