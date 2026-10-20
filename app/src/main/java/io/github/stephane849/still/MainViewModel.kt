package io.github.stephane849.still

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.stephane849.still.BuildConfig
import io.github.stephane849.still.data.SearchProvider
import io.github.stephane849.still.data.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository(application)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Expose the key required for the currently selected backend
    private val apiKeyFlow: Flow<String?> = combine(
        userPreferencesRepository.searchProvider,
        userPreferencesRepository.googleApiKey,
        userPreferencesRepository.hereApiKey
    ) { provider, googleKey, hereKey ->
        when (provider) {
            SearchProvider.GOOGLE_PLACES -> googleKey
            // Key entered in Settings first, then one built into the app.
            SearchProvider.HERE -> hereKey.ifBlank { BuildConfig.HERE_API_KEY }
        }
    }

    val apiKey: StateFlow<String?> = apiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    init {
        apiKeyFlow.onEach {
            _isLoading.value = false
        }.launchIn(viewModelScope)
    }
}
