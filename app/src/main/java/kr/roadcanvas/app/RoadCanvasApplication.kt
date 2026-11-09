package kr.roadcanvas.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.roadcanvas.app.account.AccountState
import kr.roadcanvas.app.artwork.AccountArtworkAuthorProvider
import kr.roadcanvas.app.artwork.FirestoreArtworkStore
import kr.roadcanvas.app.artwork.UnavailableArtworkAuthorProvider
import kr.roadcanvas.app.artwork.UnavailableArtworkStore
import kr.roadcanvas.app.di.AppContainer
import kr.roadcanvas.app.di.DefaultAppContainer
import kr.roadcanvas.app.drawing.room.DrawingDatabase
import kr.roadcanvas.app.drawing.room.toStorage
import kr.roadcanvas.app.gallery.FirestorePublicArtworkStore
import kr.roadcanvas.app.gallery.UnavailablePublicArtworkStore
import kr.roadcanvas.app.map.MapAuth
import kr.roadcanvas.app.map.naver.NaverMapSetup
import kr.roadcanvas.app.recording.ServiceRecordingRunner
import kr.roadcanvas.app.upload.DrawingRegistration
import kr.roadcanvas.app.upload.FirestoreDrawingUploader
import kr.roadcanvas.app.vehicle.VehicleIntegrationSelection
import kr.roadcanvas.app.vehicle.aaos.AndroidCarUxRestrictionsClient
import kr.roadcanvas.app.vehicle.pleos.PleosLocationProvider

private const val TAG = "RoadCanvas"

/** 익명 인증과 완료 그림 등록을 다시 시도하는 간격 */
private const val ACCOUNT_RETRY_DELAY_MILLIS = 30_000L

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
            if (FirebaseApp.getApps(this).isEmpty()) {
                // google-services.json 없이 빌드하면 Firebase 없이 기기 저장만 쓴다.
                Log.i(TAG, "Firebase 설정이 없어 익명 인증을 건너뜁니다.")
            } else {
                accountScope.launch { registerAccount() }
            }
        }
    }

    /**
     * 인증에 실패하면 앱이 실행 중인 동안 일정 간격으로 다시 시도한다.
     * 인증되면 기기의 완료 그림을 주기적으로 Firebase에 등록한다.
     */
    private suspend fun registerAccount() {
        val registration = container.accountRegistration
        registration.register()
        while (registration.state.value is AccountState.Failed) {
            delay(ACCOUNT_RETRY_DELAY_MILLIS)
            registration.register()
        }
        val account = (registration.state.value as? AccountState.Ready)?.account ?: return
        val drawings =
            DrawingRegistration(container.completedDrawingRepository, FirestoreDrawingUploader())
        while (true) {
            try {
                drawings.register(account)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "완료 그림 등록을 다시 시도합니다.", error)
            }
            delay(ACCOUNT_RETRY_DELAY_MILLIS)
        }
    }

    /** 테스트용 Application은 이 함수를 바꿔 대체 구현을 주입한다. */
    protected open fun createContainer(): AppContainer {
        // google-services.json 없이 빌드했으면 작품 공개는 항상 계정 확인 실패로 안내한다.
        val firebase = FirebaseApp.getApps(this).isNotEmpty()
        return DefaultAppContainer(
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
            onRecordingStorageFailure = { Log.w(TAG, "경로 점을 저장하지 못했습니다.", it) },
            onArtworkFailure = { Log.w(TAG, "작품 공개 요청이 실패했습니다.", it) },
            artworkStore = if (firebase) FirestoreArtworkStore() else UnavailableArtworkStore,
            artworkAuthorProvider = {
                if (firebase) AccountArtworkAuthorProvider(it) else UnavailableArtworkAuthorProvider
            },
            // 공개 작품 목록은 같은 조건에서 항상 연결 실패로 안내한다.
            publicArtworkStore = if (firebase) {
                FirestorePublicArtworkStore()
            } else {
                UnavailablePublicArtworkStore
            },
            onGalleryFailure = { Log.w(TAG, "공개 작품 요청에 실패했습니다.", it) }
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RoadCanvasApplication).container
