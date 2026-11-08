package kr.roadcanvas.app

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.roadcanvas.app.account.AccountState
import kr.roadcanvas.app.di.AppContainer
import kr.roadcanvas.app.di.DefaultAppContainer
import kr.roadcanvas.app.drawing.room.DrawingDatabase
import kr.roadcanvas.app.drawing.room.toStorage
import kr.roadcanvas.app.map.MapAuth
import kr.roadcanvas.app.map.naver.NaverMapSetup
import kr.roadcanvas.app.recording.ServiceRecordingRunner
import kr.roadcanvas.app.vehicle.VehicleIntegrationSelection
import kr.roadcanvas.app.vehicle.aaos.AndroidCarUxRestrictionsClient
import kr.roadcanvas.app.vehicle.pleos.PleosLocationProvider

private const val TAG = "RoadCanvas"

open class RoadCanvasApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val accountScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 빌드에 넣은 네이버 지도 키 ID와 SDK가 알린 인증 실패 상태 */
    val mapAuth = MapAuth(BuildConfig.NAVER_MAP_CLIENT_ID)

    override fun onCreate() {
        super.onCreate()
        NaverMapSetup.configure(this, mapAuth)
        container = createContainer()
        if (container is DefaultAppContainer) {
            accountScope.launch {
                do {
                    container.accountRegistration.register()
                    if (container.accountRegistration.state.value is AccountState.Failed) {
                        delay(
                            30_000
                        )
                    }
                } while (container.accountRegistration.state.value is AccountState.Failed)
            }
        }
    }

    /** 테스트용 Application은 이 함수를 바꿔 대체 구현을 주입한다. */
    protected open fun createContainer(): AppContainer = DefaultAppContainer(
        VehicleIntegrationSelection.parse(
            location = BuildConfig.VEHICLE_LOCATION,
            driving = BuildConfig.VEHICLE_DRIVING,
            simulatedRoute = BuildConfig.VEHICLE_SIM_ROUTE
        ),
        drawingStorage = DrawingDatabase.create(this).toStorage(),
        pleosLocationProvider = { PleosLocationProvider.create(this) },
        carUxRestrictionsClient = { AndroidCarUxRestrictionsClient(this) },
        mapAuth = mapAuth,
        recordingRunner = { ServiceRecordingRunner(this) },
        onRecordingStorageFailure = { Log.w(TAG, "경로 점을 저장하지 못했습니다.", it) }
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RoadCanvasApplication).container
