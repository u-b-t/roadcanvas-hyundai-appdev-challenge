package kr.roadcanvas.app.challenge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.DrawingNameValidation
import kr.roadcanvas.app.drawing.InProgressDrawingRepository
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.validateDrawingName
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.score.CircleScoreCalculator

/**
 * [startedAtMillis]에 시작해 [endedAtMillis]에 종료한 도전의 점수를 계산하고 완료 기록으로 저장한다.
 *
 * 저장에 성공하면 부여된 ID를 [SavedStateHandle]에 남기고 진행 중 기록을 지운다.
 * 화면이 다시 만들어지거나 프로세스가 다시 만들어져도 ID가 있으면 저장하지 않고 그 기록을 보여준다.
 * 저장에 실패하면 진행 중 기록을 남겨 두어 다시 저장하거나 앱을 다시 실행해 저장할 수 있다.
 * 완료 기록을 저장한 뒤 진행 중 기록을 지우기 전에 프로세스가 종료됐다면, 같은 완료 시각의 기록을 찾아
 * 다시 저장하지 않고 그 기록을 보여준다.
 *
 * 이름을 선택 입력하는 모드는 완료 기록을 저장한 뒤 [saveName]으로 이름을 붙인다.
 */
class ChallengeResultViewModel(
    private val startedAtMillis: Long,
    private val endedAtMillis: Long,
    private val savedStateHandle: SavedStateHandle,
    private val recorder: ChallengeRecorder,
    private val inProgressRepository: InProgressDrawingRepository,
    private val completedRepository: CompletedDrawingRepository,
    private val calculator: CircleScoreCalculator = CircleScoreCalculator(),
    private val onStorageFailure: (Throwable) -> Unit = {}
) : ViewModel() {
    private val mutableState =
        MutableStateFlow<ChallengeResultUiState>(ChallengeResultUiState.Calculating)

    val uiState: StateFlow<ChallengeResultUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    /** 저장에 실패했을 때만 다시 저장한다. */
    fun retrySave() {
        val current = mutableState.value as? ChallengeResultUiState.Calculated ?: return
        if (current.saveStatus != ResultSaveStatus.FAILED) return
        mutableState.value = current.copy(saveStatus = ResultSaveStatus.SAVING)
        viewModelScope.launch { save(current.result) }
    }

    /**
     * 저장한 완료 기록에 [input]을 이름으로 붙인다. 완료 기록을 저장하기 전이거나 이름 저장 중이면 무시하고,
     * 이름 규칙에 맞지 않으면 저장하지 않는다. 화면이 규칙 위반을 안내한다.
     */
    fun saveName(input: String) {
        val current = mutableState.value as? ChallengeResultUiState.Calculated ?: return
        if (current.saveStatus != ResultSaveStatus.SAVED) return
        if (current.nameSaveStatus == NameSaveStatus.SAVING) return
        val id = savedStateHandle.get<Long>(KEY_SAVED_ID) ?: return
        val validation = validateDrawingName(input, current.result.mode.nameRequired)
        val name = (validation as? DrawingNameValidation.Valid ?: return).name
        mutableState.value = current.copy(nameSaveStatus = NameSaveStatus.SAVING)
        viewModelScope.launch {
            val renamed = withContext(NonCancellable) { completedRepository.rename(id, name) }
            if (renamed is StorageResult.Failure) onStorageFailure(renamed.cause)
            mutableState.update {
                val calculated = it as? ChallengeResultUiState.Calculated ?: return@update it
                when (renamed) {
                    is StorageResult.Success ->
                        calculated.copy(name = name, nameSaveStatus = NameSaveStatus.SAVED)
                    is StorageResult.Failure ->
                        calculated.copy(nameSaveStatus = NameSaveStatus.FAILED)
                }
            }
        }
    }

    private suspend fun load() {
        val savedId = savedStateHandle.get<Long>(KEY_SAVED_ID)
        if (savedId != null) {
            showSaved(savedId)
            return
        }
        val result = finishedResult()
        if (result == null) {
            mutableState.value = ChallengeResultUiState.Unavailable
            return
        }
        val existing = completedRepository.findId(result.mode, result.completedAtMillis)
        val existingId = (existing as? StorageResult.Success)?.value
        if (existingId != null) {
            savedStateHandle[KEY_SAVED_ID] = existingId
            showSaved(existingId)
            return
        }
        mutableState.value = ChallengeResultUiState.Calculated(result, ResultSaveStatus.SAVING)
        save(result)
    }

    private suspend fun showSaved(id: Long) {
        val drawing = (completedRepository.find(id) as? StorageResult.Success)?.value
        mutableState.value = if (drawing == null) {
            ChallengeResultUiState.Unavailable
        } else {
            ChallengeResultUiState.Calculated(
                challengeResultOf(drawing, calculator),
                ResultSaveStatus.SAVED,
                name = drawing.name
            )
        }
        // 저장한 뒤 진행 중 기록을 지우지 못했다면 다시 지운다.
        clearInProgress()
    }

    /** 메모리의 종료 상태가 이 도전이면 그 경로를, 아니면 저장된 진행 중 기록의 경로를 쓴다. */
    private suspend fun finishedResult(): ChallengeResult? {
        val finished = recorder.state.value as? RecordingState.Finished
        if (finished != null && finished.startedAtMillis == startedAtMillis) {
            return challengeResultOf(
                finished.mode,
                finished.path,
                finished.startedAtMillis,
                finished.endedAtMillis,
                calculator
            )
        }
        val loaded = inProgressRepository.load()
        if (loaded is StorageResult.Failure) onStorageFailure(loaded.cause)
        val drawing = (loaded as? StorageResult.Success)?.value
            ?.takeIf { it.startedAtMillis == startedAtMillis }
            ?: return null
        return challengeResultOf(
            drawing.mode,
            drawing.path,
            startedAtMillis,
            endedAtMillis,
            calculator
        )
    }

    /** 저장 도중 화면을 떠나도 저장과 진행 중 기록 삭제는 끝까지 한다. */
    private suspend fun save(result: ChallengeResult) = withContext(NonCancellable) {
        val status = when (val saved = completedRepository.save(result.toCompletedDrawing())) {
            is StorageResult.Success -> {
                savedStateHandle[KEY_SAVED_ID] = saved.value
                clearInProgress()
                ResultSaveStatus.SAVED
            }
            is StorageResult.Failure -> {
                onStorageFailure(saved.cause)
                ResultSaveStatus.FAILED
            }
        }
        mutableState.update {
            (it as? ChallengeResultUiState.Calculated)?.copy(saveStatus = status) ?: it
        }
    }

    /** 이 도전의 진행 중 기록만 지운다. 지우지 못해도 완료 기록은 저장되었으므로 결과는 저장됨이다. */
    private suspend fun clearInProgress() {
        val current = (inProgressRepository.load() as? StorageResult.Success)?.value ?: return
        if (current.startedAtMillis != startedAtMillis) return
        val cleared = inProgressRepository.clear()
        if (cleared is StorageResult.Failure) onStorageFailure(cleared.cause)
    }

    companion object {
        const val KEY_SAVED_ID = "savedDrawingId"
    }
}
