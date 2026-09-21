package com.algoce95.novaiptv.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.algoce95.novaiptv.R
import com.algoce95.novaiptv.core.di.AppContainer
import com.algoce95.novaiptv.core.di.vmFactory
import com.algoce95.novaiptv.core.theme.AppColors

/** Paridad con `LoginScreen` de Flutter (3 campos, foco D-pad, validación). */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val authViewModel: AuthViewModel = viewModel(factory = vmFactory { AuthViewModel() })
    val authState by authViewModel.state.collectAsState()

    var server by rememberSaveable { mutableStateOf(AppContainer.credentials.server) }
    var username by rememberSaveable { mutableStateOf(AppContainer.credentials.username) }
    var password by rememberSaveable { mutableStateOf(AppContainer.credentials.password) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val serverFocus = FocusRequester()
    val userFocus = FocusRequester()
    val passFocus = FocusRequester()
    val buttonFocus = FocusRequester()

    LaunchedEffect(Unit) { serverFocus.requestFocus() }
    LaunchedEffect(authState) {
        if (authState == AuthUiState.Authenticated) onLoggedIn()
    }

    fun submit() {
        submitted = true
        if (server.isBlank() || username.isBlank() || password.isBlank()) return
        authViewModel.login(server.trim(), username.trim(), password.trim())
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = AppColors.panel,
        unfocusedContainerColor = AppColors.panel,
        focusedBorderColor = AppColors.accent,
        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
        focusedLabelColor = AppColors.mint,
        unfocusedLabelColor = AppColors.mutedLabel,
        cursorColor = AppColors.mint,
        focusedSupportingTextColor = AppColors.mutedLabel,
    )

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(82.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "NOVA IPTV",
                        color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 1.1.sp,
            )
            Spacer(Modifier.height(26.dp))
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Servidor URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.7f)
                    .focusRequester(serverFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() }),
                colors = fieldColors,
                isError = submitted && server.isBlank(),
                supportingText = {
                    if (submitted && server.isBlank()) Text("Ingrese la URL del servidor")
                },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.7f)
                    .focusRequester(userFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() }),
                colors = fieldColors,
                isError = submitted && username.isBlank(),
                supportingText = {
                    if (submitted && username.isBlank()) Text("Ingrese el usuario")
                },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.7f)
                    .focusRequester(passFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = fieldColors,
                isError = submitted && password.isBlank(),
                supportingText = {
                    if (submitted && password.isBlank()) Text("Ingrese la contraseña")
                },
            )
            Spacer(Modifier.height(24.dp))
            if (authState is AuthUiState.Error) {
                Text(
                    text = (authState as AuthUiState.Error).message,
                    color = AppColors.expired,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(fraction = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
            }
            val loading = authState == AuthUiState.Loading
            Button(
                onClick = ::submit,
                enabled = !loading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
                modifier = Modifier.focusRequester(buttonFocus),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                color = Color.White,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("CONECTANDO...")
                } else {
                    Text("CONECTAR")
                }
            }
        }
    }
}
