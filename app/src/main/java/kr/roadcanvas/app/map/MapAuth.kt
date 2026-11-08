package kr.roadcanvas.app.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 지도 SDK 인증 상태 */
sealed interface MapAuthState {
    /** 빌드에 키 ID가 없다. 지도를 만들지 않는다. */
    data object KeyMissing : MapAuthState

    /** 키 ID가 있고 아직 인증 실패를 받지 않았다. */
    data object Configured : MapAuthState

    /** SDK가 인증 실패를 알렸다. [errorCode]는 401, 429, 800 등 SDK가 준 값이다. */
    data class Failed(val errorCode: String, val message: String?) : MapAuthState
}

/** 빌드에 넣은 지도 키 ID와 SDK가 알린 인증 실패를 앱 상태로 보관한다. */
class MapAuth(val clientId: String) {
    private val mutableState = MutableStateFlow(
        if (clientId.isBlank()) MapAuthState.KeyMissing else MapAuthState.Configured
    )

    val state: StateFlow<MapAuthState> = mutableState.asStateFlow()

    fun reportFailure(errorCode: String, message: String?) {
        mutableState.value = MapAuthState.Failed(errorCode, message)
    }
}
