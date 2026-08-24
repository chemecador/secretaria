package com.chemecador.secretaria.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chemecador.secretaria.SecretariaMutedTextColor
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.app_logo
import secretaria.composeapp.generated.resources.ic_google
import secretaria.composeapp.generated.resources.onboarding_auth_title
import secretaria.composeapp.generated.resources.onboarding_guest_disclaimer
import secretaria.composeapp.generated.resources.onboarding_login_email
import secretaria.composeapp.generated.resources.onboarding_login_google
import secretaria.composeapp.generated.resources.onboarding_login_guest

/**
 * La eleccion de acceso: Google, email o sin cuenta. Es la ultima pagina de la bienvenida y
 * tambien la pantalla que ve quien ya la vio y vuelve sin sesion, por eso el contenido vive en
 * [AuthChoiceContent] y aqui solo se le pone el fondo de pantalla completa.
 */
@Composable
fun AuthChoiceScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onGoogleLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthChoiceContent(
        viewModel = viewModel,
        onLoginSuccess = onLoginSuccess,
        onGoogleLogin = onGoogleLogin,
        onEmailLogin = onEmailLogin,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 10.dp),
    )
}

/**
 * Sin cuenta se puede escribir, pero no compartir, y por eso "Empezar sin cuenta" queda abajo y
 * con el aviso delante en vez de al lado de Google: es una salida, no una tercera opcion igual.
 */
@Composable
internal fun AuthChoiceContent(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onGoogleLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoginSuccess()
    }

    Column(
        modifier = modifier
            .padding(horizontal = 26.dp)
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(66.dp),
        )
        Text(
            text = stringResource(Res.string.onboarding_auth_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onGoogleLogin,
                enabled = !state.isLoading,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecretariaTopBarColor,
                    contentColor = SecretariaTopBarContentColor,
                    disabledContainerColor = SecretariaTopBarColor.copy(alpha = 0.4f),
                    disabledContentColor = SecretariaTopBarContentColor.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.onboarding_login_google),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            OutlinedButton(
                onClick = onEmailLogin,
                enabled = !state.isLoading,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.onboarding_login_email),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        AuthFeedback(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_guest_disclaimer),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = SecretariaMutedTextColor,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = { viewModel.loginAsGuest() },
                enabled = !state.isLoading,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_login_guest),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Progreso y error comparten hueco: nunca coinciden y asi el bloque de abajo no da saltos. */
@Composable
internal fun AuthFeedback(
    state: LoginState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            state.error?.let { error ->
                Text(
                    text = stringResource(error.toStringRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
