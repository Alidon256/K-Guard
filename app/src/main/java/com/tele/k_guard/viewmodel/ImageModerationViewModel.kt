package com.tele.k_guard.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.k_guard.data.model.ModerationUiState
import com.tele.k_guard.data.repository.ModerationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImageModerationViewModel(
    private val moderationRepository: ModerationRepository = ModerationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ModerationUiState>(ModerationUiState.Idle)
    val uiState: StateFlow<ModerationUiState> = _uiState.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    fun setSelectedImage(uri: Uri?) {
        _selectedImageUri.value = uri
        if (uri == null) {
            _uiState.value = ModerationUiState.Idle
        }
    }

    fun submitImageForModeration(context: Context, childId: String?) {
        val uri = _selectedImageUri.value
        if (uri == null) {
            _uiState.value = ModerationUiState.Error("Please select an image first.")
            return
        }
        if (childId == null) {
            _uiState.value = ModerationUiState.Error("Add a child profile first so K-Guard knows which age rules to apply.")
            return
        }

        _uiState.value = ModerationUiState.Loading
        viewModelScope.launch {
            moderationRepository.moderateImage(context.applicationContext, uri, childId).fold(
                onSuccess = { response ->
                    _uiState.value = ModerationUiState.Success(
                        response = response,
                        originalContent = uri.toString(),
                        isImage = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = ModerationUiState.Error(
                        error.localizedMessage ?: "Failed to moderate image content."
                    )
                }
            )
        }
    }

    fun resetState() {
        _selectedImageUri.value = null
        _uiState.value = ModerationUiState.Idle
    }
}
