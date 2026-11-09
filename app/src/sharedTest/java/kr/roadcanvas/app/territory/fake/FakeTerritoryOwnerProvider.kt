package kr.roadcanvas.app.territory.fake

import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritoryOwner
import kr.roadcanvas.app.territory.TerritoryOwnerProvider
import kr.roadcanvas.app.territory.TerritoryStoreException

/** 정해진 사용자를 주는 대체 구현. [owner]가 null이면 인증 실패다. */
class FakeTerritoryOwnerProvider : TerritoryOwnerProvider {
    var owner: TerritoryOwner? = DEFAULT_OWNER

    override suspend fun current(): TerritoryOwner =
        owner ?: throw TerritoryStoreException(TerritoryFailure.AUTH, "인증 실패")

    fun reset() {
        owner = DEFAULT_OWNER
    }

    companion object {
        val DEFAULT_OWNER = TerritoryOwner("test", "드라이버-test")
    }
}
