package kr.roadcanvas.app.territory

import kr.roadcanvas.app.account.AccountRegistration
import kr.roadcanvas.app.account.AccountState

/**
 * 익명 인증으로 받은 UID와 자동 닉네임을 획득 사용자로 쓴다.
 * 아직 인증하지 않았거나 실패했으면 한 번 더 인증해 보고, 그래도 안 되면 인증 실패다.
 */
class AccountTerritoryOwnerProvider(private val registration: AccountRegistration) :
    TerritoryOwnerProvider {
    override suspend fun current(): TerritoryOwner {
        (registration.state.value as? AccountState.Ready)?.let { return it.account.toOwner() }
        registration.register()
        val ready = registration.state.value as? AccountState.Ready
            ?: throw TerritoryStoreException(TerritoryFailure.AUTH, "익명 계정을 확인하지 못했습니다.")
        return ready.account.toOwner()
    }

    private fun kr.roadcanvas.app.account.AnonymousAccount.toOwner() = TerritoryOwner(uid, nickname)
}
