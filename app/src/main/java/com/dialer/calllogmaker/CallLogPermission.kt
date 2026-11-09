package com.dialer.calllogmaker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat

/** False once the user ticked "Don't ask again" (or denied twice): only app settings can grant it then. */
fun canAskCallLogAgain(ctx: Context) =
    (ctx as? Activity)?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CALL_LOG) } ?: false

fun openAppSettings(ctx: Context) = ctx.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
)

/** Shown before Android's own prompt: why call-log access is wanted and what it does / doesn't do. */
@Composable
fun CallLogRationaleDialog(onContinue: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(Icons.Outlined.History, null) },
        title = { Text("Show your call history?", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("To show the calls already on this phone, the app needs access to your call logs.")
                Reason(Icons.Outlined.History, "Your past calls appear in Home and on each contact’s page, alongside calls you add yourself.")
                Reason(Icons.Outlined.Lock, "Read-only: nothing in your phone’s call history is changed or deleted.")
                Reason(Icons.Outlined.CloudOff, "This app has no internet access, so your call history can’t be sent anywhere.")
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } },
    )
}

/** Shown after the user says no: what still works, and how to allow it later. */
@Composable
fun CallLogDeniedDialog(canAskAgain: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PhonelinkErase, null) },
        title = { Text("Call history not loaded", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Reason(Icons.Outlined.EditNote, "The app still works: you’ll only see calls you add yourself with the + button.")
                Reason(
                    Icons.Outlined.History,
                    if (canAskAgain) "You can load your history any time from Add call log → Import from phone."
                    else "Android won’t ask again. To load your history later, open App settings → Permissions → Call logs → Allow.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onRetry) { Text(if (canAskAgain) "Try again" else "Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Home banner while call-log access is missing: tapping Allow starts the explain → ask flow. */
@Composable
fun AllowCallLogCard(onAllow: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.secondaryContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, null, tint = cs.onSecondaryContainer)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Show your call history", color = cs.onSecondaryContainer, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
            Text("Allow access to see calls already on this phone", color = cs.onSecondaryContainer, fontSize = 14.sp, lineHeight = 20.sp)
        }
        TextButton(onClick = onAllow) { Text("Allow", color = cs.primary, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun Reason(icon: ImageVector, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = cs.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
