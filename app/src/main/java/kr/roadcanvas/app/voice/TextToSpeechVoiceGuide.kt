package kr.roadcanvas.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VoiceGuide"
private const val PREFERENCES = "voice_guide"
private const val KEY_ENABLED = "enabled"
private const val UTTERANCE_ID = "roadcanvas_voice_guide"

/**
 * 시스템 TTS로 읽는 음성 안내. 설정은 앱 전용 SharedPreferences에 보관한다.
 * 엔진은 처음 읽을 때 만들고, 엔진이 없거나 한국어를 쓸 수 없으면 이후 안내를 모두 건너뛴다.
 * 메인 스레드에서 호출한다.
 */
class TextToSpeechVoiceGuide(context: Context) : VoiceGuide {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))
    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    private var engine: TextToSpeech? = null
    private var ready = false
    private var unavailable = false
    private var pending: String? = null

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        mutableEnabled.value = enabled
        if (!enabled) {
            pending = null
            engine?.stop()
        }
    }

    override fun speak(text: String) {
        if (!enabled.value || unavailable) return
        val current = engine
        when {
            current == null -> {
                pending = text
                engine = TextToSpeech(appContext, ::onInit)
            }
            ready -> current.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            else -> pending = text
        }
    }

    private fun onInit(status: Int) {
        val current = engine ?: return
        val language = if (status ==
            TextToSpeech.SUCCESS
        ) {
            current.setLanguage(Locale.KOREAN)
        } else {
            null
        }
        if (language == null || language < TextToSpeech.LANG_AVAILABLE) {
            Log.w(TAG, "음성 엔진을 쓸 수 없어 음성 안내를 건너뜁니다. (상태 $status, 언어 $language)")
            unavailable = true
            pending = null
            current.shutdown()
            engine = null
            return
        }
        ready = true
        pending?.let { current.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) }
        pending = null
    }
}
