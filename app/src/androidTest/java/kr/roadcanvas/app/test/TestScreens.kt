package kr.roadcanvas.app.test

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kr.roadcanvas.app.ui.theme.RoadCanvasTheme

private const val TIMEOUT_MILLIS = 10_000L
private const val POLL_MILLIS = 20L

/**
 * Pleos 런처가 창 순서를 바꾸지 않은 채 이만큼 지나면 바뀜이 끝난 것으로 본다.
 * 에뮬레이터에서 태스크가 지워진 뒤 런처가 지도 태스크를 앞으로 가져오기까지 22~145ms가 걸렸다.
 */
private const val WINDOWS_STABLE_MILLIS = 400L

/**
 * 시나리오 화면을 열고 닫는다.
 *
 * 화면을 닫거나 시나리오가 끝나면 그 화면의 태스크가 지워진다. Pleos 에뮬레이터의 런처는 태스크가 지워지면
 * 잠시 뒤 지도 태스크를 앞으로 가져온다. 그 사이에 다음 화면을 열면 지도에 가려 일시정지되고, Compose 테스트는
 * 재개된 화면만 찾으므로 `No compose hierarchies found`로 실패한다. 그래서 태스크가 지워진 뒤 처음 화면을
 * 열기 전에 창 순서가 더 바뀌지 않을 때까지 기다린다.
 */
object TestScreens {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val opened = mutableListOf<TestScreen<*>>()
    private var taskRemoved = true

    /** [activityClass] 화면을 열고 재개될 때까지 기다린다. */
    fun <A : Activity> launch(activityClass: Class<A>): TestScreen<A> {
        if (taskRemoved) {
            awaitWindowsStable()
            taskRemoved = false
        }
        return TestScreen(ActivityScenario.launch(activityClass)).also { opened += it }
    }

    /** 빈 화면을 열고 [content]를 그린다. */
    fun launchContent(content: @Composable () -> Unit): TestScreen<ComponentActivity> =
        launch(ComponentActivity::class.java).also { screen ->
            screen.onActivity { it.setContent { RoadCanvasTheme { content() } } }
        }

    /** 닫히지 않은 시나리오 화면이 있는지 */
    val hasOpenScreens: Boolean get() = opened.any { it.state != Lifecycle.State.DESTROYED }

    /** 시나리오에서 연 화면을 모두 닫는다. */
    fun closeAll() {
        opened.asReversed().forEach { it.close() }
        opened.clear()
    }

    /** 시나리오가 끝나면 테스트 실행기가 남은 화면을 닫아 태스크가 지워진다. */
    fun onScenarioStarted() {
        opened.clear()
        taskRemoved = true
    }

    internal fun onClosed() {
        taskRemoved = true
    }

    /** 창 목록의 순서와 활성 상태가 [WINDOWS_STABLE_MILLIS] 동안 바뀌지 않을 때까지 기다린다. */
    private fun awaitWindowsStable() {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var last = ""
        var stableSince = SystemClock.uptimeMillis()
        val deadline = stableSince + TIMEOUT_MILLIS
        while (true) {
            val now = SystemClock.uptimeMillis()
            val current = automation.windows.joinToString { window ->
                "${window.id}:${window.layer}:${window.isActive}:${window.isFocused}"
            }
            if (current != last) {
                last = current
                stableSince = now
            } else if (now - stableSince >= WINDOWS_STABLE_MILLIS) {
                return
            }
            check(now < deadline) { "창 순서가 계속 바뀌어 화면을 열 수 없습니다: $current" }
            SystemClock.sleep(POLL_MILLIS)
        }
    }
}

/** [TestScreens]로 연 시나리오 화면 */
class TestScreen<A : Activity>(private val scenario: ActivityScenario<A>) {
    val state: Lifecycle.State get() = scenario.state

    /** 화면이 시작되어 아직 멈추지 않았는지 */
    val isStarted: Boolean get() = state.isAtLeast(Lifecycle.State.STARTED)

    fun onActivity(block: (A) -> Unit) {
        scenario.onActivity { block(it) }
    }

    fun close() {
        scenario.close()
        TestScreens.onClosed()
    }
}
