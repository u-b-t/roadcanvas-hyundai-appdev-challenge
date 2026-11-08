package kr.roadcanvas.app.test

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.After
import io.cucumber.java.Before
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer

private const val SUBSCRIBER_TIMEOUT_MILLIS = 5_000L

/**
 * 모든 시나리오가 앞 시나리오의 화면, 기록 세션과 대체 구현 상태 없이 시작하도록 전후를 정리한다.
 * 다른 훅보다 먼저 초기화하고 가장 나중에 정리한다.
 */
class ScenarioHooks {
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer

    @Before(order = 0)
    fun startClean() {
        TestScreens.onScenarioStarted()
        fakeContainer.reset()
        awaitNoSubscribers()
    }

    @After(order = 0)
    fun cleanUp() {
        TestScreens.closeAll()
        fakeContainer.reset()
    }

    /** 화면과 기록 세션을 닫은 뒤에도 남은 구독이 있으면 다음 시나리오가 그 구독으로 위치를 받으므로 실패시킨다. */
    private fun awaitNoSubscribers() {
        val deadline = SystemClock.uptimeMillis() + SUBSCRIBER_TIMEOUT_MILLIS
        while (
            fakeContainer.locationProvider.subscriberCount.value != 0 ||
            fakeContainer.drivingRestrictionProvider.subscriberCount.value != 0
        ) {
            check(SystemClock.uptimeMillis() < deadline) { "앞 시나리오의 위치·주행 규제 구독이 남아 있습니다." }
            SystemClock.sleep(10)
        }
    }
}
