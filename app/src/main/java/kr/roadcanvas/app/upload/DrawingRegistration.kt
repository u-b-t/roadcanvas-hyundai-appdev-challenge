package kr.roadcanvas.app.upload

import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.StorageResult

fun interface DrawingUploader {
    suspend fun upload(account: AnonymousAccount, drawing: CompletedDrawing)
}

/** 로컬 저장을 원본으로 삼아 실패 시 다음 실행에서 다시 등록한다. */
class DrawingRegistration(
    private val repository: CompletedDrawingRepository,
    private val uploader: DrawingUploader
) {
    suspend fun register(account: AnonymousAccount) {
        val summaries = when (val result = repository.summaries()) {
            is StorageResult.Success -> result.value
            is StorageResult.Failure -> throw result.cause
        }
        summaries.forEach { summary ->
            val drawing = when (val result = repository.find(summary.id)) {
                is StorageResult.Success -> result.value
                is StorageResult.Failure -> throw result.cause
            }
            if (drawing != null) uploader.upload(account, drawing)
        }
    }
}
