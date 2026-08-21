package com.chemecador.secretaria.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chemecador.secretaria.MainActivity
import com.chemecador.secretaria.R
import com.chemecador.secretaria.format.DateTimeFormat
import com.chemecador.secretaria.messaging.NotificationOpenRemindersIntent
import com.chemecador.secretaria.reminders.isOverdue
import kotlin.time.Clock

/**
 * Espejo de la pantalla Recordatorios: los pendientes en el orden manual del usuario, con lo
 * vencido resaltado pero sin recolocar, que es la regla del resto de la app.
 *
 * Pinta siempre desde la copia local, sin esperar a la red: el lanzador quiere una vista ya.
 */
class RemindersWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = RemindersWidgetStore.get(context)
        provideContent {
            val snapshot by store.snapshots.collectAsState()
            RemindersWidgetContent(snapshot)
        }
    }
}

@Composable
private fun RemindersWidgetContent(snapshot: RemindersWidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background)),
    ) {
        WidgetHeader(pendingCount = snapshot.items.size)
        when {
            !snapshot.isSignedIn -> WidgetMessage(R.string.widget_reminders_signed_out)
            snapshot.updatedAtEpochMillis == null -> WidgetMessage(R.string.widget_reminders_loading)
            snapshot.items.isEmpty() -> WidgetMessage(R.string.widget_reminders_empty)
            else -> ReminderList(snapshot.items)
        }
    }
}

@Composable
private fun WidgetHeader(pendingCount: Int) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_header_background))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_reminders_title, pendingCount),
            style = TextStyle(
                color = ColorProvider(WidgetColors.OnTopBar),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(openRemindersIntent(context))),
        )
        WidgetIconButton(
            iconRes = R.drawable.ic_widget_refresh,
            contentDescriptionRes = R.string.widget_reminders_refresh,
            tint = WidgetColors.OnTopBar,
            modifier = GlanceModifier.clickable(actionRunCallback<RefreshRemindersAction>()),
        )
    }
}

@Composable
private fun ReminderList(items: List<RemindersWidgetItem>) {
    val context = LocalContext.current
    val format = context.widgetDateTimeFormat()
    // Lo vencido se decide contra el reloj de ahora, no contra el momento en que se guardo la
    // copia: una copia de anoche tiene que resaltar lo que ha vencido esta manana.
    val now = Clock.System.now()

    LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(vertical = 4.dp)) {
        items(
            items = items,
            itemId = { item -> "${item.ownerId}/${item.id}".hashCode().toLong() },
        ) { item ->
            ReminderRow(item = item, format = format, isOverdue = item.due?.isOverdue(now) == true)
        }
    }
}

@Composable
private fun ReminderRow(
    item: RemindersWidgetItem,
    format: DateTimeFormat,
    isOverdue: Boolean,
) {
    val context = LocalContext.current
    val due = item.due

    Box(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.widget_item_background))
                .padding(end = 8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_check_box_blank,
                contentDescriptionRes = R.string.widget_reminder_complete,
                tint = WidgetColors.Primary,
                modifier = GlanceModifier.clickable(
                    actionRunCallback<CompleteReminderAction>(
                        CompleteReminderAction.parametersFor(item),
                    ),
                ),
            )
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(vertical = 8.dp)
                    .clickable(actionStartActivity(openRemindersIntent(context))),
            ) {
                Text(
                    text = item.text,
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.OnSurface),
                        fontSize = 14.sp,
                    ),
                    maxLines = 2,
                )
                if (due != null || item.isShared) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        if (item.isShared) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_group),
                                contentDescription = context.getString(
                                    R.string.widget_reminder_shared,
                                ),
                                colorFilter = ColorFilter.tint(
                                    ColorProvider(WidgetColors.OnSurfaceVariant),
                                ),
                                modifier = GlanceModifier.size(13.dp),
                            )
                            Spacer(modifier = GlanceModifier.width(6.dp))
                        }
                        if (due != null) {
                            ReminderDueChip(
                                label = context.formatWidgetDue(due, format),
                                hasTime = due.time != null,
                                isOverdue = isOverdue,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Mismos colores que `ReminderDueChip` en la pantalla: rojo suave solo cuando ya ha vencido. */
@Composable
private fun ReminderDueChip(label: String, hasTime: Boolean, isOverdue: Boolean) {
    val background = if (isOverdue) {
        R.drawable.widget_due_chip_overdue_background
    } else {
        R.drawable.widget_due_chip_background
    }
    val contentColor = if (isOverdue) {
        WidgetColors.OnErrorContainer
    } else {
        WidgetColors.OnSecondaryContainer
    }

    Row(
        modifier = GlanceModifier
            .background(ImageProvider(background))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(
                if (hasTime) R.drawable.ic_widget_schedule else R.drawable.ic_widget_calendar,
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(contentColor)),
            modifier = GlanceModifier.size(12.dp),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = label,
            style = TextStyle(color = ColorProvider(contentColor), fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetMessage(messageRes: Int) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(actionStartActivity(openRemindersIntent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(messageRes),
            style = TextStyle(
                color = ColorProvider(WidgetColors.OnSurfaceVariant),
                fontSize = 13.sp,
            ),
        )
    }
}

/** 40 dp de area tactil con el icono a 20: por debajo de eso el toque falla en el lanzador. */
@Composable
private fun WidgetIconButton(
    iconRes: Int,
    contentDescriptionRes: Int,
    tint: Color,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = context.getString(contentDescriptionRes),
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
            modifier = GlanceModifier.size(20.dp),
        )
    }
}

/** Reutiliza el mismo camino que el aviso de vencimiento: accion propia leida en `MainActivity`. */
private fun openRemindersIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = NotificationOpenRemindersIntent.ACTION_OPEN_REMINDERS
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        putExtra(NotificationOpenRemindersIntent.EXTRA_OPEN_REMINDERS, true)
    }
