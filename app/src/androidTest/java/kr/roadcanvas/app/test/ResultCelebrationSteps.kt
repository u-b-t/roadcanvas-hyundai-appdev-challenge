package kr.roadcanvas.app.test

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.BEST_RECORD_BADGE_TAG
import kr.roadcanvas.app.challenge.CELEBRATION_TAG
import kr.roadcanvas.app.challenge.CelebrationPlayedKey
import kr.roadcanvas.app.challenge.ResultCelebration
import kr.roadcanvas.app.challenge.TERRITORY_OUTCOME_TAG
import kr.roadcanvas.app.challenge.TODAY_TOPIC_TAG
import kr.roadcanvas.app.challenge.newCellsCelebration
import kr.roadcanvas.app.challenge.stolenCellsCelebration
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.territory.highlightedCountTag
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val UNDER_CELEBRATION_BUTTON = "연출 아래 버튼"

/** 결과 축하 연출과 자유그림 오늘의 주제 시나리오 */
class ResultCelebrationSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private var underButtonClicks = 0

    @Then("축하 연출로 {string}을 보여 줬다")
    @Then("축하 연출로 {string}를 보여 줬다")
    fun assertCelebrationPlayed(message: String) {
        waitFor(hasTestTag(CELEBRATION_TAG) and playedCelebration(message))
    }

    @Then("축하 연출로 빼앗은 칸 수를 보여 줬다")
    fun assertStolenCelebration() {
        assertCelebrationPlayed(stolenCellsCelebration(submittedOutcome().stolenCells))
    }

    @Then("축하 연출로 새로 차지한 칸 수를 보여 줬다")
    fun assertNewCellsCelebration() {
        val outcome = submittedOutcome()
        assertEquals(0, outcome.stolenCells)
        assertCelebrationPlayed(newCellsCelebration(outcome.newCells))
    }

    @Then("축하 연출을 보여 주지 않았다")
    fun assertNoCelebration() {
        composeRule.waitForIdle()
        val played = composeRule.onAllNodes(hasTestTag(CELEBRATION_TAG))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrElseNullable(CelebrationPlayedKey) { null } }
        assertTrue("보여 준 연출 $played", played.isEmpty())
    }

    @Then("결과에 최고 기록 배지가 표시된다")
    fun assertBestBadge() {
        showInScroll(hasTestTag(BEST_RECORD_BADGE_TAG))
    }

    @Then("결과에 최고 기록 배지가 없다")
    fun assertNoBestBadge() {
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodes(hasTestTag(BEST_RECORD_BADGE_TAG)).fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Then("결과 카드에 빼앗은 칸 수 안내가 남아 있다")
    fun assertStolenOutcomeText() {
        val text = stolenCellsCelebration(submittedOutcome().stolenCells)
        showInScroll(hasTestTag(TERRITORY_OUTCOME_TAG) and hasText(text))
    }

    @Then("서버 반영의 {string} 수가 강조되어 표시된다")
    fun assertHighlightedCount(label: String) {
        showInScroll(hasTestTag(highlightedCountTag(label)))
    }

    @Given("버튼 위에 축하 연출 {string}을 띄운다")
    fun showCelebrationOverButton(message: String) {
        composeRule.mainClock.autoAdvance = false
        TestScreens.launchContent {
            Box(modifier = Modifier.fillMaxSize()) {
                Button(
                    onClick = { underButtonClicks++ },
                    modifier = Modifier.fillMaxSize()
                ) { Text(text = UNDER_CELEBRATION_BUTTON) }
                // 버튼 가운데에 연출을 겹쳐 연출이 터치를 가로채는지 확인한다.
                ResultCelebration(message = message, modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    @Then("축하 연출 {string}이 보인다")
    fun assertCelebrationVisible(message: String) {
        composeRule.onNode(hasText(message) and hasAnyAncestor(hasTestTag(CELEBRATION_TAG)))
            .assertIsDisplayed()
    }

    @Then("축하 연출 {string}이 보이지 않는다")
    fun assertCelebrationGone(message: String) {
        assertTrue(
            composeRule.onAllNodes(hasText(message) and hasAnyAncestor(hasTestTag(CELEBRATION_TAG)))
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.mainClock.autoAdvance = true
    }

    @When("축하 연출 아래 버튼을 누른다")
    fun clickUnderCelebration() {
        composeRule.onNode(hasText(UNDER_CELEBRATION_BUTTON), useUnmergedTree = true)
            .performClick()
    }

    @Then("아래 버튼이 눌렸다")
    fun assertUnderButtonClicked() {
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1, underButtonClicks)
    }

    @Then("오늘의 주제는 {string}다")
    @Then("오늘의 주제는 {string}이다")
    fun assertTopic(topic: String) {
        waitFor(hasTestTag(TODAY_TOPIC_TAG) and hasText(topic))
        showInScroll(hasTestTag(TODAY_TOPIC_TAG) and hasText(topic))
    }

    @Then("오늘의 주제가 보이지 않는다")
    fun assertNoTopic() {
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodes(hasTestTag(TODAY_TOPIC_TAG)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Then("이름 입력창에 기본값 {string}이 채워져 있다")
    @Then("이름 입력창에 기본값 {string}가 채워져 있다")
    fun assertSuggestedName(name: String) {
        // 입력창은 안내 문구와 글자 수를 함께 가지므로 입력값만 비교한다.
        waitFor(hasEditableText(name))
    }

    @Then("완료 기록의 이름은 {string}이다")
    @Then("완료 기록의 이름은 {string}다")
    fun assertSavedName(name: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            savedNames() == listOf(name)
        }
    }

    private fun savedNames(): List<String?> {
        val summaries = runBlocking { fakeContainer.completedDrawingRepository.summaries() }
        return (summaries as StorageResult.Success).value.map { it.name }
    }

    private fun submittedOutcome() =
        requireNotNull(fakeContainer.territoryStore.capture("capture-1")).outcome

    private fun playedCelebration(message: String) =
        SemanticsMatcher.expectValue(CelebrationPlayedKey, message)

    private fun hasEditableText(text: String) = SemanticsMatcher("입력값 $text") { node ->
        node.config.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text == text
    }

    private fun showInScroll(matcher: SemanticsMatcher) {
        waitFor(matcher)
        val node = composeRule.onAllNodes(matcher).onFirst()
        if (composeRule.onAllNodes(matcher and hasAnyAncestor(hasScrollAction()))
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            node.performScrollTo()
        }
        node.assertIsDisplayed()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
