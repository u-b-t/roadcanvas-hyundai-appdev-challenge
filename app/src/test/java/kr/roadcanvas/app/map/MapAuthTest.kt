package kr.roadcanvas.app.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapAuthTest {
    @Test
    fun `키 ID가 비어 있으면 키 없음 상태다`() {
        assertEquals(MapAuthState.KeyMissing, MapAuth(clientId = "").state.value)
        assertEquals(MapAuthState.KeyMissing, MapAuth(clientId = "  ").state.value)
    }

    @Test
    fun `키 ID가 있으면 인증 실패를 받을 때까지 설정됨 상태다`() {
        val auth = MapAuth(clientId = "client-id")

        assertEquals(MapAuthState.Configured, auth.state.value)

        auth.reportFailure("401", "Unauthorized")

        assertEquals(MapAuthState.Failed("401", "Unauthorized"), auth.state.value)
    }
}
