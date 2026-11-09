package com.dialer.calllogmaker

import com.dialer.calllogmaker.ui.theme.GoogleSans
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.RingVolume
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Replica of Google Contacts' QuickContactActivity, which Google Phone opens from a call-log avatar.
// Sizes measured on a 1080x2400 / 440dpi device.

private fun Context.start(intent: Intent) {
    runCatching { startActivity(intent) }
}

private fun Context.dialNumber(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
private fun Context.smsNumber(number: String) = start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")))

private const val SETTINGS_KEY = "settings"
private const val RECENT_LIMIT = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactScreen(number: String, logs: List<CallLog>, onBack: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val calls = remember(logs, number) { logs.filter { it.number == number }.sortedByDescending { it.time } }
    val name = calls.firstOrNull()?.name.orEmpty()
    val saved = isSavedContact(name)
    val label = calls.firstOrNull()?.label ?: "Mobile"
    val title = if (saved) name else number

    // Not persisted: this replica has no real contact storage behind it.
    var starred by rememberSaveable { mutableStateOf(true) }
    var voicemail by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ringtone = remember { defaultRingtone(ctx) }
    // Recent activity shows the newest RECENT_LIMIT calls; "View more" expands the rest.
    var showAllCalls by rememberSaveable(number) { mutableStateOf(false) }
    val canExpand = calls.size > RECENT_LIMIT
    val shownCalls = if (showAllCalls) calls else calls.take(RECENT_LIMIT)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Header (banner/photo/name) scrolled away → title moves into the toolbar.
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.surfaceContainer)
            .statusBarsPadding()
    ) {
        // Toolbar, 64dp
        Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(4.dp))
            ToolbarButton(Icons.AutoMirrored.Outlined.ArrowBack, "Navigate up", cs.onSurface, onClick = onBack)
            // Title fades/slides into the toolbar as the big header scrolls away.
            val titleIn by animateFloatAsState(if (collapsed) 1f else 0f, tween(200), label = "title")
            Text(
                title,
                color = cs.onSurface, fontSize = 20.sp, lineHeight = 28.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .graphicsLayer {
                        alpha = titleIn
                        translationY = (1 - titleIn) * 12.dp.toPx()
                    },
            )
            if (saved) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (starred) cs.primaryContainer else Color.Transparent)
                            .clickable { starred = !starred },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            if (starred) "Remove from Favorites" else "Add to Favorites",
                            tint = if (starred) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        )
                    }
                }
                ToolbarButton(Icons.Outlined.Edit, "Edit contact") {
                    ctx.start(
                        Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.NAME, name)
                            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                    )
                }
            } else {
                ToolbarButton(Icons.Outlined.PersonAddAlt, "Add to contacts") { ctx.addContact(number) }
            }
            ToolbarButton(Icons.Outlined.ManageAccounts, "Scroll to Contact settings") {
                // header, verbs, [source chip], number card, [recent header + calls], settings
                val i = 2 + (if (saved) 1 else 0) + 1 +
                    (if (calls.isEmpty()) 0 else 1 + shownCalls.size + (if (canExpand) 1 else 0))
                scope.launch { listState.animateScrollToItem(i) }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!saved) SourceBanner()
                    Spacer(Modifier.height(16.dp))
                    if (saved) BigLetterAvatar(name) else BigUnknownAvatar(number)
                    Text(
                        title, color = cs.onSurface, fontSize = 36.sp, fontFamily = GoogleSans, lineHeight = 45.sp,
                        textAlign = TextAlign.Center, maxLines = 2,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                    )
                }
            }
            // Action buttons stay pinned under the toolbar while scrolling, like Google's collapsing header.
            stickyHeader(key = "verbs") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(cs.surfaceContainer)
                        .padding(horizontal = 8.dp),
                ) {
                    val m = Modifier.weight(1f)
                    VerbButton(Icons.Filled.Call, "Call", m) { ctx.dialNumber(number) }
                    VerbButton(Icons.Rounded.ChatBubble, "Message", m) { ctx.smsNumber(number) }
                    if (saved) {
                        VerbButton(Icons.Filled.Videocam, "Video", m) { ctx.dialNumber(number) }
                        VerbButton(Icons.Filled.Email, "Email", m, enabled = false) {}
                    } else {
                        VerbButton(Icons.Filled.PersonSearch, "Lookup", m) {
                            ctx.start(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", number))
                        }
                        VerbButton(Icons.Filled.PersonAddAlt1, "Add contact", m) { ctx.addContact(number) }
                    }
                }
            }
            if (saved) item {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp), contentAlignment = Alignment.Center) {
                    SourceChip("Added on " + Instant.ofEpochMilli(calls.minOf { it.time }).atZone(zone)
                        .format(DateTimeFormatter.ofPattern("dd/MM")))
                }
            }
            item {
                Spacer(Modifier.height(if (saved) 12.dp else 8.dp))
                NumberCard(number, if (saved) label else null) { ctx.smsNumber(number) }
            }
            if (calls.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    GroupHeaderCard("Recent activity")
                }
                items(shownCalls, key = { it.id }) { c ->
                    Box(Modifier.animateItem()) { ActivityRow(c, !canExpand && c == shownCalls.last(), now, zone) }
                }
                if (canExpand) item(key = "more") {
                    Box(Modifier.animateItem()) {
                        ViewMoreRow(showAllCalls, calls.size - RECENT_LIMIT) { showAllCalls = !showAllCalls }
                    }
                }
            }
            settingsSection(saved, ctx, name, number, ringtone, voicemail, { voicemail = it }, { confirmDelete = true })
            item {
                Spacer(Modifier.height(28.dp))
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
                Box(Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp), contentAlignment = Alignment.Center) {
                    Text("Help & feedback", color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                Spacer(Modifier.height(if (saved) 48.dp else 280.dp))
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this contact's call history?") },
        text = { Text("All call logs for $number will be removed from this app.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                onDelete()
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

private fun Context.addContact(number: String) = start(
    Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
)

@Composable
private fun ToolbarButton(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun SourceBanner() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(cs.surfaceContainerHigh),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Call, null, tint = Color(0xFF1A73E8), modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Text("Contact info from Phone", color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun BigLetterAvatar(name: String) {
    Box(
        Modifier
            .size(182.dp)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) { Text(name.take(1).uppercase(), color = Color(0xFFFFFADE), fontSize = 96.sp, fontFamily = GoogleSans, lineHeight = 96.sp) }
}

@Composable
private fun BigUnknownAvatar(number: String) {
    Box(
        Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(avatarColor(number)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Person, null, tint = Color(0xFFFFF8F8), modifier = Modifier.size(56.dp)) }
}

@Composable
private fun VerbButton(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.height(98.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(top = 8.dp, start = 4.dp, end = 4.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(if (enabled) cs.secondaryContainer else cs.onSurface.copy(alpha = 0.12f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (enabled) cs.onSecondaryContainer else cs.onSurface.copy(alpha = 0.38f))
        }
        Text(
            label,
            color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f),
            fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SourceChip(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(32.dp)
            .border(BorderStroke(1.dp, cs.outlineVariant), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Label, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = cs.onSurface, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NumberCard(number: String, sub: String?, onMessage: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .heightIn(min = if (sub == null) 56.dp else 68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceBright)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Call, null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(number, color = cs.onSurface, fontSize = 16.sp, lineHeight = 24.sp)
            if (sub != null) Text(sub, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
        }
        ToolbarButton(Icons.Outlined.ChatBubbleOutline, "Message $number", onClick = onMessage)
    }
}

@Composable
private fun GroupHeaderCard(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 4.dp))
            .background(cs.surfaceContainerHighest)
            .padding(start = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(text, color = cs.onSurface, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun ActivityRow(c: CallLog, last: Boolean, now: Long, zone: ZoneId) {
    val cs = MaterialTheme.colorScheme
    val title = when {
        c.type == CallType.VOICEMAIL -> "Voicemail ${callTimeLabel(c.durationSec)}"
        c.durationSec > 0 && c.type.hasDuration -> (if (c.video) "Video call time " else "Call time ") + callTimeLabel(c.durationSec)
        else -> "${c.type.title} ${if (c.video) "video call" else "call"}"
    }
    val arrow = typeIcon(c.type)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 2.dp)
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(4.dp, 4.dp, if (last) 16.dp else 4.dp, if (last) 16.dp else 4.dp))
            .background(cs.surfaceBright)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Call, null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = cs.onSurface, fontSize = 16.sp, lineHeight = 24.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(arrow, null, tint = if (c.type == CallType.MISSED) cs.error else cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(c.number, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        Text(activityDateLabel(c.time, now, zone), color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** Last card of Recent activity: toggles between the newest few calls and all of them. */
@Composable
private fun ViewMoreRow(expanded: Boolean, hidden: Int, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "chevron")
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 2.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp, 4.dp, 16.dp, 16.dp))
            .background(cs.surfaceBright)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "View less" else "View more ($hidden)",
            color = cs.primary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Outlined.ExpandMore, null, tint = cs.primary, modifier = Modifier.graphicsLayer { rotationZ = turn })
    }
}

private data class SettingRow(
    val icon: ImageVector,
    val title: String,
    val sub: String? = null,
    val danger: Boolean = false,
    val switch: Boolean? = null,
    val onClick: () -> Unit = {},
)

private fun LazyListScope.settingsSection(
    saved: Boolean, ctx: Context, name: String, number: String, ringtone: String,
    voicemail: Boolean, setVoicemail: (Boolean) -> Unit, onDelete: () -> Unit,
) {
    item(key = SETTINGS_KEY) {
        Text(
            if (saved) "Contact settings" else "Settings",
            color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 12.dp),
        )
    }
    val groups = if (saved) listOf(
        listOf(
            SettingRow(Icons.Outlined.RingVolume, "Contact ringtone", "Default ($ringtone)"),
            SettingRow(Icons.Outlined.FilterNone, "Reminders"),
            SettingRow(Icons.Outlined.GppGood, "Verify keys", "Verify end-to-end encryption with this contact"),
        ),
        listOf(
            SettingRow(Icons.Outlined.Share, "Share contact") {
                ctx.start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$name\n$number"), null))
            },
            SettingRow(Icons.Outlined.Widgets, "Add to home screen"),
            SettingRow(Icons.AutoMirrored.Outlined.Logout, "Move to another account"),
            SettingRow(Icons.Outlined.Voicemail, "Send to voicemail", switch = voicemail) { setVoicemail(!voicemail) },
            SettingRow(Icons.Outlined.Block, "Block numbers", danger = true),
            SettingRow(Icons.Outlined.Delete, "Delete", danger = true, onClick = onDelete),
        ),
    ) else listOf(listOf(SettingRow(Icons.Outlined.Block, "Block & report spam", danger = true)))

    groups.forEachIndexed { gi, rows ->
        rows.forEachIndexed { i, r ->
            item { SettingItem(r, i, rows.size, topGap = if (i == 0 && gi > 0) 12.dp else if (i == 0) 0.dp else 2.dp) }
        }
    }
    if (saved) item {
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Contact info from ", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, lineHeight = 24.sp)
            Text("Phone", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun SettingItem(r: SettingRow, index: Int, size: Int, topGap: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    val color = if (r.danger) cs.error else cs.onSurface
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = topGap)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(groupShape(index, size))
            .background(cs.surfaceContainerLow)
            .clickable(onClick = r.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(r.icon, null, tint = if (r.danger) cs.error else cs.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = color, fontSize = 16.sp, lineHeight = 24.sp)
            if (r.sub != null) Text(r.sub, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (r.switch != null) Switch(checked = r.switch, onCheckedChange = { r.onClick() })
    }
}

private fun defaultRingtone(ctx: Context): String = runCatching {
    val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
    RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx)
}.getOrNull() ?: "Ringtone"
