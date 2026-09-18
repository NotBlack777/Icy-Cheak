package com.icy.devcheckplus.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-lifecycle-scoped state for the "update available" UI.
 *
 * This is the fix for the update dialog only ever appearing once per process.
 * The root cause was that dialog *visibility* and the "an update is available"
 * *state* were collapsed into one value: [UpdateRepository.dismiss] both recorded
 * the dismissed version and set the check state back to Idle, and the dialog was
 * driven directly off a launch-time side effect. Dismissing it therefore
 * destroyed the only signal that could ever re-pop the dialog until a full
 * process restart re-ran the launch check.
 *
 * Here the two concerns are separated:
 *  - [checkState] is the repository's process-wide "what is the update status",
 *    which is **never cleared by dismissal**;
 *  - [dialogDismissed] is this Activity's "is the dialog currently hidden",
 *    which is all "Later" / the scrim tap touch.
 * Re-showing the dialog — via the Settings re-open row or a fresh manual check —
 * is just [dialogDismissed] = false. Survives rotation for the life of the task.
 */
class UpdateViewModel : ViewModel() {

    /** Latest check result, surfaced from [UpdateRepository.checkState]. */
    val checkState: StateFlow<UpdateCheckState> = UpdateRepository.checkState

    /** `true` while the full update dialog is hidden (dismissed or never raised). */
    private val _dialogDismissed = MutableStateFlow(false)
    val dialogDismissed: StateFlow<Boolean> = _dialogDismissed.asStateFlow()

    init {
        viewModelScope.launch {
            UpdateRepository.checkState.collect { state ->
                if (state is UpdateCheckState.Available) {
                    // A fresh check that found an update re-raises the dialog once,
                    // even if the previous result was dismissed this session.
                    _dialogDismissed.value = false
                }
            }
        }
    }

    /** Dismisses the dialog only — the update stays "available" underneath. */
    fun hideDialog() {
        _dialogDismissed.value = true
    }

    /** Re-raises the dialog without a network round-trip. */
    fun showDialog() {
        _dialogDismissed.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.NewInstanceFactory() {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel() as T
        }
    }
}
