package kr.roadcanvas.app.drawing.room

import kr.roadcanvas.app.artwork.ArtworkPublication
import kr.roadcanvas.app.artwork.ArtworkPublicationRepository
import kr.roadcanvas.app.artwork.PublishStatus
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.storageResultOf

class RoomArtworkPublicationRepository(private val dao: ArtworkPublicationDao) :
    ArtworkPublicationRepository {
    override suspend fun find(drawingId: Long): StorageResult<ArtworkPublication?> =
        storageResultOf {
            dao.find(drawingId)?.let {
                ArtworkPublication(it.drawingId, it.artworkId, PublishStatus.fromKey(it.status))
            }
        }

    /** 없는 완료 기록이면 외래 키 제약으로 실패한다. */
    override suspend fun save(publication: ArtworkPublication): StorageResult<Unit> =
        storageResultOf {
            dao.upsert(
                ArtworkPublicationEntity(
                    drawingId = publication.drawingId,
                    artworkId = publication.artworkId,
                    status = publication.status.key
                )
            )
        }
}
