package kr.roadcanvas.app.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.score.CircleScoreCalculator
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteConfig
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteGenerator
import kr.roadcanvas.app.vehicle.simulated.SimulatedRouteType

private const val TAG = "SeedDrawings"
private const val HOUR_MILLIS = 3_600_000L

/**
 * 내 그림 화면을 기기에서 확인하기 위해 모의 경로로 만든 완료 기록을 저장하는 디버그 전용 화면.
 * `adb shell am start -n kr.roadcanvas.app/.debug.SeedDrawingsActivity`로 실행하면 기록을 추가하고 바로 닫힌다.
 * 기존 기록은 지우지 않는다. 지우려면 앱 데이터를 삭제한다.
 */
class SeedDrawingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = appContainer.completedDrawingRepository
        val now = System.currentTimeMillis()
        val routes = listOf(SimulatedRouteType.CIRCLE, SimulatedRouteType.SQUISHED_CIRCLE)
        routes.forEachIndexed { index, type ->
            val drawing = sampleDrawing(type, completedAtMillis = now - index * HOUR_MILLIS)
            // 디버그 전용 일회성 작업이라 두 건 저장을 메인 스레드에서 기다린다.
            when (val result = runBlocking { repository.save(drawing) }) {
                is StorageResult.Success -> Log.i(TAG, "$type 기록 저장: ${result.value}")
                is StorageResult.Failure -> Log.w(TAG, "$type 기록 저장 실패", result.cause)
            }
        }
        finish()
    }

    private fun sampleDrawing(type: SimulatedRouteType, completedAtMillis: Long): CompletedDrawing {
        val route = SimulatedRouteGenerator.generate(type)
        val interval = SimulatedRouteConfig().intervalMillis
        val startedAt = completedAtMillis - route.size * interval
        val score = CircleScoreCalculator().calculate(
            route.map { GeoPoint(it.latitude, it.longitude) }
        )
        return CompletedDrawing(
            mode = DrawingMode.CIRCLE,
            completedAtMillis = completedAtMillis,
            path = route.mapIndexed { index, point ->
                RoutePoint(point.latitude, point.longitude, startedAt + index * interval)
            },
            score = score.score,
            metrics = score.metrics?.let {
                mapOf("circularity" to it.circularity, "closure" to it.closure)
            }.orEmpty(),
            distanceMeters = score.metrics?.pathLengthMeters ?: 0.0,
            durationMillis = route.size * interval
        )
    }
}
