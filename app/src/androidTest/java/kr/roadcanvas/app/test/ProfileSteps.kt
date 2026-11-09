package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.profile.PROFILE_LABEL
import kr.roadcanvas.app.profile.PROFILE_NICKNAME_TAG
import kr.roadcanvas.app.profile.PROFILE_SUMMARY_ROW_TAG
import kr.roadcanvas.app.profile.PROFILE_TITLE
import kr.roadcanvas.app.profile.ProfileSummary
import kr.roadcanvas.app.ui.components.CAR_TEXT_INPUT_TAG
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 프로필 시나리오. 대체 프로필 저장소에 요약을 두고 메인 화면에서 화면을 연다. */
class ProfileSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val store get() = fakeContainer.profileStore
    private val myUid get() = FakeAppContainer.DEFAULT_VOTER_UID

    private val nicknameField =
        hasSetTextAction() and hasAnyAncestor(hasTestTag(CAR_TEXT_INPUT_TAG))

    @Given("프로필 서버에 동그라미 최고 점수 {int}점, 내 땅 {int}칸, 공개 작품 {int}개가 있다")
    fun putSummary(score: Int, cells: Int, artworks: Int) {
        store.putSummary(myUid, ProfileSummary(score, cells.toLong(), artworks))
    }

    @Given("프로필 서버에 연결할 수 없다")
    fun disconnect() {
        store.networkFailure = true
    }

    @Given("메인 화면에서 프로필 화면을 연다")
    @When("메인 화면에서 프로필 화면을 연다")
    fun openProfile() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
        // 제목 줄의 버튼은 스크롤 영역 밖이라 스크롤하지 않고 누른다.
        val button = hasText(PROFILE_LABEL) and hasClickAction()
        composeRule.waitForHome()
        waitFor(button)
        composeRule.onNode(button).performClick()
        waitFor(hasText(PROFILE_TITLE) and !hasClickAction())
        waitFor(hasTestTag(PROFILE_NICKNAME_TAG) and !hasText("-"))
        composeRule.waitForIdle()
    }

    @When("닉네임 입력창에 {string}를 입력한다")
    @When("닉네임 입력창에 {string}을 입력한다")
    fun typeNickname(text: String) {
        waitFor(nicknameField)
        composeRule.onNode(nicknameField).performTextClearance()
        composeRule.onNode(nicknameField).performTextInput(text)
        composeRule.waitForIdle()
    }

    @Then("프로필 닉네임은 {string}이다")
    fun assertNickname(nickname: String) {
        waitFor(hasTestTag(PROFILE_NICKNAME_TAG) and hasText(nickname))
    }

    @Then("프로필 요약 {string}는 {string}이다")
    @Then("프로필 요약 {string}은 {string}이다")
    fun assertSummary(label: String, value: String) {
        waitFor(hasTestTag(PROFILE_SUMMARY_ROW_TAG) and hasText(label) and hasText(value))
    }

    @Then("프로필 서버에 저장된 닉네임은 {string}이다")
    fun assertStoredNickname(nickname: String) {
        composeRule.waitForIdle()
        assertEquals(nickname, store.nicknames[myUid])
    }

    @Then("프로필 서버에 저장된 닉네임이 없다")
    fun assertNothingStored() {
        composeRule.waitForIdle()
        assertTrue(store.nicknames.isEmpty())
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
