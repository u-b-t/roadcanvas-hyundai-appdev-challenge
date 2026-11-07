package kr.roadcanvas.app.test

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.junit.WithJunitRule
import kr.roadcanvas.app.MainActivity
import org.junit.Rule

@WithJunitRule
class ComposeRuleHolder {
    @get:Rule
    val composeRule = createEmptyComposeRule()
}

class HelloSteps(private val ruleHolder: ComposeRuleHolder) {
    private var activity: ActivityScenario<MainActivity>? = null

    @Given("앱을 실행한다")
    fun launchApp() {
        activity = ActivityScenario.launch(MainActivity::class.java)
    }

    @Then("화면에 {string}가 표시된다")
    fun assertTextDisplayed(text: String) {
        ruleHolder.composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @After
    fun closeApp() {
        activity?.close()
    }
}
