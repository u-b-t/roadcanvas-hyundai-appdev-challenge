package kr.roadcanvas.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteMapStateTest {
    private val route = listOf(
        RoutePoint(37.5665, 126.9780),
        RoutePoint(37.5666, 126.9781),
        RoutePoint(37.5667, 126.9782)
    )

    @Test
    fun `키가 없으면 좌표와 관계없이 키 없음 안내를 표시한다`() {
        val state = RouteMapState.of(route, RouteMapOptions(), MapAuthState.KeyMissing)

        assertEquals(RouteMapState.KeyMissing, state)
        assertEquals("지도 키가 설정되지 않아 지도를 표시할 수 없습니다.", state.notice)
    }

    @Test
    fun `인증에 실패하면 오류 코드를 안내에 포함한다`() {
        val state = RouteMapState.of(
            route,
            RouteMapOptions(),
            MapAuthState.Failed("429", "quota")
        )

        assertEquals(RouteMapState.AuthFailed("429"), state)
        assertEquals("지도 인증에 실패해 지도를 표시할 수 없습니다. (오류 429)", state.notice)
    }

    @Test
    fun `좌표가 2개 미만이면 경로를 그리지 않고 마지막 위치를 알려준다`() {
        val empty = RouteMapState.of(emptyList(), RouteMapOptions(), MapAuthState.Configured)
        val single = RouteMapState.of(route.take(1), RouteMapOptions(), MapAuthState.Configured)

        assertEquals(RouteMapState.NotEnoughPoints(lastPoint = null), empty)
        assertEquals(RouteMapState.NotEnoughPoints(lastPoint = route.first()), single)
        assertEquals("경로를 그리려면 위치가 2개 이상 필요합니다.", single.notice)
    }

    @Test
    fun `완료 경로는 전체가 보이게 맞추고 출발점과 도착점을 표시한다`() {
        val state = RouteMapState.of(route, RouteMapOptions(), MapAuthState.Configured)

        assertEquals(
            RouteMapState.Route(
                points = route,
                camera = RouteCamera.FIT_BOUNDS,
                startMarker = route.first(),
                endMarker = route.last()
            ),
            state
        )
        assertNull(state.notice)
    }

    @Test
    fun `진행 중 경로는 최신 위치를 따라가고 도착점은 표시하지 않는다`() {
        val state = RouteMapState.of(
            route,
            RouteMapOptions(inProgress = true),
            MapAuthState.Configured
        ) as RouteMapState.Route

        assertEquals(RouteCamera.FOLLOW_LATEST, state.camera)
        assertEquals(route.first(), state.startMarker)
        assertNull(state.endMarker)
    }

    @Test
    fun `마커 표시를 끄면 출발점과 도착점 마커가 없다`() {
        val state = RouteMapState.of(
            route,
            RouteMapOptions(showEndpointMarkers = false),
            MapAuthState.Configured
        ) as RouteMapState.Route

        assertNull(state.startMarker)
        assertNull(state.endMarker)
    }

    @Test
    fun `현재 위치 표시는 경로선 대신 마지막 좌표를 현재 위치로 쓴다`() {
        val options = RouteMapOptions(showCurrentLocation = true)

        val located = RouteMapState.of(route, options, MapAuthState.Configured)
        val waiting = RouteMapState.of(emptyList(), options, MapAuthState.Configured)

        assertEquals(RouteMapState.CurrentLocation(route.last()), located)
        assertEquals(RouteMapState.CurrentLocation(point = null), waiting)
        assertNull(located.notice)
        assertNull(waiting.notice)
    }

    @Test
    fun `현재 위치 표시도 키가 없으면 키 없음 안내를 표시한다`() {
        val state = RouteMapState.of(
            route,
            RouteMapOptions(showCurrentLocation = true),
            MapAuthState.KeyMissing
        )

        assertEquals(RouteMapState.KeyMissing, state)
    }

    @Test
    fun `기준 원을 주면 경로와 함께 그리고 원 전체가 보이게 맞춘다`() {
        val circle = RouteCircle(RoutePoint(37.5665, 126.9780), radiusMeters = 100.0)

        val state = RouteMapState.of(
            route,
            RouteMapOptions(referenceCircle = circle),
            MapAuthState.Configured
        ) as RouteMapState.Route

        assertEquals(circle, state.circle)
        val (southWest, northEast) = circle.boundsCorners()
        // 위도 1도는 약 111.2km라 반지름 100m는 위도 약 0.0009도다.
        assertEquals(37.5665 - 0.000899, southWest.latitude, 0.000001)
        assertEquals(37.5665 + 0.000899, northEast.latitude, 0.000001)
        // 경도 간격은 위도가 높을수록 좁아 같은 거리의 경도 차가 더 크다.
        assertEquals(
            0.000899 / kotlin.math.cos(Math.toRadians(37.5665)),
            northEast.longitude - 126.9780,
            0.000001
        )
        assertEquals(route + listOf(southWest, northEast), state.boundsPoints)
    }

    @Test
    fun `기준 원이 없으면 경로 좌표만으로 맞춘다`() {
        val state = RouteMapState.of(route, RouteMapOptions(), MapAuthState.Configured)
            as RouteMapState.Route

        assertNull(state.circle)
        assertEquals(route, state.boundsPoints)
    }

    @Test
    fun `채운 영역은 꼭짓점이 3개 이상일 때만 경로와 함께 그린다`() {
        val area = route.reversed()
        val filled = RouteMapState.of(
            route,
            RouteMapOptions(filledArea = area),
            MapAuthState.Configured
        ) as RouteMapState.Route
        val tooFew = RouteMapState.of(
            route,
            RouteMapOptions(filledArea = area.take(2)),
            MapAuthState.Configured
        ) as RouteMapState.Route
        val none = RouteMapState.of(route, RouteMapOptions(), MapAuthState.Configured)
            as RouteMapState.Route

        assertEquals(area, filled.area)
        assertEquals(route, filled.boundsPoints)
        assertNull(tooFew.area)
        assertNull(none.area)
    }
}
