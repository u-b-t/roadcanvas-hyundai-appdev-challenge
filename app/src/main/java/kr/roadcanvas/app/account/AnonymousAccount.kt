package kr.roadcanvas.app.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 인증 및 프로필 등록을 화면과 분리한다. */
data class AnonymousAccount(val uid: String, val nickname: String)

sealed interface AccountState {
    data object Idle : AccountState
    data object Loading : AccountState
    data class Ready(val account: AnonymousAccount) : AccountState
    data object Failed : AccountState
}

/** 익명 사용자에게 처음 부여하는 닉네임. 프로필 규칙의 1~30자를 지킨다. */
fun defaultNickname(uid: String): String = "드라이버-${uid.take(8)}"

fun interface AccountProvider {
    suspend fun getOrCreate(): AnonymousAccount
}

class AccountRegistration(private val provider: AccountProvider) {
    private val mutableState = MutableStateFlow<AccountState>(AccountState.Idle)
    val state: StateFlow<AccountState> = mutableState.asStateFlow()

    suspend fun register() {
        if (mutableState.value is AccountState.Loading) return
        mutableState.value = AccountState.Loading
        mutableState.value = try {
            AccountState.Ready(provider.getOrCreate())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            mutableState.value = AccountState.Idle
            throw cancelled
        } catch (_: Exception) {
            AccountState.Failed
        }
    }
}
