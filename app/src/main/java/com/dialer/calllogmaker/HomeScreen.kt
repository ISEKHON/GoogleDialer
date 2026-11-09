package com.dialer.calllogmaker

import com.dialer.calllogmaker.ui.theme.GoogleSans
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined._3p
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.calllogmaker.ui.theme.GoogleDialerTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// All sizes below were measured from Google Phone on a 1080x2400 / 440dpi device.

// Contact avatar colors are fixed (not themed). Order keeps the screenshot's colors for its names.
private val AvatarColors = listOf(
    Color(0xFF5629A4), Color(0xFF8D0053), Color(0xFF753403), Color(0xFF004A77),
    Color(0xFF8A1A16), Color(0xFF00504F), Color(0xFF00522C),
)

private val FILTERS = listOf("All", "Missed", "Contacts", "Non-spam", "Spam")

/** "Likely: X" is a caller-ID guess, not a saved contact. */
internal fun isSavedContact(name: String) = name.isNotBlank() && !name.startsWith("Likely:")

/** Grouped list cards: 16dp on a group's outer corners, 4dp between items. */
internal fun groupShape(index: Int, size: Int): Shape {
    val top = if (index == 0) 16.dp else 4.dp
    val bottom = if (index == size - 1) 16.dp else 4.dp
    return RoundedCornerShape(top, top, bottom, bottom)
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    var logs by remember { mutableStateOf(CallLogStore.load(ctx)) }
    fun update(new: List<CallLog>) {
        logs = new
        CallLogStore.save(ctx, new)
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var digits by rememberSaveable { mutableStateOf("") }
    // Number whose contact card is open (Google opens Contacts' QuickContactActivity here).
    var contact by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    // Phone call history: explain, then ask (until the user says no); when allowed, pull in calls newer
    // than the last sync on every resume — also catches access granted from app settings.
    var syncTick by remember { mutableIntStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var permissionDialog by rememberSaveable { mutableStateOf<String?>(null) } // "why" | "denied"
    var canAskAgain by remember { mutableStateOf(true) }
    var hasCallLog by remember { mutableStateOf(PhoneCallLog.hasPermission(ctx)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCallLog = granted
        if (granted) syncTick++
        else {
            canAskAgain = canAskCallLogAgain(ctx)
            permissionDialog = "denied"
        }
    }
    LifecycleResumeEffect(Unit) {
        hasCallLog = PhoneCallLog.hasPermission(ctx)
        if (hasCallLog) {
            permissionDialog = null
            syncTick++
        } else if (permissionDialog == null && !CallLogStore.callLogPromptDone(ctx)) permissionDialog = "why"
        onPauseOrDispose { }
    }
    when (permissionDialog) {
        "why" -> CallLogRationaleDialog(
            onContinue = {
                permissionDialog = null
                permission.launch(Manifest.permission.READ_CALL_LOG)
            },
            onNotNow = {
                CallLogStore.setCallLogPromptDone(ctx)
                permissionDialog = null
            },
        )
        "denied" -> CallLogDeniedDialog(
            canAskAgain,
            onRetry = {
                permissionDialog = null
                if (canAskAgain) permission.launch(Manifest.permission.READ_CALL_LOG) else openAppSettings(ctx)
            },
            onDismiss = {
                CallLogStore.setCallLogPromptDone(ctx)
                permissionDialog = null
            },
        )
    }
    LaunchedEffect(syncTick) {
        if (syncTick == 0) return@LaunchedEffect
        val since = CallLogStore.lastPhoneSync(ctx)
        // Only the first, full import shows progress; later syncs are tiny and would just flicker.
        syncing = since == 0L
        try {
            val fresh = withContext(Dispatchers.IO) { PhoneCallLog.read(ctx, since) }
            val add = newImports(logs, fresh)
            if (add.isNotEmpty()) update(logs + add)
            fresh.maxOfOrNull { it.time }?.let { CallLogStore.setLastPhoneSync(ctx, it) }
        } finally {
            syncing = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            Modifier
                .weight(1f)
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(90)))
                },
                label = "tab",
            ) { t ->
                if (t == 0) CallHistoryTab(logs, syncing, hasCallLog, onAllowCallLog = { permissionDialog = "why" }, ::update, onOpenContact = { contact = it }, onAdd = { adding = true })
                else KeypadTab(logs, digits, onDigitsChange = { digits = it })
            }
        }
        BottomNav(tab) { tab = it }
    }

    // Drawn over Home (kept composed underneath, so its scroll position survives).
    // AnimatedContent keeps the old page on screen while it animates out.
    BackHandler(enabled = contact != null) { contact = null }
    AnimatedContent(
        targetState = contact,
        transitionSpec = {
            // Material shared-axis X: slide a little + fade.
            if (targetState != null) (slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(300))).togetherWith(fadeOut(tween(150)))
            else fadeIn(tween(0)).togetherWith(slideOutHorizontally(tween(250)) { it / 6 } + fadeOut(tween(250)))
        },
        label = "contact",
    ) { number ->
        if (number != null) ContactScreen(
            number, logs,
            onBack = { contact = null },
            onDelete = {
                update(logs.filterNot { it.number == number })
                contact = null
            },
        )
    }
    BackHandler(enabled = adding) { adding = false }
    AnimatedVisibility(
        visible = adding,
        enter = slideInVertically(tween(350)) { it / 5 } + fadeIn(tween(250)),
        exit = slideOutVertically(tween(250)) { it / 5 } + fadeOut(tween(200)),
    ) {
        AddCallLogScreen(
            logs,
            onClose = { adding = false },
            onSave = {
                update(logs + it)
                adding = false
            },
        )
    }
}

@Composable
private fun CallHistoryTab(
    logs: List<CallLog>, syncing: Boolean, hasCallLog: Boolean, onAllowCallLog: () -> Unit, update: (List<CallLog>) -> Unit, onOpenContact: (String) -> Unit, onAdd: () -> Unit,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val visible = when (filter) {
        1 -> logs.filter { it.type == CallType.MISSED }
        2 -> logs.filter { isSavedContact(it.name) }
        3 -> logs.filterNot { it.type == CallType.BLOCKED }
        4 -> logs.filter { it.type == CallType.BLOCKED } // blocked calls stand in for spam
        else -> logs
    }
    val sections = groupLogs(visible, now, zone)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            item { SearchBar() }
            item { FilterChips(filter) { filter = it } }
            item { FavoritesRow() }
            // Way back in after "Not now" / deny; disappears as soon as access is granted.
            item(key = "allow") {
                AnimatedVisibility(!hasCallLog, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    AllowCallLogCard(onAllowCallLog)
                }
            }
            item(key = "loading") {
                AnimatedVisibility(syncing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    LoadingCard("Loading your call history…")
                }
            }
            sections.forEachIndexed { si, (title, groups) ->
                item(key = "h_$title") { Box(Modifier.animateItem()) { SectionHeader(title, top = if (si == 0) 37.dp else 27.dp) } }
                itemsIndexed(groups, key = { _, g -> g.first.id }) { i, g ->
                    Box(Modifier.animateItem()) { CallRow(
                        g, i, groups.size, now, zone,
                        onAvatarClick = { onOpenContact(g.first.number) },
                        expanded = expandedId == g.first.id,
                        onClick = { expandedId = if (expandedId == g.first.id) null else g.first.id },
                        onDelete = {
                            val ids = g.calls.map { it.id }.toSet()
                            update(logs.filterNot { it.id in ids })
                        },
                    ) }
                }
            }
            if (sections.isEmpty() && !syncing) item {
                Text(
                    "No calls",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Not in Google's design: entry point for adding call logs by hand.
        FloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Icon(Icons.Filled.Add, "Add call log") }
    }
}

/** Spinner + message in a rounded card, shown while the phone's call history is being read. */
@Composable
internal fun LoadingCard(text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(start = 12.dp, end = 12.dp, top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = cs.primary, strokeWidth = 3.dp, trackColor = cs.secondaryContainer)
        Spacer(Modifier.width(16.dp))
        Text(text, color = cs.onSurface, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun SearchBar() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Menu, "Open navigation drawer", tint = cs.onSurface)
        }
        Text(
            "Search contacts", color = cs.onSurfaceVariant, fontSize = 16.sp, lineHeight = 24.sp,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, "Start voice search", tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterChips(selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(top = 16.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FILTERS.forEachIndexed { i, label ->
            val isSel = i == selected
            val bg by animateColorAsState(if (isSel) cs.secondaryContainer else cs.secondaryContainer.copy(alpha = 0f), label = "chipBg")
            val border by animateColorAsState(if (isSel) cs.outlineVariant.copy(alpha = 0f) else cs.outlineVariant, label = "chipBorder")
            val fg by animateColorAsState(if (isSel) cs.onSecondaryContainer else cs.onSurfaceVariant, label = "chipFg")
            val pad by animateDpAsState(if (isSel) 24.dp else 16.dp, label = "chipPad")
            val minW by animateDpAsState(if (isSel) 72.dp else 0.dp, label = "chipMinW")
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(BorderStroke(1.dp, border), RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .widthIn(min = minW)
                    .padding(horizontal = pad),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = fg,
                    fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FavoritesRow() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 8.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Favorites", color = cs.primary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Outlined.KeyboardArrowDown, null, tint = cs.primary,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .height(32.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHighest)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("View contacts", color = cs.onSurface, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun SectionHeader(text: String, top: Dp) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 32.dp, top = top, bottom = 10.dp),
    )
}

internal fun typeIcon(type: CallType) = when (type) {
    CallType.INCOMING -> Icons.AutoMirrored.Outlined.CallReceived
    CallType.OUTGOING -> Icons.AutoMirrored.Outlined.CallMade
    CallType.MISSED -> Icons.AutoMirrored.Outlined.CallMissed
    CallType.REJECTED -> Icons.Outlined.DoNotDisturbOn
    CallType.BLOCKED -> Icons.Outlined.Block
    CallType.VOICEMAIL -> Icons.Outlined.Voicemail
}

/** Small SIM badge with the slot number, shown on dual-SIM logs. */
@Composable
internal fun SimBadge(sim: Int, tint: Color) {
    Box(Modifier.padding(start = 4.dp).size(16.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.SimCard, "SIM $sim", tint = tint, modifier = Modifier.size(16.dp))
        Text("$sim", color = tint, fontSize = 8.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
internal fun CallRow(
    g: CallGroup, index: Int, size: Int, now: Long, zone: ZoneId,
    onAvatarClick: () -> Unit = {},
    expanded: Boolean = false, onClick: () -> Unit = {}, onDelete: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val e = g.first
    val missed = e.type == CallType.MISSED
    val nameColor = if (missed) cs.error else cs.onSurface
    val subColor = if (missed) cs.error else cs.onSurfaceVariant
    val askBusiness = !isSavedContact(e.name)
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = if (index == size - 1) 0.dp else 2.dp)
            .fillMaxWidth()
            .clip(groupShape(index, size))
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = if (askBusiness || expanded) 16.dp else 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(e.name, Modifier.clip(CircleShape).clickable(onClick = onAvatarClick))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.name.ifBlank { e.number }, color = nameColor, fontSize = 16.sp, lineHeight = 24.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (e.hd) {
                        Icon(
                            Icons.Outlined.Hd, null, tint = cs.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 6.dp, end = 6.dp)
                                .size(16.dp),
                        )
                    }
                    if (g.calls.size > 1) {
                        Text(if (e.hd) "(${g.calls.size})" else " (${g.calls.size})", color = nameColor, fontSize = 16.sp, lineHeight = 24.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(typeIcon(e.type), null, tint = subColor, modifier = Modifier.size(16.dp))
                    if (e.video) Icon(
                        Icons.Outlined.Videocam, "Video call", tint = subColor,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                    )
                    if (e.sim > 0) SimBadge(e.sim, subColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${e.label} • ${timeLabel(e.time, now, zone)}",
                        color = subColor, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1,
                    )
                }
            }
            Box(Modifier.size(48.dp, 40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Call, "Call", tint = cs.onSurfaceVariant)
            }
        }
        if (askBusiness) {
            Row(
                Modifier
                    .padding(start = 56.dp, top = 17.dp)
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainer)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined._3p, null, tint = cs.onSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Was this a business?", color = cs.onSurface, fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
        AnimatedVisibility(
            expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
        ) { CallDetails(g, zone, onDelete) }
    }
}

@Composable
private fun CallDetails(g: CallGroup, zone: ZoneId, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a")
    Column(Modifier.padding(start = 56.dp, top = 14.dp)) {
        Text(g.first.number, color = cs.onSurface, fontSize = 14.sp)
        g.calls.forEach { c ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(typeIcon(c.type), null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(Instant.ofEpochMilli(c.time).atZone(zone).format(fmt), color = cs.onSurfaceVariant, fontSize = 13.sp)
                    Text(
                        when {
                            !c.type.hasDuration -> "${c.type.title} call"
                            c.durationSec == 0 -> "Not answered"
                            else -> durationLabel(c.durationSec)
                        },
                        color = cs.onSurfaceVariant, fontSize = 13.sp,
                    )
                }
            }
        }
        TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete")
        }
    }
}

@Composable
internal fun avatarColor(key: String) = AvatarColors[Math.floorMod(key.hashCode(), AvatarColors.size)]

@Composable
internal fun Avatar(name: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val known = isSavedContact(name)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (known) avatarColor(name) else cs.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (known) Text(name.take(1).uppercase(), color = Color(0xFFFAF8F8), fontSize = 26.sp, fontFamily = GoogleSans)
        else Icon(Icons.Filled.Person, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerLow)
                .padding(horizontal = 28.dp),
        ) {
            NavItem(
                if (selected == 0) Icons.Filled.Home else Icons.Outlined.Home,
                "Home", selected == 0, Modifier.weight(1f),
            ) { onSelect(0) }
            NavItem(Icons.Filled.Dialpad, "Keypad", selected == 1, Modifier.weight(1f)) { onSelect(1) }
        }
        // Google Phone paints the gesture-bar area one tone lighter than the nav bar.
        Spacer(
            Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh)
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
        )
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // M3 nav indicator: the pill grows out from the center when selected.
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "navIndicator")
    val iconTint by animateColorAsState(if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant, label = "navIcon")
    val labelColor by animateColorAsState(if (selected) cs.secondary else cs.onSurfaceVariant, label = "navLabel")
    Column(
        modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(width = 56.dp, height = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = progress
                        alpha = progress
                    }
                    .clip(CircleShape)
                    .background(cs.secondaryContainer)
            )
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            color = labelColor,
            fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview(widthDp = 393, heightDp = 873)
@Composable
private fun HomeScreenPreview() = GoogleDialerTheme(darkTheme = true, dynamicColor = false) { HomeScreen() }
