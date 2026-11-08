package kr.roadcanvas.app.artwork.fake

import kr.roadcanvas.app.artwork.ArtworkPublication
import kr.roadcanvas.app.artwork.ArtworkPublicationRepository
import kr.roadcanvas.app.drawing.StorageResult

/** 메모리에 보관하는 공개 기록 저장소. [failure]를 지정하면 모든 요청이 그 오류로 실패한다. */
class FakeArtworkPublicationRepository : ArtworkPublicationRepository {
    private val publications = mutableMapOf<Long, ArtworkPublication>()

    var failure: Throwable? = null

    override suspend fun find(drawingId: Long) = result { publications[drawingId] }

    override suspend fun save(publication: ArtworkPublication) =
        result { publications[publication.drawingId] = publication }

    fun reset() {
        publications.clear()
        failure = null
    }

    private inline fun <T> result(block: () -> T): StorageResult<T> =
        failure?.let { StorageResult.Failure(it) } ?: StorageResult.Success(block())
}
