package kr.roadcanvas.app.test

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.CIRCLE_READY_TITLE
import kr.roadcanvas.app.challenge.ChallengeReadyScreen
import kr.roadcanvas.app.challenge.ChallengeReadyStatus
import kr.roadcanvas.app.modeselect.CIRCLE_CHALLENGE_NAME
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.vehicle.VehiclePermissions
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer

private const val WAIT_TIMEOUT_MILLIS = 5_000L

class ChallengeReadySteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @Given("동그라미 챌린지 준비 화면을 연다")
    fun openChallengeReady() {
        composeRule.mainClock.autoAdvance = true
        val launched = ActivityScenario.launch(ComponentActivity::class.java)
        launched.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    RoadCanvasNavHost(
                        navController = rememberNavController(),
                        splashDurationMillis = 0L
                    )
                }
            }
        }
        scenario = launched
        // 실제 앱과 같은 뒤로 가기 흐름을 위해 모드 선택에서 들어간다.
        waitForText(MODE_SELECT_TITLE)
        composeRule.onNode(hasText(CIRCLE_CHALLENGE_NAME) and hasClickAction()).performClick()
        waitForText(CIRCLE_READY_TITLE)
        // 준비 화면이 시작 조건을 구독한 뒤에 테스트 위치를 보내야 화면이 받는다.
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            fakeContainer.locationProvider.subscriberCount.value > 0
        }
    }

    @Given("위치 권한을 다시 묻지 않도록 거절한 준비 화면을 연다")
    fun openPermanentlyDeniedReady() {
        composeRule.mainClock.autoAdvance = true
        val launched = ActivityScenario.launch(ComponentActivity::class.java)
        launched.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    ChallengeReadyScreen(
                        status = ChallengeReadyStatus.PermissionRequired(
                            permissions = listOf(VehiclePermissions.FUSED_LOCATION),
                            permanentlyDenied = true
                        ),
                        onStart = {},
                        onBack = {},
                        onRequestPermission = {},
                        onOpenSettings = {},
                        onRetry = {}
                    )
                }
            }
        }
        scenario = launched
        waitForText(CIRCLE_READY_TITLE)
    }

    @Then("{string} 버튼이 활성화되어 있다")
    fun assertButtonEnabled(label: String) {
        waitForButton(label, isEnabled())
    }

    @Then("{string} 버튼이 비활성화되어 있다")
    fun assertButtonDisabled(label: String) {
        waitForButton(label, isNotEnabled())
    }

    @Then("{string} 버튼이 표시된다")
    fun assertButtonDisplayed(label: String) {
        waitForButton(label, isEnabled())
        composeRule.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
    }

    @Then("위치를 다시 요청한다")
    fun assertLocationRequestedAgain() {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            fakeContainer.locationProvider.requestCount.value >= 2
        }
    }

    @When("준비 화면에서 뒤로 가기를 누른다")
    fun pressBack() {
        composeRule.waitForIdle()
        requireNotNull(scenario).onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Then("시작 조건 확인 구독이 해제된다")
    fun assertUnsubscribed() {
        waitForText(MODE_SELECT_TITLE)
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            fakeContainer.locationProvider.subscriberCount.value == 0 &&
                fakeContainer.drivingRestrictionProvider.subscriberCount.value == 0
        }
    }

    @After
    fun closeScreen() {
        scenario?.close()
    }

    private fun waitForButton(label: String, state: SemanticsMatcher) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(label) and hasClickAction() and state)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
