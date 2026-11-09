package com.dialer.calllogmaker

import com.dialer.calllogmaker.ui.theme.GoogleSans
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Google's call button green is fixed, not part of the dynamic scheme.
private val CallGreen = Color(0xFF5BB974)
private val OnCallGreen = Color(0xFF202124)

private data class Key(val char: Char, val label: String, val sub: String? = null)

private val KEYS = listOf(
    Key('1', "1"), Key('2', "2", "ABC"), Key('3', "3", "DEF"),
    Key('4', "4", "GHI"), Key('5', "5", "JKL"), Key('6', "6", "MNO"),
    Key('7', "7", "PQRS"), Key('8', "8", "TUV"), Key('9', "9", "WXYZ"),
    Key('*', "∗"), Key('0', "0", "+"), Key('#', "#"),
)

private val DTMF = mapOf(
    '0' to ToneGenerator.TONE_DTMF_0, '1' to ToneGenerator.TONE_DTMF_1, '2' to ToneGenerator.TONE_DTMF_2,
    '3' to ToneGenerator.TONE_DTMF_3, '4' to ToneGenerator.TONE_DTMF_4, '5' to ToneGenerator.TONE_DTMF_5,
    '6' to ToneGenerator.TONE_DTMF_6, '7' to ToneGenerator.TONE_DTMF_7, '8' to ToneGenerator.TONE_DTMF_8,
    '9' to ToneGenerator.TONE_DTMF_9, '*' to ToneGenerator.TONE_DTMF_S, '#' to ToneGenerator.TONE_DTMF_P,
)

private const val SHEET_HEIGHT = 438 // dp, measured

private fun Context.launch(intent: Intent) {
    runCatching { startActivity(intent) } // no app to handle it → ignore
}

private fun Context.dial(number: String) = launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

@Composable
fun KeypadTab(logs: List<CallLog>, digits: String, onDigitsChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val tones = remember {
        val enabled = Settings.System.getInt(ctx.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
        if (enabled) runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 80) }.getOrNull() else null
    }
    DisposableEffect(Unit) { onDispose { tones?.release() } }

    fun press(c: Char) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        DTMF[c]?.let { tones?.startTone(it, 150) }
        onDigitsChange(digits + c)
    }

    // One entry per number, most recent first.
    val people = remember(logs) { logs.sortedByDescending { it.time }.distinctBy { it.number } }
    val query = digits.filter(Char::isDigit)
    val matches = if (digits.isEmpty()) people.filter { isSavedContact(it.name) }.take(8)
    else if (query.isEmpty()) emptyList()
    else people.filter { it.number.filter(Char::isDigit).contains(query) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = (SHEET_HEIGHT - 10).dp)) {
            if (digits.isEmpty()) item(key = "suggested") { Box(Modifier.animateItem()) { SuggestedHeader() } }
            itemsIndexed(matches, key = { _, p -> p.number }) { i, p ->
                Box(Modifier.animateItem()) { ContactCard(p, i, matches.size) { ctx.dial(p.number) } }
            }
            if (digits.isNotEmpty()) {
                item(key = "actions") {
                    Column(Modifier.animateItem().padding(top = if (matches.isEmpty()) 0.dp else 8.dp)) {
                        ActionRow(Icons.Outlined.PersonAddAlt, "Create new contact") {
                            ctx.launch(
                                Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                                    .putExtra(ContactsContract.Intents.Insert.PHONE, digits)
                            )
                        }
                        ActionRow(Icons.Outlined.PersonAddAlt, "Add to a contact") {
                            ctx.launch(
                                Intent(Intent.ACTION_INSERT_OR_EDIT)
                                    .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                    .putExtra(ContactsContract.Intents.Insert.PHONE, digits)
                            )
                        }
                        ActionRow(Icons.Outlined.Videocam, "Video call") { ctx.dial(digits) }
                        ActionRow(Icons.Outlined.ChatBubbleOutline, "Send a message") {
                            ctx.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(digits)}")))
                        }
                    }
                }
            }
        }
        // The dial pad slides up when the Keypad tab opens, like Google Phone.
        val sheetIn = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            sheetIn,
            enter = slideInVertically(tween(300)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { DialpadSheet(
            digits = digits,
            onKey = ::press,
            onPlus = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onDigitsChange(digits + '+')
            },
            onDelete = { onDigitsChange(digits.dropLast(1)) },
            onClear = { onDigitsChange("") },
            onAppend = { onDigitsChange(digits + it) },
            onCall = {
                // Like Google Phone: an empty dialer first recalls the last dialed number.
                if (digits.isEmpty()) logs.filter { it.type == CallType.OUTGOING }.maxByOrNull { it.time }
                    ?.let { onDigitsChange(it.number.filter { c -> c.isDigit() || c == '+' }) }
                else ctx.dial(digits)
            },
        ) }
    }
}

@Composable
private fun SuggestedHeader() {
    Text(
        "Suggested",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 32.dp, top = 13.dp, bottom = 15.dp),
    )
}

@Composable
private fun ContactCard(p: CallLog, index: Int, size: Int, onCall: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = if (index == size - 1) 0.dp else 2.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(groupShape(index, size))
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onCall)
            .padding(start = 16.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name.ifBlank { p.number }, color = cs.onSurface, fontSize = 16.sp, lineHeight = 24.sp, maxLines = 1)
            Text(
                "${p.label} ${p.number.filterNot(Char::isWhitespace)}",
                color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1,
            )
        }
        Icon(Icons.Outlined.Call, "Call", tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = cs.primary)
        Spacer(Modifier.width(28.dp))
        Text(text, color = cs.primary, fontSize = 16.sp, lineHeight = 24.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadSheet(
    digits: String,
    onKey: (Char) -> Unit,
    onPlus: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onAppend: (String) -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .height(SHEET_HEIGHT.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(cs.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Number display: overflow menu | digits | backspace
        Row(Modifier.height(55.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp, 55.dp), contentAlignment = Alignment.Center) {
                if (digits.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { menu = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.MoreVert, "More options", tint = cs.onSurfaceVariant) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem({ Text("Add 2-sec pause") }, onClick = { onAppend(","); menu = false })
                        DropdownMenuItem({ Text("Add wait") }, onClick = { onAppend(";"); menu = false })
                    }
                }
            }
            BasicText(
                formatDigits(digits),
                modifier = Modifier.weight(1f),
                style = TextStyle(color = cs.onSurface, fontFamily = GoogleSans, textAlign = TextAlign.Center, lineHeight = 44.sp),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 36.sp),
            )
            Box(
                Modifier
                    .size(56.dp, 55.dp)
                    .combinedClickable(
                        enabled = digits.isNotEmpty(),
                        onClick = onDelete,
                        onLongClick = onClear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace, "backspace",
                    tint = if (digits.isEmpty()) cs.onSurface.copy(alpha = 0.38f) else cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KEYS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        DialKey(
                            k,
                            Modifier.weight(1f),
                            onClick = { onKey(k.char) },
                            onLongClick = if (k.char == '0') onPlus else null,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Row(
            Modifier
                .height(56.dp)
                .clip(CircleShape)
                .background(CallGreen)
                .clickable(onClick = onCall)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Call, null, tint = OnCallGreen)
            Spacer(Modifier.width(8.dp))
            Text("Call", color = OnCallGreen, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(k: Key, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .height(65.dp)
            .clip(CircleShape)
            .background(cs.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(k.label, color = cs.onSurface, fontSize = 32.sp, fontFamily = GoogleSans, lineHeight = 40.sp)
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            when {
                k.char == '1' -> Icon(
                    Icons.Outlined.Voicemail, "voicemail", tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                k.sub != null -> Text(k.sub, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

private fun formatDigits(digits: String): String =
    if (digits.length < 4) digits
    else PhoneNumberUtils.formatNumber(digits, Locale.getDefault().country) ?: digits
