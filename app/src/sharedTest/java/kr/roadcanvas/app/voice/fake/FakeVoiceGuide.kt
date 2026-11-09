package kr.roadcanvas.app.voice.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.roadcanvas.app.voice.VoiceGuide

/** 읽은 안내를 [spoken]에 남기는 대체 구현. 꺼져 있으면 남기지 않는다. */
class FakeVoiceGuide : VoiceGuide {
    private val mutableEnabled = MutableStateFlow(true)
    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    private val mutableSpoken = MutableStateFlow(emptyList<String>())
    val spoken: StateFlow<List<String>> = mutableSpoken.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
    }

    override fun speak(text: String) {
        if (enabled.value) mutableSpoken.value += text
    }

    fun reset() {
        mutableEnabled.value = true
        mutableSpoken.value = emptyList()
    }
}
