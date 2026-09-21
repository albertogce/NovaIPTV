package com.algoce95.novaiptv.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.storage.CredentialStore
import com.algoce95.novaiptv.core.storage.PrefsStore
import com.algoce95.novaiptv.core.utils.UrlNormalizer
import com.algoce95.novaiptv.data.api.XtreamApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Paridad con `AuthController` de Flutter (login validado contra la API). */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Authenticated : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val prefs: PrefsStore = AppContainer.prefs,
    private val credentials: CredentialStore = AppContainer.credentials,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(server: String, username: String, password: String) {
        if (_state.value == AuthUiState.Loading) return
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            if (!isOnline()) {
                _state.value = AuthUiState.Error("Sin conexión a internet. Comprueba tu red e inténtalo de nuevo.")
                return@launch
            }
            try {
                val normalized = UrlNormalizer.normalizeUrl(server)
                val client = XtreamApiClient.create(normalized, username, password)
                // La API confirma la cuenta; sin esto cualquier credencial entraba.
                val info = client.getServerInfo()
                val userInfo = info.optJSONObject("user_info")
                val auth = userInfo?.opt("auth")
                val authorized = auth == 1 || auth.toString() == "1" || auth == true
                if (!authorized) {
                    val status = userInfo?.opt("status")?.toString()?.trim().orEmpty()
                    _state.value = AuthUiState.Error(
                        if (status.isNotEmpty()) {
                            "Acceso denegado: $status."
                        } else {
                            "Usuario o contraseña incorrectos, o cuenta inactiva."
                        },
                    )
                    return@launch
                }
                credentials.save(username, password, normalized)
                // La caducidad solo se refresca al iniciar sesión, no en auto-arranque.
                val expDate = userInfo?.opt("exp_date")?.toString()?.trim().orEmpty()
                prefs.setString(PrefsStore.Keys.ACCOUNT_EXP_DATE, expDate)
                AppContainer.refreshApi()
                _state.value = AuthUiState.Authenticated
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(friendlyError(e))
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = AppContainer.appContext
            ?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return true
        val net = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(net)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No se pudo encontrar el servidor. Revisa la URL introducida."
        is java.net.SocketTimeoutException -> "El servidor tardó demasiado en responder. Inténtalo de nuevo."
        is javax.net.ssl.SSLException -> "Fallo de seguridad (HTTPS) al conectar con el servidor."
        is java.io.IOException -> "Fallo de red al conectar con el servidor."
        else -> "No se pudo iniciar sesión: ${e.message ?: e.javaClass.simpleName}"
    }
}
