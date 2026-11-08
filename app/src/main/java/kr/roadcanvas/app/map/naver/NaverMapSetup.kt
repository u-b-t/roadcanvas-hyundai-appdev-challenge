package kr.roadcanvas.app.map.naver

import android.content.Context
import android.util.Log
import com.naver.maps.map.NaverMapSdk
import kr.roadcanvas.app.map.MapAuth

/** 앱 시작 시 네이버 지도 SDK 클라이언트와 인증 실패 처리를 설정한다. */
object NaverMapSetup {
    private const val TAG = "NaverMapSetup"

    fun configure(context: Context, auth: MapAuth) {
        val sdk = NaverMapSdk.getInstance(context)
        if (auth.clientId.isBlank()) {
            Log.w(TAG, "NAVER_MAP_CLIENT_ID가 없어 지도를 표시하지 않습니다.")
        } else {
            sdk.client = NaverMapSdk.NcpKeyClient(auth.clientId)
        }
        sdk.onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { exception ->
            Log.e(TAG, "네이버 지도 인증 실패: ${exception.errorCode}", exception)
            auth.reportFailure(exception.errorCode, exception.message)
        }
    }
}
