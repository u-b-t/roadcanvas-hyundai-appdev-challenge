package kr.roadcanvas.app.artwork

import kr.roadcanvas.app.drawing.StorageResult

/** 완료 기록의 작품 공개 상태 */
enum class PublishStatus(val key: String) {
    /** 공개한 적이 없거나 비공개(DRAFT)로 되돌렸다. */
    UNPUBLISHED("unpublished"),

    /** 작품 문서와 경로를 올리는 중이다. */
    PUBLISHING("publishing"),

    /** 다른 사용자에게 공개(PUBLISHED)되어 있다. */
    PUBLISHED("published"),

    /** 등록하지 못했다. 같은 작품 ID로 다시 시도할 수 있다. */
    FAILED("failed");

    companion object {
        fun fromKey(key: String): PublishStatus = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("알 수 없는 공개 상태: $key")
    }
}

/**
 * 완료 기록 [drawingId]를 공개할 때 쓰는 작품 ID와 마지막 공개 상태.
 * [artworkId]는 처음 공개를 시도할 때 한 번 만들고 재시도와 다시 공개에도 그대로 쓴다.
 */
data class ArtworkPublication(val drawingId: Long, val artworkId: String, val status: PublishStatus)

/** 작품 공개 기록을 기기에 보관하는 저장소. 완료 기록을 지우면 함께 지워진다. */
interface ArtworkPublicationRepository {
    /** [drawingId]의 공개 기록. 공개를 시도한 적이 없으면 null이다. */
    suspend fun find(drawingId: Long): StorageResult<ArtworkPublication?>

    /** 공개 기록을 저장한다. 같은 완료 기록의 기존 기록은 바꾼다. */
    suspend fun save(publication: ArtworkPublication): StorageResult<Unit>
}

/** 공개하지 못한 이유. 화면이 안내 문구를 고르는 데 쓴다. */
enum class PublishFailure {
    /** 익명 계정을 확인하지 못했다. */
    AUTH,

    /** 서버에 연결하지 못했거나 요청이 중간에 실패했다. */
    NETWORK,

    /** 경로 일부를 올리지 못했거나 올린 경로의 개수·순서가 맞지 않는다. */
    INCOMPLETE,

    /** 작품 이름, 닉네임이나 경로가 공개 규칙에 맞지 않는다. */
    INVALID,

    /** 기기 기록을 읽거나 저장하지 못했다. */
    STORAGE,

    /** 서버가 요청을 거부했거나 알 수 없는 오류다. */
    UNKNOWN
}
