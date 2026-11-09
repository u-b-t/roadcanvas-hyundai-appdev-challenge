package kr.roadcanvas.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.roadcanvas.app.account.AnonymousAccount

/** 내 기록 요약 상태 */
sealed interface ProfileSummaryState {
    data object Loading : ProfileSummaryState

    data class Loaded(val summary: ProfileSummary) : ProfileSummaryState

    data class Failed(val reason: ProfileFailure) : ProfileSummaryState
}

/** 닉네임 저장 상태 */
sealed interface NicknameSaveState {
    data object Idle : NicknameSaveState

    data object Saving : NicknameSaveState

    data object Saved : NicknameSaveState

    data class Failed(val reason: ProfileFailure) : NicknameSaveState
}

/**
 * 프로필 화면 상태. [account]는 확인한 계정이며 확인 전이나 실패면 null이다.
 * [input]은 닉네임 입력값, [inputError]는 저장을 시도했을 때 검사를 통과하지 못한 이유다.
 */
data class ProfileUiState(
    val account: AnonymousAccount? = null,
    val summary: ProfileSummaryState = ProfileSummaryState.Loading,
    val input: String = "",
    val inputError: NicknameValidation? = null,
    val save: NicknameSaveState = NicknameSaveState.Idle
)

/** 들어오면 프로필을 읽고 입력창을 현재 닉네임으로 채운다. 입력을 바꾸면 저장 결과 안내를 지운다. */
class ProfileViewModel(private val profile: Profile) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        mutableState.update { it.copy(summary = ProfileSummaryState.Loading) }
        loadJob = viewModelScope.launch {
            val result = profile.load()
            val account = when (result) {
                is ProfileResult.Success -> result.account
                is ProfileResult.Failure -> result.account
            }
            mutableState.update { current ->
                current.copy(
                    account = account ?: current.account,
                    summary = when (result) {
                        is ProfileResult.Success -> ProfileSummaryState.Loaded(result.summary)
                        is ProfileResult.Failure -> ProfileSummaryState.Failed(result.reason)
                    },
                    // 처음 읽을 때만 입력창을 현재 닉네임으로 채워 입력 중인 값을 덮어쓰지 않는다.
                    input = if (current.account == null && account != null) {
                        account.nickname
                    } else {
                        current.input
                    }
                )
            }
        }
    }

    fun onInputChange(input: String) {
        mutableState.update {
            it.copy(input = input, inputError = null, save = NicknameSaveState.Idle)
        }
    }

    fun save() {
        if (mutableState.value.save == NicknameSaveState.Saving) return
        mutableState.update { it.copy(save = NicknameSaveState.Saving, inputError = null) }
        viewModelScope.launch {
            val result = profile.changeNickname(mutableState.value.input)
            mutableState.update { current ->
                when (result) {
                    is NicknameChangeResult.Changed -> current.copy(
                        account = current.account?.copy(nickname = result.nickname),
                        input = result.nickname,
                        save = NicknameSaveState.Saved
                    )
                    is NicknameChangeResult.Invalid -> current.copy(
                        inputError = result.reason,
                        save = NicknameSaveState.Idle
                    )
                    is NicknameChangeResult.Failure -> current.copy(
                        save = NicknameSaveState.Failed(result.reason)
                    )
                }
            }
        }
    }
}
