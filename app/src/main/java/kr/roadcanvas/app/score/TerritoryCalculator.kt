package kr.roadcanvas.app.score

import kotlin.math.abs

/** 닫힌 경로로 보는 시작점과 끝점의 최대 거리(m). 잠정값이며 사용자가 확정하면 바꾼다. */
const val TERRITORY_CLOSURE_TOLERANCE_METERS = 50.0

/** 차지할 수 있는 최소 면적(m²). 잠정값이며 사용자가 확정하면 바꾼다. */
const val TERRITORY_MIN_AREA_SQUARE_METERS = 5_000.0

/** 판정에 필요한 최소 유효 위치값 수. 동그라미 점수와 같은 기준이다. */
const val TERRITORY_MIN_VALID_POINTS = 10

/** 이 거리보다 가까운 연속 위치값은 같은 지점으로 본다(m). 정차 중 잡음을 줄인다. */
const val TERRITORY_DUPLICATE_POINT_METERS = 0.5

/** 땅따먹기 판정 상수. 기본값은 위 상수다. */
data class TerritoryConfig(
    val closureToleranceMeters: Double = TERRITORY_CLOSURE_TOLERANCE_METERS,
    val minAreaSquareMeters: Double = TERRITORY_MIN_AREA_SQUARE_METERS,
    val minValidPoints: Int = TERRITORY_MIN_VALID_POINTS,
    val duplicatePointMeters: Double = TERRITORY_DUPLICATE_POINT_METERS
)

/**
 * 기록한 위도·경도 경로가 둘러싼 땅따먹기 영역을 판정한다.
 *
 * 판정 순서와 규칙:
 * 1. 위도·경도 범위를 벗어난 값을 버리고 [TerritoryConfig.minValidPoints]보다 적으면 [TerritoryResult.InsufficientPoints]다.
 *    [TerritoryConfig.duplicatePointMeters]보다 가까운 연속 점을 하나로 합친 뒤 서로 다른 지점이 셋 미만이어도 같다.
 * 2. 시작점과 끝점이 [TerritoryConfig.closureToleranceMeters]보다 멀면 [TerritoryResult.NotClosed]다.
 *    경로 중간에 고리가 있어도 끝점이 돌아오지 않으면 닫히지 않은 것으로 본다.
 * 3. 닫힌 경로는 끝점에서 시작점으로 곧게 이은 뒤 출발점 기준 지역 평면 좌표(m)로 바꾼다.
 * 4. 경로를 따라가다 앞서 지나간 구간과 교차하면 교차점에서, 앞서 지난 꼭짓점에
 *    [TerritoryConfig.duplicatePointMeters] 이내로 다시 오면 그 꼭짓점에서 고리를 잘라낸다.
 *    그 결과 경로는 자기 교차가 없는 고리 여러 개로 나뉜다.
 * 5. 면적이 가장 큰 고리 하나를 영역으로 한다. 나머지 고리는 버린다. 그래서
 *    - 8자처럼 자기 교차하면 큰 쪽 고리만 차지한다.
 *    - 같은 길을 여러 바퀴 돌아도 한 바퀴 영역만 차지하고 면적을 겹쳐 세지 않는다.
 *    - 회전하며 생긴 작은 고리나 위치 잡음 고리는 영역에 넣지 않는다.
 *    - 같은 구간을 갔다가 돌아온 곁가지는 면적이 없으므로 영역을 넓히지 않는다.
 *      돌아오는 점이 갈 때의 점과 떨어진 채 구간만 평행하게 겹치면 교차로 보지 않아 경계에 폭 없는 곁가지로 남을 수 있다.
 * 6. 영역 면적이 [TerritoryConfig.minAreaSquareMeters]보다 작으면 [TerritoryResult.TooSmall], 아니면 [TerritoryResult.Claimed]다.
 *
 * 교차는 두 구간이 서로의 끝점이 아닌 내부에서 만날 때만 센다. 구간이 이전 꼭짓점을 스치거나 평행하게 겹치는 경우는 교차로 보지 않는다.
 */
class TerritoryCalculator(val config: TerritoryConfig = TerritoryConfig()) {

    fun calculate(points: List<GeoPoint>): TerritoryResult {
        val valid = points.filter { it.isValid() }
        if (valid.size < config.minValidPoints) return TerritoryResult.InsufficientPoints
        val origin = valid.first()
        val local = dropDuplicates(toLocalMeters(valid, origin))
        if (local.size < MIN_POLYGON_POINTS) return TerritoryResult.InsufficientPoints

        val gap = local.first().distanceTo(local.last())
        if (gap > config.closureToleranceMeters) {
            return TerritoryResult.NotClosed(gap, config.closureToleranceMeters)
        }

        val territory = splitIntoLoops(local).maxByOrNull { abs(signedArea(it)) }
        val area = territory?.let { abs(signedArea(it)) } ?: 0.0
        if (territory == null || area < config.minAreaSquareMeters) {
            return TerritoryResult.TooSmall(area, config.minAreaSquareMeters)
        }
        return TerritoryResult.Claimed(
            areaSquareMeters = area,
            boundary = territory.map { toGeoPoint(it, origin) },
            perimeterMeters = (territory + territory.first()).zipWithNext { a, b ->
                a.distanceTo(b)
            }.sum()
        )
    }

    private fun dropDuplicates(points: List<LocalPoint>): List<LocalPoint> {
        val result = mutableListOf(points.first())
        for (point in points.drop(1)) {
            if (point.distanceTo(result.last()) >= config.duplicatePointMeters) result += point
        }
        // 끝점이 시작점과 같으면 닫는 변이 길이 0이 되므로 뺀다.
        if (result.size > 1 &&
            result.last().distanceTo(result.first()) < config.duplicatePointMeters
        ) {
            result.removeAt(result.lastIndex)
        }
        return result
    }

    /**
     * 끝점에서 시작점으로 닫은 경로를 자기 교차가 없는 고리들로 나눈다. 각 고리는 첫 점을 반복하지 않는다.
     * 새 구간이 앞선 구간들과 교차하면 새 구간 위에서 가장 먼저 만나는 교차점에서 그 사이를 고리로 떼어 낸다.
     * 새 점이 앞서 지난 꼭짓점과 [TerritoryConfig.duplicatePointMeters] 이내이면 그 꼭짓점에서 떼어 낸다.
     * 시작점으로 돌아오는 마지막 점도 이 규칙으로 남은 경로를 고리로 만든다.
     */
    private fun splitIntoLoops(points: List<LocalPoint>): List<List<LocalPoint>> {
        val loops = mutableListOf<List<LocalPoint>>()
        val current = mutableListOf(points.first())
        fun cutAfter(index: Int, loop: List<LocalPoint>) {
            loops += loop
            while (current.size > index + 1) current.removeAt(current.lastIndex)
        }
        for (next in points.drop(1) + points.first()) {
            while (true) {
                val from = current.last()
                // 마지막 구간은 새 구간과 끝점을 공유하므로 제외한다.
                val crossing = (0 until current.size - 2)
                    .mapNotNull { index ->
                        intersection(from, next, current[index], current[index + 1])
                            ?.let { (t, point) -> Triple(index, t, point) }
                    }
                    .minByOrNull { it.second }
                    ?: break
                val (index, _, point) = crossing
                cutAfter(index, listOf(point) + current.subList(index + 1, current.size))
                current += point
            }
            // 바로 앞 꼭짓점 둘로 돌아오면 점 둘짜리 고리가 되므로 보지 않는다. 되돌아 나오는 급회전에서 경로가 끊기지 않게 한다.
            val revisited = (0 until current.size - 2).lastOrNull {
                current[it].distanceTo(next) < config.duplicatePointMeters
            }
            if (revisited == null) {
                current += next
            } else {
                cutAfter(revisited, current.subList(revisited, current.size).toList())
            }
        }
        return loops.filter { it.size >= MIN_POLYGON_POINTS }
    }

    /**
     * 구간 a1→a2와 b1→b2가 두 구간 모두의 내부에서 만나면 a1→a2 위의 비율 t와 교차점을 준다.
     * 평행하거나 끝점에서 만나면 null이다.
     */
    private fun intersection(
        a1: LocalPoint,
        a2: LocalPoint,
        b1: LocalPoint,
        b2: LocalPoint
    ): Pair<Double, LocalPoint>? {
        val rx = a2.x - a1.x
        val ry = a2.y - a1.y
        val sx = b2.x - b1.x
        val sy = b2.y - b1.y
        val denominator = rx * sy - ry * sx
        if (abs(denominator) <
            PARALLEL_EPSILON * (rx * rx + ry * ry + sx * sx + sy * sy)
        ) {
            return null
        }
        val qx = b1.x - a1.x
        val qy = b1.y - a1.y
        val t = (qx * sy - qy * sx) / denominator
        val u = (qx * ry - qy * rx) / denominator
        if (t <= ENDPOINT_EPSILON || t >= 1 - ENDPOINT_EPSILON) return null
        if (u <= ENDPOINT_EPSILON || u >= 1 - ENDPOINT_EPSILON) return null
        return t to LocalPoint(a1.x + t * rx, a1.y + t * ry)
    }

    /** 신발끈 공식. 반시계가 양수다. */
    private fun signedArea(polygon: List<LocalPoint>): Double = polygon.indices.sumOf { i ->
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        a.x * b.y - b.x * a.y
    } / 2

    private companion object {
        const val MIN_POLYGON_POINTS = 3
        const val PARALLEL_EPSILON = 1e-12
        const val ENDPOINT_EPSILON = 1e-9
    }
}
