package kr.roadcanvas.app.profile

/**
 * 프로필 화면의 내 기록 요약. [circleBestScore]는 동그라미 순위에 등록한 최고 점수이며 없으면 null이다.
 * [territoryCellCount]는 땅따먹기 순위의 보유 칸 수, [publishedArtworkCount]는 공개 중인 내 작품 수다.
 */
data class ProfileSummary(
    val circleBestScore: Int?,
    val territoryCellCount: Long,
    val publishedArtworkCount: Int
)

/** 프로필을 읽거나 닉네임을 바꾸지 못한 이유. 화면이 안내 문구를 고르는 데 쓴다. */
enum class ProfileFailure {
    /** 서버에 연결하지 못했다. */
    NETWORK,

    /** 익명 계정을 확인하지 못했거나 서버가 권한을 거부했다. */
    AUTH,

    /** 알 수 없는 오류다. */
    UNKNOWN
}

/** 서버 저장소 오류를 실패 이유로 알린다. */
class ProfileStoreException(
    val failure: ProfileFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 프로필 서버 저장소. 실패하면 [ProfileStoreException]이나 다른 예외를 던진다.
 * 서버 문서 구조는 docs/firebase-client.md를 따른다.
 */
interface ProfileStore {
    /** [uid] 사용자의 내 기록 요약 */
    suspend fun summary(uid: String): ProfileSummary

    /**
     * [uid] 사용자의 닉네임을 [nickname]으로 바꾼다. 프로필(`users/{uid}`)과 닉네임을 복사해 둔
     * 동그라미 순위, 땅따먹기 순위, 내 작품을 한 번에 바꾸며 하나라도 실패하면 아무것도 바꾸지 않는다.
     */
    suspend fun updateNickname(uid: String, nickname: String)
}

/** 서버가 연결되지 않은 빌드에서 쓰는 저장소. 모든 요청이 연결 실패다. */
object UnavailableProfileStore : ProfileStore {
    private fun unavailable(): Nothing =
        throw ProfileStoreException(ProfileFailure.NETWORK, "프로필 서버가 연결되지 않았습니다.")

    override suspend fun summary(uid: String) = unavailable()

    override suspend fun updateNickname(uid: String, nickname: String) = unavailable()
}
