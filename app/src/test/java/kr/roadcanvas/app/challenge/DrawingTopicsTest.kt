package kr.roadcanvas.app.challenge

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingTopicsTest {
    @Test
    fun `주제는 20개 이상이고 서로 다르며 모두 이름 규칙에 맞는다`() {
        assertTrue(DRAWING_TOPICS.size >= 20)
        assertEquals(DRAWING_TOPICS.size, DRAWING_TOPICS.toSet().size)
        DRAWING_TOPICS.forEach { assertTrue(it, it.isNotBlank() && it.length <= 20) }
    }

    @Test
    fun `같은 날짜는 같은 주제이고 다음 날은 다음 주제다`() {
        val day = LocalDate.of(2026, 11, 9)

        assertEquals(topicOfDay(day), topicOfDay(LocalDate.of(2026, 11, 9)))
        val index = DRAWING_TOPICS.indexOf(topicOfDay(day))
        assertEquals(
            DRAWING_TOPICS[(index + 1) % DRAWING_TOPICS.size],
            topicOfDay(day.plusDays(1))
        )
    }

    @Test
    fun `목록 길이만큼 날짜가 지나면 모든 주제를 한 번씩 고른다`() {
        val start = LocalDate.of(2026, 1, 1)

        val topics = (0 until DRAWING_TOPICS.size).map { topicOfDay(start.plusDays(it.toLong())) }

        assertEquals(DRAWING_TOPICS.toSet(), topics.toSet())
    }

    @Test
    fun `다른 주제를 누르면 다음 주제로 바뀌고 한 바퀴 돌면 오늘의 주제로 돌아온다`() {
        val picker = DrawingTopicPicker(today = { LocalDate.ofEpochDay(0) })
        assertEquals(DRAWING_TOPICS[0], picker.topic.value)

        picker.next()
        assertEquals(DRAWING_TOPICS[1], picker.topic.value)

        repeat(DRAWING_TOPICS.size - 1) { picker.next() }
        assertEquals(DRAWING_TOPICS[0], picker.topic.value)
    }

    @Test
    fun `시작한 주제를 남기고 초기화하면 지운다`() {
        val picker = DrawingTopicPicker(today = { LocalDate.ofEpochDay(0) })
        assertNull(picker.startedTopic)

        picker.next()
        picker.startWith()
        assertEquals(DRAWING_TOPICS[1], picker.startedTopic)

        picker.reset()
        assertNull(picker.startedTopic)
        assertEquals(DRAWING_TOPICS[0], picker.topic.value)
        assertNotEquals(DRAWING_TOPICS[1], picker.topic.value)
    }
}
