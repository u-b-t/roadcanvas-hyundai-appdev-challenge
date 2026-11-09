package kr.roadcanvas.app.profile

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AccountState
import kr.roadcanvas.app.account.AnonymousAccount

/** 프로필 조회 결과. 요약을 읽지 못해도 계정을 확인했으면 닉네임은 보여줄 수 있다. */
sealed interface ProfileResult {
    data class Success(val account: AnonymousAccount, val summary: ProfileSummary) : ProfileResult

    /** [account]가 null이면 계정을 확인하지 못했다. */
    data class Failure(val account: AnonymousAccount?, val reason: ProfileFailure) : ProfileResult
}

/** 닉네임 변경 결과 */
sealed interface NicknameChangeResult {
    data class Changed(val nickname: String) : NicknameChangeResult

    /** 검사를 통과하지 못해 서버에 보내지 않았다. */
    data class Invalid(val reason: NicknameValidation) : NicknameChangeResult

    data class Failure(val reason: ProfileFailure) : NicknameChangeResult
}

/**
 * 익명 계정의 프로필. 닉네임과 내 기록 요약을 [store]에서 읽고 닉네임을 바꾼다.
 * 계정을 아직 확인하지 못했으면 한 번 더 인증해 본다. 바꾼 닉네임은 [registration]에 반영해
 * 이후 작품 공개·땅따먹기·순위 등록이 새 닉네임을 쓴다.
 */
class Profile(
    private val registration: AccountRegistration,
    private val store: ProfileStore,
    private val onFailure: (Throwable) -> Unit = {}
) {
    suspend fun load(): ProfileResult {
        val account = account() ?: return ProfileResult.Failure(null, ProfileFailure.AUTH)
        return try {
            ProfileResult.Success(account, store.summary(account.uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
            ProfileResult.Failure(account, failureOf(e))
        }
    }

    suspend fun changeNickname(input: String): NicknameChangeResult {
        val validation = validateNickname(input)
        if (validation !is NicknameValidation.Valid) return NicknameChangeResult.Invalid(validation)
        val account = account() ?: return NicknameChangeResult.Failure(ProfileFailure.AUTH)
        if (account.nickname == validation.nickname) {
            return NicknameChangeResult.Changed(validation.nickname)
        }
        return try {
            store.updateNickname(account.uid, validation.nickname)
            registration.replaceNickname(validation.nickname)
            NicknameChangeResult.Changed(validation.nickname)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
            NicknameChangeResult.Failure(failureOf(e))
        }
    }

    private suspend fun account(): AnonymousAccount? {
        (registration.state.value as? AccountState.Ready)?.let { return it.account }
        registration.register()
        return (registration.state.value as? AccountState.Ready)?.account
    }

    private fun failureOf(error: Throwable): ProfileFailure = when (error) {
        is ProfileStoreException -> error.failure
        is IOException -> ProfileFailure.NETWORK
        else -> ProfileFailure.UNKNOWN
    }
}
