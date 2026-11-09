package kr.roadcanvas.app.profile.fake

import kr.roadcanvas.app.profile.ProfileFailure
import kr.roadcanvas.app.profile.ProfileStore
import kr.roadcanvas.app.profile.ProfileStoreException
import kr.roadcanvas.app.profile.ProfileSummary

/**
 * 메모리에 사용자별 요약과 닉네임을 두는 대체 저장소. [networkFailure]이면 모든 요청이 연결 실패다.
 * 닉네임을 바꾸면 [nicknames]에 사용자별 마지막 닉네임이 남는다.
 */
class FakeProfileStore : ProfileStore {
    private val summaries = mutableMapOf<String, ProfileSummary>()
    val nicknames = mutableMapOf<String, String>()
    var networkFailure = false

    fun putSummary(uid: String, summary: ProfileSummary) {
        summaries[uid] = summary
    }

    override suspend fun summary(uid: String): ProfileSummary {
        check()
        return summaries[uid] ?: ProfileSummary(null, 0, 0)
    }

    override suspend fun updateNickname(uid: String, nickname: String) {
        check()
        nicknames[uid] = nickname
    }

    fun reset() {
        summaries.clear()
        nicknames.clear()
        networkFailure = false
    }

    private fun check() {
        if (networkFailure) {
            throw ProfileStoreException(ProfileFailure.NETWORK, "테스트 연결 실패")
        }
    }
}
