package kr.roadcanvas.app.test

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Then
import kr.roadcanvas.app.MainActivity
import org.junit.Assert.assertTrue

private const val DISTRACTION_OPTIMIZED = "distractionOptimized"

class DrivingScreenSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule

    @Then("앱 실행 화면은 Distraction Optimization을 선언한다")
    fun assertDistractionOptimized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(info.metaData?.getBoolean(DISTRACTION_OPTIMIZED) == true)
    }

    @Then("화면에 {string}가 보이지 않는다")
    @Then("화면에 {string}이 보이지 않는다")
    fun assertTextHidden(text: String) {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        assertTrue("화면에 \"$text\"가 남아 있다", nodes.isEmpty())
    }
}
