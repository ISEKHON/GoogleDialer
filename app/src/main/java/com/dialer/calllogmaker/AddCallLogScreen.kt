package com.dialer.calllogmaker

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val LABELS = listOf("Mobile", "Work", "Home", "India")
private val QUICK_DURATIONS = listOf(0 to "Not answered", 30 to "30s", 60 to "1m", 300 to "5m", 3600 to "1h")

/** Full-screen "Add call log" page in Google Phone's visual style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCallLogScreen(logs: List<CallLog>, onClose: () -> Unit, onSave: (List<CallLog>) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val start = remember { LocalDateTime.now(zone) }

    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var likely by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf("Mobile") }
    var type by rememberSaveable { mutableStateOf(CallType.INCOMING) }
    var video by rememberSaveable { mutableStateOf(false) }
    var hd by rememberSaveable { mutableStateOf(true) }
    var sim by rememberSaveable { mutableIntStateOf(0) }
    var date by rememberSaveable { mutableStateOf(start.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var seconds by rememberSaveable { mutableStateOf("") }
    var times by rememberSaveable { mutableIntStateOf(1) }
    var gap by rememberSaveable { mutableIntStateOf(2) }
    var randomCount by rememberSaveable { mutableIntStateOf(10) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var fromLog by remember { mutableStateOf(false) }

    var denied by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val callLogPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            denied = true // Android may stop showing the prompt; the button then opens app settings
            return@rememberLauncherForActivityResult
        }
        importing = true
        scope.launch {
            val all = try {
                withContext(Dispatchers.IO) { PhoneCallLog.read(ctx) }
            } finally {
                importing = false
            }
            all.maxOfOrNull { it.time }?.let { CallLogStore.setLastPhoneSync(ctx, it) }
            val add = newImports(logs, all)
            Toast.makeText(ctx, if (add.isEmpty()) "No new calls to import" else "Imported ${add.size} calls", Toast.LENGTH_SHORT).show()
            onSave(add)
        }
    }

    // Android's own contact picker: picking one phone number grants read access to just that row,
    // so no Contacts permission is needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0).orEmpty()
                        number = c.getString(1).orEmpty()
                        label = Phone.getTypeLabel(ctx.resources, c.getInt(2), c.getString(3)).toString()
                        likely = false
                    }
                }
        }
    }

    val secs = seconds.toIntOrNull() ?: 0
    val duration = if (type.hasDuration) (minutes.toIntOrNull() ?: 0) * 60 + secs else 0
    val valid = number.any(Char::isDigit) && secs <= 59
    val template = CallLog(
        id = System.currentTimeMillis() * 1000,
        name = name.trim().let { if (likely && it.isNotEmpty()) "Likely: $it" else it },
        number = number.trim(),
        label = label.trim().ifBlank { "Mobile" },
        type = type,
        time = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli(),
        durationSec = duration,
        video = video,
        sim = sim,
        hd = hd && duration > 0,
    )
    val batch = repeatCall(template, times, gap)

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.surfaceContainer)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Top bar
        Row(Modifier.height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "Close", tint = cs.onSurface) }
            Text(
                "Add call log", color = cs.onSurface, fontSize = 20.sp, lineHeight = 28.sp,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (valid) cs.primary else cs.onSurface.copy(alpha = 0.12f))
                    .clickable(enabled = valid) { onSave(batch) }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (times > 1) "Save $times" else "Save",
                    color = if (valid) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f),
                    fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Live preview of the Home card, so the result is visible before saving.
            Header("Preview")
            if (template.number.isEmpty()) {
                Text(
                    "Enter a number to see the call log card",
                    color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 4.dp),
                )
            } else {
                CallRow(CallGroup(batch), 0, 1, System.currentTimeMillis(), zone)
            }

            Header("Caller")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(template.name)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            name, { name = it }, label = { Text("Name (optional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            number, { v -> number = v.filter { it.isDigit() || it in "+ -()" }.take(20) },
                            label = { Text("Phone number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(Icons.Outlined.Contacts, "Contacts") {
                        try {
                            picker.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }
                    Box {
                        TonalPill(Icons.Outlined.History, "From call log", enabled = logs.isNotEmpty()) { fromLog = true }
                        DropdownMenu(fromLog, onDismissRequest = { fromLog = false }) {
                            logs.sortedByDescending { it.time }.distinctBy { it.number }.take(20).forEach { l ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(l.name.ifBlank { l.number })
                                            if (l.name.isNotBlank()) Text(l.number, color = cs.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                    },
                                    leadingIcon = { Avatar(l.name) },
                                    onClick = {
                                        likely = l.name.startsWith("Likely: ")
                                        name = l.name.removePrefix("Likely: ")
                                        number = l.number
                                        label = l.label
                                        fromLog = false
                                    },
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(name.isNotBlank(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    SwitchRow(null, "Show as caller ID guess", "Appears as “Likely: ${name.trim()}”", likely) { likely = it }
                }
                Label("Number label")
                ChipRow {
                    (LABELS + listOfNotNull(label.takeIf { it !in LABELS && it.isNotBlank() })).forEach {
                        Chip(it, label == it) { label = it }
                    }
                }
                OutlinedTextField(
                    label, { label = it.take(20) }, label = { Text("Custom label") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Header("Call")
            Card {
                Label("Type")
                ChipRow {
                    CallType.entries.forEach { t -> Chip(t.title, type == t, typeIcon(t)) { type = t } }
                }
                SwitchRow(Icons.Outlined.Videocam, "Video call", null, video) { video = it }
                SwitchRow(
                    Icons.Outlined.Hd, "HD voice", if (type.hasDuration) "Shows the HD badge when the call connected"
                    else "Only for connected calls", hd && type.hasDuration, enabled = type.hasDuration,
                ) { hd = it }
                Label("SIM")
                ChipRow {
                    listOf("Single SIM", "SIM 1", "SIM 2").forEachIndexed { i, s -> Chip(s, sim == i) { sim = i } }
                }
            }

            Header("When")
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(Icons.Outlined.CalendarToday, date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))) { pickDate = true }
                    TonalPill(Icons.Outlined.Schedule, time.format(DateTimeFormatter.ofPattern("h:mm a"))) { pickTime = true }
                }
                ChipRow(Modifier.padding(top = 8.dp)) {
                    fun setAgo(minutesAgo: Long) {
                        val t = LocalDateTime.now(zone).minusMinutes(minutesAgo)
                        date = t.toLocalDate(); time = t.toLocalTime().withSecond(0).withNano(0)
                    }
                    Chip("Now", false) { setAgo(0) }
                    Chip("15 min ago", false) { setAgo(15) }
                    Chip("1 hour ago", false) { setAgo(60) }
                    Chip("Yesterday", false) { setAgo(24 * 60) }
                    Chip("Last week", false) { setAgo(6 * 24 * 60) }
                }
            }

            AnimatedVisibility(type.hasDuration, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
              Column {
                Header(if (type == CallType.VOICEMAIL) "Message length" else "Call length")
                Card {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Minutes") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            seconds, { seconds = it.filter(Char::isDigit).take(2) }, label = { Text("Seconds") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                            isError = secs > 59, supportingText = if (secs > 59) ({ Text("0–59") }) else null,
                        )
                    }
                    ChipRow(Modifier.padding(top = 8.dp)) {
                        QUICK_DURATIONS.forEach { (s, text) ->
                            Chip(text, duration == s && (minutes.isNotEmpty() || seconds.isNotEmpty() || s == 0)) {
                                minutes = if (s >= 60) "${s / 60}" else ""
                                seconds = if (s % 60 > 0) "${s % 60}" else ""
                            }
                        }
                    }
                    Text(
                        if (duration == 0) "Shows as not answered" else "Shows as “Call time ${callTimeLabel(duration)}”",
                        color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
              }
            }

            Header("Repeat")
            Card {
                Stepper("Add this call", "$times ${if (times == 1) "time" else "times"}", times, 1..20) { times = it }
                AnimatedVisibility(times > 1, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Stepper("Gap between calls", "$gap min", gap, 1..120) { gap = it }
                }
                Text(
                    "Repeated calls from one number group into one card with a count, like “(${times.coerceAtLeast(2)})”.",
                    color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Shown only while call-log access is missing (it's asked for automatically at app start).
            if (!PhoneCallLog.hasPermission(ctx) || importing) {
                Header("Phone call history")
                Card {
                    Text(
                        "Load the calls already on this phone (the same history Google Phone shows). Read-only: nothing on the phone is changed.",
                        color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (importing) Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = cs.primary, strokeWidth = 3.dp, trackColor = cs.secondaryContainer)
                        Spacer(Modifier.width(16.dp))
                        Text("Importing your call history…", color = cs.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
                    } else TonalPill(Icons.Outlined.Download, if (denied) "Open settings to allow" else "Import from phone") {
                        if (denied) ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                        ) else callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
                    }
                }
            }

            Header("Quick fill")
            Card {
                Stepper("Random calls", "$randomCount", randomCount, 5..50, step = 5) { randomCount = it }
                Text(
                    "Mixed types, times over the last 7 days, using names already in your log plus unknown numbers.",
                    color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 4.dp),
                )
                TonalPill(Icons.Outlined.AutoAwesome, "Generate $randomCount calls") {
                    val people = logs.filter { it.name.isNotBlank() }.distinctBy { it.number }.map { it.name to it.number }
                    onSave(randomLogs(randomCount, System.currentTimeMillis(), people, Random.Default))
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (pickDate) {
        // DatePicker works in UTC-midnight millis.
        val state = rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Same look as the Home filter chips. */
@Composable
private fun Chip(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(cs.secondaryContainer)
                else Modifier.border(BorderStroke(1.dp, cs.outlineVariant), RoundedCornerShape(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, color = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant,
            fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TonalPill(icon: ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (enabled) cs.secondaryContainer else cs.onSurface.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) cs.onSecondaryContainer else cs.onSurface.copy(alpha = 0.38f)
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = tint, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector?, title: String, sub: String?, checked: Boolean,
    enabled: Boolean = true, onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f), fontSize = 16.sp, lineHeight = 24.sp)
            if (sub != null) Text(sub, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Stepper(title: String, value: String, current: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = cs.onSurface, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
        StepButton(Icons.Filled.Remove, "Less", current - step >= range.first) { onChange(current - step) }
        Text(
            value, color = cs.onSurface, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton(Icons.Filled.Add, "More", current + step <= range.last) { onChange(current + step) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) cs.secondaryContainer else cs.onSurface.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (enabled) cs.onSecondaryContainer else cs.onSurface.copy(alpha = 0.38f)) }
}
