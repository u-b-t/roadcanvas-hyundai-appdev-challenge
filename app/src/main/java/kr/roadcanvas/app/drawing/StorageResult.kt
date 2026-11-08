package kr.roadcanvas.app.drawing

import kotlin.coroutines.cancellation.CancellationException

/** 저장소 작업 결과. 실패하면 예외를 던지지 않고 [Failure]로 전달한다. */
sealed interface StorageResult<out T> {
    data class Success<out T>(val value: T) : StorageResult<T>

    data class Failure(val cause: Throwable) : StorageResult<Nothing>
}

/** [block]의 예외를 [StorageResult.Failure]로 바꾼다. 코루틴 취소는 그대로 전파한다. */
internal suspend fun <T> storageResultOf(block: suspend () -> T): StorageResult<T> = try {
    StorageResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    StorageResult.Failure(e)
}
