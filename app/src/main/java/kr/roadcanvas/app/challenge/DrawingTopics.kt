package kr.roadcanvas.app.challenge

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 자유그림 오늘의 주제 목록. 날짜마다 차례로 하나를 고르며 순서를 바꾸면 날짜별 주제도 바뀐다. */
val DRAWING_TOPICS = listOf(
    "하트",
    "별",
    "물고기",
    "알파벳 S",
    "숫자 8",
    "달팽이",
    "번개",
    "물결",
    "삼각형",
    "네모",
    "초승달",
    "나비",
    "고양이 얼굴",
    "구름",
    "웃는 얼굴",
    "사과",
    "나뭇잎",
    "알파벳 Z",
    "화살표",
    "왕관",
    "자동차",
    "집"
)

/** [date]의 오늘의 주제. 날짜마다 [topics]를 차례로 돈다. */
fun topicOfDay(date: LocalDate, topics: List<String> = DRAWING_TOPICS): String =
    topics[dayIndex(date, topics)]

private fun dayIndex(date: LocalDate, topics: List<String>) =
    Math.floorMod(date.toEpochDay(), topics.size.toLong()).toInt()

/**
 * 자유그림 준비 화면의 오늘의 주제와 결과 화면의 이름 제안.
 * 준비 화면은 [topic]을 보여 주고 [next]로 다른 주제로 바꾼다. 자유그림을 시작하면 [startWith]로 그때 주제를 남기고,
 * 결과 화면은 [startedTopic]을 이름 입력 기본값으로 제안한다. 메모리에만 두므로 앱 프로세스가 다시 만들어지면 제안하지 않는다.
 */
class DrawingTopicPicker(
    private val today: () -> LocalDate = LocalDate::now,
    private val topics: List<String> = DRAWING_TOPICS
) {
    private var skipped = 0
    private val mutableTopic = MutableStateFlow(topicOfDay(today(), topics))

    val topic: StateFlow<String> = mutableTopic.asStateFlow()

    /** 마지막으로 시작한 자유그림의 주제. 시작하지 않았으면 null이다. */
    var startedTopic: String? = null
        private set

    /** 오늘의 주제 다음 주제로 바꾼다. 목록을 한 바퀴 돌면 오늘의 주제로 돌아온다. */
    fun next() {
        skipped = (skipped + 1) % topics.size
        mutableTopic.value = topicAt(skipped)
    }

    /** 지금 보이는 주제로 자유그림을 시작했다. */
    fun startWith(topic: String = mutableTopic.value) {
        startedTopic = topic
    }

    /** 처음 상태로 되돌린다. */
    fun reset() {
        skipped = 0
        startedTopic = null
        mutableTopic.value = topicAt(0)
    }

    private fun topicAt(offset: Int): String =
        topics[(dayIndex(today(), topics) + offset) % topics.size]
}
