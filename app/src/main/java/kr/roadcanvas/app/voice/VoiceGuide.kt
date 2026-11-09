package kr.roadcanvas.app.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 주행 중 음성 안내. 설정으로 끌 수 있고, 꺼져 있거나 음성 엔진이 없으면 아무것도 읽지 않는다. */
interface VoiceGuide {
    /** 음성 안내를 켰는지. 기본값은 켜짐이다. */
    val enabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)

    /** [text]를 읽는다. 읽던 안내는 멈추고 새 안내를 읽는다. */
    fun speak(text: String)
}

/** 음성 엔진을 쓰지 않는 구현. 설정은 기억하지만 읽지 않는다. */
class SilentVoiceGuide : VoiceGuide {
    private val mutableEnabled = MutableStateFlow(true)
    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        mutableEnabled.value = enabled
    }

    override fun speak(text: String) = Unit
}
