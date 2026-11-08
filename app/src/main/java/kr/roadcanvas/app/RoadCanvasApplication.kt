package kr.roadcanvas.app

import android.app.Application
import android.content.Context
import kr.roadcanvas.app.di.AppContainer
import kr.roadcanvas.app.di.DefaultAppContainer
import kr.roadcanvas.app.vehicle.VehicleIntegrationSelection

open class RoadCanvasApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** 테스트용 Application은 이 함수를 바꿔 대체 구현을 주입한다. */
    protected open fun createContainer(): AppContainer = DefaultAppContainer(
        VehicleIntegrationSelection.parse(
            location = BuildConfig.VEHICLE_LOCATION,
            driving = BuildConfig.VEHICLE_DRIVING,
            simulatedRoute = BuildConfig.VEHICLE_SIM_ROUTE
        )
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RoadCanvasApplication).container
