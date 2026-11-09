package kr.roadcanvas.app.artwork

import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.DrawingNameValidation
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.validateDrawingName

/** 공개·비공개 전환·이름 반영 결과 */
sealed interface ArtworkResult {
    data object Success : ArtworkResult

    data class Failure(val reason: PublishFailure) : ArtworkResult
}

/**
 * 이름 붙인 자유그림을 공개 작품으로 등록하고 비공개로 되돌린다.
 *
 * 등록 순서는 서버 명세를 따른다: 작품 ID를 처음 한 번 만들어 기기에 보관 → DRAFT 문서 저장 →
 * 경로 청크 저장 → 저장된 청크의 개수와 순서 확인 → PUBLISHED로 전환.
 * 실패하면 기기 기록은 그대로 두고 공개 상태만 실패로 남겨 같은 작품 ID로 다시 시도한다.
 * 화면을 떠나도 진행 중인 요청과 상태 저장은 끝까지 한다.
 */
class ArtworkPublisher(
    private val publications: ArtworkPublicationRepository,
    private val store: ArtworkStore,
    private val authorProvider: ArtworkAuthorProvider,
    private val newArtworkId: () -> String = { UUID.randomUUID().toString() },
    private val onFailure: (Throwable) -> Unit = {}
) {
    private val mutableInFlight = MutableStateFlow(emptySet<Long>())

    /** 지금 등록하거나 비공개로 바꾸는 중인 완료 기록 ID */
    val inFlight: StateFlow<Set<Long>> = mutableInFlight.asStateFlow()

    /** 공개할 수 있는 기록인지. 이름을 붙인 자유그림만 공개한다. */
    fun publishable(drawing: CompletedDrawing): Boolean =
        drawing.mode == DrawingMode.FREE_DRAWING && titleOf(drawing.name) != null

    /**
     * [drawingId]의 공개 상태. 요청 중이면 등록 중이다.
     * 등록 중으로 저장된 채 요청이 없으면 앱이 등록 도중 종료된 것이므로 실패로 본다.
     */
    suspend fun status(drawingId: Long): StorageResult<PublishStatus> {
        if (drawingId in
            mutableInFlight.value
        ) {
            return StorageResult.Success(PublishStatus.PUBLISHING)
        }
        return when (val found = publications.find(drawingId)) {
            is StorageResult.Failure -> found
            is StorageResult.Success -> StorageResult.Success(
                when (val status = found.value?.status) {
                    null -> PublishStatus.UNPUBLISHED
                    PublishStatus.PUBLISHING -> PublishStatus.FAILED
                    else -> status
                }
            )
        }
    }

    /** [drawing]을 공개한다. 이미 요청 중이면 아무것도 하지 않고 실패를 돌려준다. */
    suspend fun publish(drawing: CompletedDrawing): ArtworkResult = exclusive(drawing.id) {
        val title = titleOf(drawing.name)
        if (drawing.mode != DrawingMode.FREE_DRAWING || title == null) {
            return@exclusive ArtworkResult.Failure(PublishFailure.INVALID)
        }
        val chunks = try {
            artworkChunksOf(drawing.path)
        } catch (e: InvalidArtworkRouteException) {
            onFailure(e)
            return@exclusive ArtworkResult.Failure(PublishFailure.INVALID)
        }
        val existing = when (val found = publications.find(drawing.id)) {
            is StorageResult.Failure -> return@exclusive storageFailure(found.cause)
            is StorageResult.Success -> found.value
        }
        // 재시도와 다시 공개에도 같은 문서를 쓰도록 요청 전에 작품 ID를 기기에 남긴다.
        val publication = ArtworkPublication(
            drawingId = drawing.id,
            artworkId = existing?.artworkId ?: newArtworkId(),
            status = PublishStatus.PUBLISHING
        )
        publications.save(publication).let {
            if (it is StorageResult.Failure) return@exclusive storageFailure(it.cause)
        }
        val failure = try {
            upload(publication.artworkId, drawing, title, chunks)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
            failureOf(e)
        }
        val status = if (failure == null) PublishStatus.PUBLISHED else PublishStatus.FAILED
        val saved = publications.save(publication.copy(status = status))
        when {
            failure != null -> ArtworkResult.Failure(failure)
            saved is StorageResult.Failure -> storageFailure(saved.cause)
            else -> ArtworkResult.Success
        }
    }

    /** 공개한 작품을 비공개(DRAFT)로 되돌린다. 실패하면 공개 상태를 유지한다. */
    suspend fun unpublish(drawingId: Long): ArtworkResult = exclusive(drawingId) {
        val publication = when (val found = publications.find(drawingId)) {
            is StorageResult.Failure -> return@exclusive storageFailure(found.cause)
            is StorageResult.Success -> found.value
        }
        if (publication?.status != PublishStatus.PUBLISHED) return@exclusive ArtworkResult.Success
        try {
            store.setVisibility(publication.artworkId, ArtworkVisibility.DRAFT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
            return@exclusive ArtworkResult.Failure(failureOf(e))
        }
        when (val saved = publications.save(publication.copy(status = PublishStatus.UNPUBLISHED))) {
            is StorageResult.Failure -> storageFailure(saved.cause)
            is StorageResult.Success -> ArtworkResult.Success
        }
    }

    /**
     * 바꾼 이름 [name]을 공개 작품의 title에 반영한다. 공개 중이 아니면 다음 공개 때 반영되므로 아무것도 하지 않는다.
     */
    suspend fun syncTitle(drawingId: Long, name: String?): ArtworkResult =
        withContext(NonCancellable) {
            val publication = when (val found = publications.find(drawingId)) {
                is StorageResult.Failure -> return@withContext storageFailure(found.cause)
                is StorageResult.Success -> found.value
            }
            if (publication?.status != PublishStatus.PUBLISHED) {
                return@withContext ArtworkResult.Success
            }
            val title = titleOf(name)
                ?: return@withContext ArtworkResult.Failure(PublishFailure.INVALID)
            try {
                store.updateTitle(publication.artworkId, title)
                ArtworkResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
                ArtworkResult.Failure(failureOf(e))
            }
        }

    private suspend fun upload(
        artworkId: String,
        drawing: CompletedDrawing,
        title: String,
        chunks: List<ArtworkChunk>
    ) {
        val author = try {
            authorProvider.current()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ArtworkStoreException(PublishFailure.AUTH, "작성자를 확인하지 못했습니다.", e)
        }
        val nickname = author.nickname.trim()
        if (nickname.isEmpty() || nickname.length > ARTWORK_NICKNAME_MAX_LENGTH) {
            throw ArtworkStoreException(PublishFailure.INVALID, "닉네임이 공개 규칙에 맞지 않습니다.")
        }
        store.saveDraft(
            author.copy(nickname = nickname),
            artworkId,
            ArtworkDraft(
                nickname = nickname,
                title = title,
                distanceMeters = drawing.distanceMeters.coerceAtLeast(0.0),
                durationMillis = drawing.durationMillis.coerceAtLeast(0L),
                pointChunkCount = chunks.size
            )
        )
        chunks.forEach { store.saveChunk(artworkId, it) }
        if (!chunksComplete(store.chunkIndices(artworkId), chunks.size)) {
            throw ArtworkStoreException(PublishFailure.INCOMPLETE, "저장된 경로 청크가 맞지 않습니다.")
        }
        store.setVisibility(artworkId, ArtworkVisibility.PUBLISHED)
    }

    /** 같은 기록의 요청은 하나씩만 하고, 화면을 떠나도 끝까지 한다. */
    private suspend fun exclusive(
        drawingId: Long,
        block: suspend () -> ArtworkResult
    ): ArtworkResult {
        var started = false
        mutableInFlight.update {
            if (drawingId in it) {
                it
            } else {
                started = true
                it + drawingId
            }
        }
        if (!started) return ArtworkResult.Failure(PublishFailure.UNKNOWN)
        return try {
            withContext(NonCancellable) { block() }
        } finally {
            mutableInFlight.update { it - drawingId }
        }
    }

    private fun storageFailure(cause: Throwable): ArtworkResult {
        onFailure(cause)
        return ArtworkResult.Failure(PublishFailure.STORAGE)
    }

    private fun failureOf(error: Throwable): PublishFailure = when (error) {
        is ArtworkStoreException -> error.failure
        is IOException -> PublishFailure.NETWORK
        else -> PublishFailure.UNKNOWN
    }

    /** 공개 규칙에 맞는 작품 이름. 앞뒤 공백을 제거하고 1~20자가 아니면 null이다. */
    private fun titleOf(name: String?): String? =
        (validateDrawingName(name.orEmpty(), required = true) as? DrawingNameValidation.Valid)?.name
}
