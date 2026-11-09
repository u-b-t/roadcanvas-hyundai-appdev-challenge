package kr.roadcanvas.app.upload

import kotlinx.coroutines.CancellationException
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.StorageResult

fun interface DrawingUploader {
    /** 같은 기록을 다시 보내도 서버에 중복 저장하지 않아야 한다. */
    suspend fun upload(account: AnonymousAccount, drawing: CompletedDrawing)
}

/**
 * 기기 저장을 원본으로 삼아 완료 그림을 등록한다.
 * 이번 실행에서 등록한 기록은 다시 보내지 않고, 실패한 기록은 다음 호출에서 다시 보낸다.
 * 한 기록이 실패해도 나머지를 등록한 뒤 첫 오류를 던진다.
 */
class DrawingRegistration(
    private val repository: CompletedDrawingRepository,
    private val uploader: DrawingUploader
) {
    private val registered = mutableSetOf<Pair<String, Long>>()

    suspend fun register(account: AnonymousAccount) {
        val summaries = when (val result = repository.summaries()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> throw result.cause
        }
        var firstError: Exception? = null
        summaries
            .filter { (account.uid to it.id) !in registered }
            .forEach { summary ->
                try {
                    val drawing = when (val result = repository.find(summary.id)) {
                        is StorageResult.Success -> result.value
                        is StorageResult.Failure -> throw result.cause
                    }
                    if (drawing != null) uploader.upload(account, drawing)
                    registered += account.uid to summary.id
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (firstError == null) firstError = error
                }
            }
        firstError?.let { throw it }
    }
}
