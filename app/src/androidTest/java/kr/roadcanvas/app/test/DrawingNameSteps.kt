package kr.roadcanvas.app.test

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.mydrawings.MY_DRAWINGS_LIST_TAG
import kr.roadcanvas.app.ui.components.CAR_TEXT_INPUT_TAG
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 그림 이름 시나리오. 결과 화면과 내 그림 상세의 이름 입력창을 실제 화면 흐름으로 조작한다. */
class DrawingNameSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer

    private val nameField = hasSetTextAction() and hasAnyAncestor(hasTestTag(CAR_TEXT_INPUT_TAG))

    @When("이름 입력창에 {string}를 입력한다")
    @When("이름 입력창에 {string}을 입력한다")
    fun typeName(text: String) {
        waitFor(nameField)
        scrollIntoView(nameField)
        composeRule.onNode(nameField).performTextClearance()
        composeRule.onNode(nameField).performTextInput(text)
        composeRule.waitForIdle()
    }

    /** 결과 카드나 상세 오버레이가 스크롤되어 있어도 버튼까지 스크롤한 뒤 누른다. */
    @When("{string} 버튼을 찾아 누른다")
    fun scrollAndClick(label: String) {
        val button = hasText(label) and hasClickAction()
        waitFor(button)
        scrollIntoView(button)
        composeRule.onNode(button).performClick()
        composeRule.waitForIdle()
    }

    @Then("이름 입력창에 {string} 안내와 글자 수 {string}이 표시된다")
    fun assertPlaceholderAndCounter(placeholder: String, counter: String) {
        waitFor(hasText(placeholder))
        scrollIntoView(hasText(placeholder))
        composeRule.onNodeWithText(placeholder).assertIsDisplayed()
        assertCounter(counter)
    }

    @Then("이름 입력창에 글자 수 {string}이 표시된다")
    fun assertCounter(counter: String) {
        waitFor(hasText(counter))
        scrollIntoView(hasText(counter))
        composeRule.onNodeWithText(counter).assertIsDisplayed()
    }

    @Then("이름 입력창의 터치 영역 높이는 76dp 이상이다")
    fun assertTouchTarget() {
        waitFor(hasTestTag(CAR_TEXT_INPUT_TAG))
        val bounds = composeRule.onNodeWithTag(CAR_TEXT_INPUT_TAG).getUnclippedBoundsInRoot()
        assertTrue("입력창 높이 ${bounds.height}", bounds.height >= MIN_TOUCH_TARGET)
        assertTrue("입력창 너비 ${bounds.width}", bounds.width >= 76.dp)
    }

    @Then("이름 입력창에 {string}가 남아 있다")
    fun assertFieldText(text: String) {
        val node = composeRule.onNode(nameField).fetchSemanticsNode()
        assertEquals(text, node.config.getOrNull(SemanticsProperties.EditableText)?.text)
    }

    @Then("저장된 완료 기록의 이름이 없다")
    fun assertNoSavedName() {
        assertNull(savedSummary().name)
    }

    @Then("저장된 완료 기록의 이름은 {string}다")
    fun assertSavedName(name: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) { savedSummary().name == name }
        assertEquals(name, savedSummary().name)
    }

    @Then("목록의 {string} 항목이 표시된다")
    fun assertListItem(title: String) {
        val item = hasText(title) and hasClickAction() and
            hasAnyAncestor(hasTestTag(MY_DRAWINGS_LIST_TAG))
        waitFor(item)
        composeRule.onNode(item).assertIsDisplayed()
    }

    private fun savedSummary() = runBlocking {
        val result = fakeContainer.completedDrawingRepository.summaries()
        assertTrue("저장 결과: $result", result is StorageResult.Success)
        (result as StorageResult.Success).value.single()
    }

    /** 스크롤 영역 안에 있으면 보이도록 스크롤한다. */
    private fun scrollIntoView(matcher: SemanticsMatcher) {
        val scrollable = matcher and hasAnyAncestor(hasScrollAction())
        if (composeRule.onAllNodes(scrollable).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNode(matcher).performScrollTo()
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
