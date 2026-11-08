package kr.roadcanvas.app.score

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** 점수 계산 테스트에 쓰는 경로 생성기. 로컬 평면 좌표(m)를 위도·경도로 바꾼다. */
object TestPaths {
    private const val BASE_LATITUDE = 37.5665
    private const val BASE_LONGITUDE = 126.9780
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val SPACING_METERS = 2.0

    /** 출발점에서 시작해 [turns]바퀴 도는 원. 반시계가 기본이다. */
    fun circle(
        radius: Double,
        turns: Double = 1.0,
        clockwise: Boolean = false,
        noiseSigma: Double = 0.0,
        seed: Int = 15
    ): List<GeoPoint> {
        val sign = if (clockwise) -1.0 else 1.0
        val local = arc(radius, turns * 2 * PI, sign)
        return toGeo(addNoise(local, noiseSigma, seed))
    }

    /** 반지름 [radius]의 반원. 출발점과 도착점이 지름만큼 떨어진다. */
    fun semicircle(radius: Double): List<GeoPoint> = toGeo(arc(radius, PI, 1.0))

    /** 길이 [length]의 직선을 갔다가 같은 길로 돌아온다. */
    fun straightOutAndBack(length: Double): List<GeoPoint> {
        val out = line(0.0 to 0.0, length to length * 0.5)
        return toGeo(out + out.reversed().drop(1))
    }

    /** 한 변 [side]인 정사각형 1바퀴. */
    fun square(side: Double): List<GeoPoint> {
        val corners = listOf(0.0 to 0.0, side to 0.0, side to side, 0.0 to side, 0.0 to 0.0)
        return toGeo(
            corners.zipWithNext { a, b -> line(a, b).dropLast(1) }.flatten() + corners.last()
        )
    }

    /** 장반경 [semiMajor], 장단축비 [ratio]인 타원 1바퀴. */
    fun ellipse(semiMajor: Double, ratio: Double): List<GeoPoint> {
        val semiMinor = semiMajor / ratio
        val count = (2 * PI * semiMajor / SPACING_METERS).toInt()
        val local = (0..count).map { i ->
            val t = 2 * PI * i / count
            semiMajor * cos(t) - semiMajor to semiMinor * sin(t)
        }
        return toGeo(local)
    }

    /**
     * 원을 [missingFraction]만큼 남기고 돌다가 위치 기록이 끊긴 구간을
     * 출발점까지 직선으로 잇는다. 앱 재실행으로 중간 점이 빠진 상황이다.
     */
    fun circleWithGap(radius: Double, missingFraction: Double): List<GeoPoint> {
        val recorded = arc(radius, (1 - missingFraction) * 2 * PI, 1.0)
        return toGeo(recorded.dropLast(1) + line(recorded.last(), recorded.first()))
    }

    /** 한 자리에서 [count]번 기록한 위치. */
    fun stationary(count: Int): List<GeoPoint> =
        List(count) { GeoPoint(BASE_LATITUDE, BASE_LONGITUDE) }

    private fun arc(radius: Double, angle: Double, sign: Double): List<Pair<Double, Double>> {
        val count = maxOf(2, (radius * angle / SPACING_METERS).toInt())
        // 출발점이 원점에 오도록 중심을 (-radius, 0)에 둔다.
        return (0..count).map { i ->
            val t = sign * angle * i / count
            radius * cos(t) - radius to radius * sin(t)
        }
    }

    private fun line(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>
    ): List<Pair<Double, Double>> {
        val length = hypot(to.first - from.first, to.second - from.second)
        val count = maxOf(1, (length / SPACING_METERS).toInt())
        return (0..count).map { i ->
            val t = i.toDouble() / count
            from.first + (to.first - from.first) * t to from.second + (to.second - from.second) * t
        }
    }

    private fun addNoise(
        points: List<Pair<Double, Double>>,
        sigma: Double,
        seed: Int
    ): List<Pair<Double, Double>> {
        if (sigma == 0.0) return points
        val random = Random(seed)
        return points.map { (x, y) ->
            x + sigma * random.nextGaussian() to
                y + sigma * random.nextGaussian()
        }
    }

    private fun Random.nextGaussian(): Double {
        // Box-Muller 변환
        val u1 = nextDouble(Double.MIN_VALUE, 1.0)
        val u2 = nextDouble()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u1)) * cos(2 * PI * u2)
    }

    private fun toGeo(points: List<Pair<Double, Double>>): List<GeoPoint> {
        val cosLat = cos(Math.toRadians(BASE_LATITUDE))
        return points.map { (x, y) ->
            GeoPoint(
                latitude = BASE_LATITUDE + Math.toDegrees(y / EARTH_RADIUS_METERS),
                longitude = BASE_LONGITUDE + Math.toDegrees(x / (EARTH_RADIUS_METERS * cosLat))
            )
        }
    }
}
