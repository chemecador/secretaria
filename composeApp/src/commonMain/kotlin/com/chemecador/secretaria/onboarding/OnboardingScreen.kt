package com.chemecador.secretaria.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chemecador.secretaria.PlatformBackHandler
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.login.AuthChoiceContent
import com.chemecador.secretaria.login.LoginViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.onboarding_free
import secretaria.composeapp.generated.resources.onboarding_free_detail
import secretaria.composeapp.generated.resources.onboarding_lists
import secretaria.composeapp.generated.resources.onboarding_lists_body
import secretaria.composeapp.generated.resources.onboarding_lists_title
import secretaria.composeapp.generated.resources.onboarding_next
import secretaria.composeapp.generated.resources.onboarding_no_ads
import secretaria.composeapp.generated.resources.onboarding_no_ads_detail
import secretaria.composeapp.generated.resources.onboarding_no_personal_data
import secretaria.composeapp.generated.resources.onboarding_no_personal_data_detail
import secretaria.composeapp.generated.resources.onboarding_notes
import secretaria.composeapp.generated.resources.onboarding_notes_body
import secretaria.composeapp.generated.resources.onboarding_notes_title
import secretaria.composeapp.generated.resources.onboarding_open_source
import secretaria.composeapp.generated.resources.onboarding_open_source_detail
import secretaria.composeapp.generated.resources.onboarding_reminders
import secretaria.composeapp.generated.resources.onboarding_reminders_body
import secretaria.composeapp.generated.resources.onboarding_reminders_title
import secretaria.composeapp.generated.resources.onboarding_skip
import secretaria.composeapp.generated.resources.onboarding_start
import secretaria.composeapp.generated.resources.onboarding_summary_title

/**
 * Las cinco paginas de la bienvenida, en orden. El acceso es la ultima a proposito: el formulario
 * dejo de ser el primer contacto con la app.
 */
enum class OnboardingPage {
    LISTS,
    NOTES,
    REMINDERS,
    SUMMARY,
    AUTH,
}

/**
 * Bienvenida de la primera apertura: tres pantallas de valor, un resumen y el acceso.
 *
 * [onCompleted] se dispara al llegar a [OnboardingPage.AUTH], no al iniciar sesion: quien ha
 * llegado hasta ahi ya ha visto (o saltado) la explicacion, y repetirsela si cierra la app antes
 * de decidir como entrar seria castigarle por dudar.
 */
@Composable
fun OnboardingScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onGoogleLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    startPage: OnboardingPage = OnboardingPage.LISTS,
) {
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState(initialPage = startPage.ordinal) { pages.size }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.first { it == OnboardingPage.AUTH.ordinal }
        onCompleted()
    }

    // Deshabilitado en la primera pagina para que atras cierre la app, como en cualquier inicio.
    PlatformBackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    fun goTo(page: Int) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(page.coerceIn(pages.indices))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { index ->
            when (pages[index]) {
                OnboardingPage.LISTS -> ValuePage(
                    mockup = Res.drawable.onboarding_lists,
                    title = Res.string.onboarding_lists_title,
                    body = Res.string.onboarding_lists_body,
                )

                OnboardingPage.NOTES -> ValuePage(
                    mockup = Res.drawable.onboarding_notes,
                    title = Res.string.onboarding_notes_title,
                    body = Res.string.onboarding_notes_body,
                )

                OnboardingPage.REMINDERS -> ValuePage(
                    mockup = Res.drawable.onboarding_reminders,
                    title = Res.string.onboarding_reminders_title,
                    body = Res.string.onboarding_reminders_body,
                )

                OnboardingPage.SUMMARY -> SummaryPage()

                OnboardingPage.AUTH -> AuthChoiceContent(
                    viewModel = viewModel,
                    onLoginSuccess = onLoginSuccess,
                    onGoogleLogin = onGoogleLogin,
                    onEmailLogin = onEmailLogin,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        OnboardingFooter(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            onGoToPage = ::goTo,
            onSkip = { goTo(OnboardingPage.AUTH.ordinal) },
            onNext = { goTo(pagerState.currentPage + 1) },
        )
    }
}

/**
 * Los puntos y las acciones no viajan con el pager, se quedan quietos debajo. El hueco de las
 * acciones mide siempre lo mismo aunque en la pagina de acceso este vacio: si encogiera a mitad de
 * un swipe, el contenido de arriba pegaria un salto.
 */
@Composable
private fun OnboardingFooter(
    pageCount: Int,
    currentPage: Int,
    onGoToPage: (Int) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .padding(bottom = 10.dp),
    ) {
        PageIndicator(
            pageCount = pageCount,
            currentPage = currentPage,
            onGoToPage = onGoToPage,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (OnboardingPage.entries[currentPage]) {
                OnboardingPage.LISTS,
                OnboardingPage.NOTES,
                OnboardingPage.REMINDERS,
                -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onSkip,
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.onboarding_skip),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    PrimaryPillButton(
                        text = stringResource(Res.string.onboarding_next),
                        onClick = onNext,
                        height = 52.dp,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                OnboardingPage.SUMMARY -> PrimaryPillButton(
                    text = stringResource(Res.string.onboarding_start),
                    onClick = onNext,
                    height = 56.dp,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth(),
                )

                // En el acceso solo se ven los puntos: las acciones son los propios botones.
                OnboardingPage.AUTH -> Unit
            }
        }
    }
}

@Composable
private fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    height: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(height / 2),
        colors = ButtonDefaults.buttonColors(
            containerColor = SecretariaTopBarColor,
            contentColor = SecretariaTopBarContentColor,
        ),
        modifier = modifier.height(height),
    ) {
        Text(text = text, fontSize = fontSize, fontWeight = FontWeight.Medium)
    }
}

/** Cada punto lleva una zona de toque mucho mayor que el circulo, que solo mide 7 dp. */
@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    onGoToPage: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IndicatorRowHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            val color by animateColorAsState(
                targetValue = if (page == currentPage) {
                    SecretariaTopBarColor
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
                },
                animationSpec = tween(durationMillis = 220),
            )
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .width(DotSize + DotGap)
                    .height(IndicatorRowHeight)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onGoToPage(page) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun ValuePage(
    mockup: DrawableResource,
    title: StringResource,
    body: StringResource,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // El mockup cede altura antes que el texto: en una pantalla baja encoge, no recorta.
        Image(
            painter = painterResource(mockup),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = MockupHeight)
                .aspectRatio(MockupAspectRatio, matchHeightConstraintsFirst = true)
                .shadow(18.dp, MockupShape)
                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), MockupShape),
        )
        Text(
            text = stringResource(title),
            fontSize = 27.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.27).sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 34.dp),
        )
        Text(
            text = stringResource(body),
            fontSize = 15.5.sp,
            lineHeight = 23.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SummaryPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .padding(top = 50.dp),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_summary_title),
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.27).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SummaryItem(Res.string.onboarding_free, Res.string.onboarding_free_detail)
            SummaryItem(Res.string.onboarding_no_ads, Res.string.onboarding_no_ads_detail)
            SummaryItem(Res.string.onboarding_open_source, Res.string.onboarding_open_source_detail)
            SummaryItem(
                Res.string.onboarding_no_personal_data,
                Res.string.onboarding_no_personal_data_detail,
            )
        }
    }
}

@Composable
private fun SummaryItem(
    title: StringResource,
    detail: StringResource,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = SecretariaTopBarColor,
            modifier = Modifier.size(21.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(title),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(detail),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val MockupHeight = 380.dp
private const val MockupAspectRatio = 214f / 380f
private val MockupShape = RoundedCornerShape(22.dp)
private val DotSize = 7.dp
private val DotGap = 7.dp
private val IndicatorRowHeight = 37.dp
