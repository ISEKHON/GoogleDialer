package com.dialer.calllogmaker

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CallType(val title: String) {
    INCOMING("Incoming"), OUTGOING("Outgoing"), MISSED("Missed"),
    REJECTED("Declined"), BLOCKED("Blocked"), VOICEMAIL("Voicemail");

    /** Only connected calls (and voicemails) have a length. */
    val hasDuration get() = this == INCOMING || this == OUTGOING || this == VOICEMAIL
}

data class CallLog(
    val id: Long,
    val name: String, // blank = unknown number
    val number: String,
    val label: String, // e.g. "Mobile", "India"
    val type: CallType,
    val time: Long, // epoch millis
    val durationSec: Int,
    val video: Boolean = false,
    val sim: Int = 0, // 0 = single SIM (no badge), 1 or 2
    val hd: Boolean = durationSec > 0 && type.hasDuration,
)

/** Imported rows get negative ids from the system row id: stable across imports, never clash with ours. */
fun importedId(systemRowId: Long) = -systemRowId

fun isImported(log: CallLog) = log.id < 0

/** Maps android.provider.CallLog.Calls.TYPE values (1..7). */
fun callTypeFromAndroid(type: Int): CallType = when (type) {
    2 -> CallType.OUTGOING
    3 -> CallType.MISSED
    4 -> CallType.VOICEMAIL
    5 -> CallType.REJECTED
    6 -> CallType.BLOCKED
    else -> CallType.INCOMING // 1 incoming, 7 answered on another device
}

/** [imported] calls not already in [existing] (matched by id). */
fun newImports(existing: List<CallLog>, imported: List<CallLog>): List<CallLog> {
    val ids = existing.mapTo(HashSet()) { it.id }
    return imported.filter { it.id !in ids }
}

/** [template] [times] times, each [gapMinutes] earlier than the previous; shows as "(n)" on Home. */
fun repeatCall(template: CallLog, times: Int, gapMinutes: Int): List<CallLog> =
    List(times.coerceIn(1, 99)) { i ->
        template.copy(id = template.id + i, time = template.time - i * gapMinutes * 60_000L)
    }

/**
 * [count] realistic-looking calls spread over the last 7 days. Names come from [people]
 * (name to number); about a third are unknown numbers.
 */
fun randomLogs(count: Int, now: Long, people: List<Pair<String, String>>, rnd: kotlin.random.Random): List<CallLog> {
    val types = listOf(
        CallType.INCOMING to 40, CallType.OUTGOING to 35, CallType.MISSED to 15,
        CallType.REJECTED to 5, CallType.BLOCKED to 2, CallType.VOICEMAIL to 3,
    )
    fun pickType(): CallType {
        var r = rnd.nextInt(types.sumOf { it.second })
        for ((t, w) in types) { if (r < w) return t; r -= w }
        return CallType.INCOMING
    }
    fun randomNumber() = "+91 9" + (1..4).joinToString("") { "${rnd.nextInt(10)}" } +
        " " + (1..5).joinToString("") { "${rnd.nextInt(10)}" }
    return List(count) { i ->
        val (name, number) = if (people.isNotEmpty() && rnd.nextInt(3) > 0) people[rnd.nextInt(people.size)]
        else "" to randomNumber()
        val type = pickType()
        val duration = when {
            !type.hasDuration -> 0
            type == CallType.VOICEMAIL -> 5 + rnd.nextInt(55)
            rnd.nextInt(5) == 0 -> 0 // rang out unanswered
            else -> 5 + rnd.nextInt(if (rnd.nextInt(6) == 0) 3600 else 600)
        }
        CallLog(
            id = now * 1000 + i,
            name = name, number = number,
            label = if (name.isBlank()) "India" else "Mobile",
            type = type,
            time = now - rnd.nextLong(7 * 86_400_000L),
            durationSec = duration,
            video = type.hasDuration && rnd.nextInt(10) == 0,
            sim = 0,
        )
    }
}

/** Consecutive calls with the same number and type, shown as one card with "(n)". */
data class CallGroup(val calls: List<CallLog>) {
    val first get() = calls.first()
}

fun sectionOf(time: Long, now: Long, zone: ZoneId): String {
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> "Older"
    }
}

/** Newest first, split into sections, consecutive same-number/same-type calls merged. */
fun groupLogs(logs: List<CallLog>, now: Long, zone: ZoneId): List<Pair<String, List<CallGroup>>> {
    val result = mutableListOf<Pair<String, MutableList<CallGroup>>>()
    for (log in logs.sortedByDescending { it.time }) {
        val section = sectionOf(log.time, now, zone)
        if (result.lastOrNull()?.first != section) result += section to mutableListOf()
        val groups = result.last().second
        val last = groups.lastOrNull()?.first
        if (last != null && last.number == log.number && last.type == log.type) {
            groups[groups.lastIndex] = CallGroup(groups.last().calls + log)
        } else {
            groups += CallGroup(listOf(log))
        }
    }
    return result
}

fun timeLabel(time: Long, now: Long, zone: ZoneId): String {
    val diffMin = (now - time) / 60_000
    val at = Instant.ofEpochMilli(time).atZone(zone)
    val fmt = { p: String -> at.format(DateTimeFormatter.ofPattern(p, Locale.getDefault())) }
    return when {
        diffMin in 0..0 -> "Just now"
        diffMin in 1..59 -> "$diffMin min ago"
        sectionOf(time, now, zone) == "Today" -> fmt("h:mm a")
        now - time < 7 * 86_400_000L -> fmt("EEE h:mm a")
        else -> fmt("d MMM yyyy, h:mm a")
    }
}

fun durationLabel(sec: Int): String = when {
    sec <= 0 -> "0 sec"
    sec < 60 -> "$sec sec"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60} min ${sec % 60} sec"
}

/** Contact card style: "1h 8s", "2m 15s", "45s". */
fun callTimeLabel(sec: Int): String =
    listOf(sec / 3600 to "h", sec % 3600 / 60 to "m", sec % 60 to "s")
        .filter { it.first > 0 }
        .joinToString(" ") { "${it.first}${it.second}" }
        .ifEmpty { "0s" }

/** Contact card "Recent activity" date: time today, weekday within a week, else "Jan 13". */
fun activityDateLabel(time: Long, now: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(time).atZone(zone)
    val fmt = { p: String -> at.format(DateTimeFormatter.ofPattern(p, Locale.getDefault())) }
    return when {
        sectionOf(time, now, zone) == "Today" -> fmt("h:mm a")
        now - time < 7 * 86_400_000L -> fmt("EEE")
        else -> fmt("MMM d")
    }
}

object CallLogStore {
    private const val KEY = "logs"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("call_logs", Context.MODE_PRIVATE)

    /** Time of the newest call already pulled from the phone; auto-sync only takes calls after it. */
    fun lastPhoneSync(ctx: Context) = prefs(ctx).getLong("lastPhoneSync", 0)
    fun setLastPhoneSync(ctx: Context, time: Long) = prefs(ctx).edit().putLong("lastPhoneSync", time).apply()

    /** True once the user said "Not now" or denied: the app stops asking at start (Add call log still offers it). */
    fun callLogPromptDone(ctx: Context) = prefs(ctx).getBoolean("callLogPromptDone", false)
    fun setCallLogPromptDone(ctx: Context) = prefs(ctx).edit().putBoolean("callLogPromptDone", true).apply()

    fun load(ctx: Context): List<CallLog> {
        val json = prefs(ctx).getString(KEY, null) ?: return seed(System.currentTimeMillis())
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    val type = CallType.valueOf(getString("type"))
                    val duration = getInt("durationSec")
                    CallLog(
                        getLong("id"), getString("name"), getString("number"), getString("label"),
                        type, getLong("time"), duration,
                        // Fields added later default so logs saved by older versions still load.
                        video = optBoolean("video", false),
                        sim = optInt("sim", 0),
                        hd = optBoolean("hd", duration > 0 && type.hasDuration),
                    )
                }
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun save(ctx: Context, logs: List<CallLog>) {
        val arr = JSONArray()
        logs.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("number", it.number)
                    .put("label", it.label).put("type", it.type.name)
                    .put("time", it.time).put("durationSec", it.durationSec)
                    .put("video", it.video).put("sim", it.sim).put("hd", it.hd)
            )
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** First-run sample data matching the reference screenshot. */
    private fun seed(now: Long): List<CallLog> {
        val zone = ZoneId.systemDefault()
        fun yesterday(h: Int, m: Int, s: Int = 0) =
            LocalDate.now(zone).minusDays(1).atTime(h, m, s).atZone(zone).toInstant().toEpochMilli()
        return listOf(
            CallLog(1, "Likely: Ladu", "+91 98765 43210", "India", CallType.INCOMING, now - 10 * 60_000, 95),
            CallLog(2, "Likely: Karan", "+91 98765 43210", "India", CallType.INCOMING, now - 25 * 60_000, 40),
            CallLog(3, "", "+91 98397 11722", "India", CallType.OUTGOING, yesterday(21, 30), 0),
            CallLog(4, "", "+91 90000 00001", "India", CallType.OUTGOING, yesterday(13, 2), 310),
            CallLog(5, "", "+91 90000 00002", "Mobile", CallType.INCOMING, yesterday(10, 55, 40), 128),
            CallLog(6, "Hero", "+91 90000 00003", "India", CallType.OUTGOING, yesterday(10, 55), 0),
        )
    }
}
