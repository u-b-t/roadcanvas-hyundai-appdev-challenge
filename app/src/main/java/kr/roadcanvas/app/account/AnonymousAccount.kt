package kr.roadcanvas.app.account

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 인증 및 프로필 등록을 화면과 분리한다. */
data class AnonymousAccount(val uid: String, val nickname: String)

sealed interface AccountState {
    data object Idle : AccountState
    data object Loading : AccountState
    data class Ready(val account: AnonymousAccount) : AccountState
    data object Failed : AccountState
}

/** 닉네임을 알 수 없는 사용자를 가리킬 때 쓰는 이름. 프로필 규칙의 1~30자를 지킨다. */
fun defaultNickname(uid: String): String = "드라이버-${uid.take(8)}"

private val NICKNAME_ADJECTIVES = listOf(
    "빠른", "느긋한", "용감한", "반짝이는", "씩씩한", "조용한", "신나는", "든든한",
    "날쌘", "푸른", "다정한", "똑똑한", "부지런한", "명랑한", "힘찬", "포근한"
)
private val NICKNAME_NOUNS = listOf(
    "고래", "여우", "호랑이", "다람쥐", "펭귄", "부엉이", "돌고래", "수달",
    "치타", "판다", "참새", "거북이", "사슴", "독수리", "토끼", "곰"
)

/**
 * 새 익명 사용자에게 처음 부여하는 "형용사 명사 숫자" 닉네임(예: "빠른 고래 12").
 * 같은 [uid]면 같은 닉네임이라 프로필 생성 트랜잭션이 다시 실행돼도 바뀌지 않는다. 숫자는 1~99다.
 */
fun readableNickname(uid: String): String {
    val random = Random(uid.hashCode())
    val adjective = NICKNAME_ADJECTIVES[random.nextInt(NICKNAME_ADJECTIVES.size)]
    val noun = NICKNAME_NOUNS[random.nextInt(NICKNAME_NOUNS.size)]
    return "$adjective $noun ${random.nextInt(1, 100)}"
}

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

    /** 서버에 저장한 새 닉네임을 이후 작품 공개·땅따먹기·순위 등록에 쓰도록 계정에 반영한다. */
    fun replaceNickname(nickname: String) {
        mutableState.update { state ->
            if (state is AccountState.Ready) {
                AccountState.Ready(state.account.copy(nickname = nickname))
            } else {
                state
            }
        }
    }
}
