package com.dialer.calllogmaker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CallLogTest {
    private val zone = ZoneOffset.UTC
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).toInstant(zone).toEpochMilli()
    private val now = at(9, 15, 0)
    private fun log(id: Long, number: String, type: CallType, time: Long) =
        CallLog(id, "", number, "India", type, time, 30)

    @Test
    fun groupsConsecutiveSameNumberAndSplitsSections() {
        val logs = listOf(
            log(1, "111", CallType.INCOMING, at(9, 14, 50)),
            log(2, "111", CallType.INCOMING, at(9, 14, 30)),
            log(3, "111", CallType.OUTGOING, at(9, 14, 0)), // different type → new card
            log(4, "111", CallType.OUTGOING, at(8, 21, 30)), // yesterday → new section
            log(5, "222", CallType.MISSED, at(1, 9, 0)),
        )
        val result = groupLogs(logs.shuffled(), now, zone)
        assertEquals(listOf("Today", "Yesterday", "Older"), result.map { it.first })
        assertEquals(listOf(2, 1), result[0].second.map { it.calls.size })
        assertEquals(1L, result[0].second[0].first.id)
    }

    @Test
    fun repeatCallMakesOneGroupWithCount() {
        val t = CallLog(100, "Papa", "111", "Mobile", CallType.MISSED, now, 0)
        val batch = repeatCall(t, 3, 2)
        assertEquals(listOf(now, now - 120_000, now - 240_000), batch.map { it.time })
        assertEquals(3, batch.map { it.id }.toSet().size)
        val groups = groupLogs(batch, now, zone)
        assertEquals(1, groups.single().second.size)
        assertEquals(3, groups.single().second.single().calls.size)
    }

    @Test
    fun randomLogsAreConsistent() {
        val logs = randomLogs(200, now, listOf("Papa" to "111"), kotlin.random.Random(42))
        assertEquals(200, logs.size)
        assertEquals(200, logs.map { it.id }.toSet().size)
        logs.forEach {
            assert(it.time in (now - 7 * 86_400_000L)..now)
            if (!it.type.hasDuration) assertEquals(0, it.durationSec)
            if (it.hd) assert(it.durationSec > 0)
        }
        assert(logs.any { it.name == "Papa" } && logs.any { it.name.isBlank() })
    }

    @Test
    fun importMapsTypesAndSkipsDuplicates() {
        assertEquals(
            listOf(CallType.INCOMING, CallType.OUTGOING, CallType.MISSED, CallType.VOICEMAIL, CallType.REJECTED, CallType.BLOCKED, CallType.INCOMING),
            (1..7).map(::callTypeFromAndroid),
        )
        val mine = CallLog(5, "Papa", "111", "Mobile", CallType.OUTGOING, now, 30)
        val a = CallLog(importedId(5), "Papa", "111", "Mobile", CallType.OUTGOING, now, 30) // same row id as mine, but imported
        val b = CallLog(importedId(6), "", "222", "India", CallType.MISSED, now, 0)
        assertEquals(true, isImported(a) && !isImported(mine))
        val first = newImports(listOf(mine), listOf(a, b))
        assertEquals(listOf(a, b), first)
        assertEquals(emptyList<CallLog>(), newImports(listOf(mine) + first, listOf(a, b))) // re-import adds nothing
    }

    @Test
    fun hdDefaultsToConnected() {
        assertEquals(true, CallLog(1, "", "1", "", CallType.INCOMING, now, 10).hd)
        assertEquals(false, CallLog(1, "", "1", "", CallType.OUTGOING, now, 0).hd)
        assertEquals(false, CallLog(1, "", "1", "", CallType.MISSED, now, 0).hd)
    }

    @Test
    fun labels() {
        assertEquals("10 min ago", timeLabel(at(9, 14, 50), now, zone))
        assertEquals("Just now", timeLabel(now, now, zone))
        assertEquals("0 sec", durationLabel(0))
        assertEquals("45 sec", durationLabel(45))
        assertEquals("2 min", durationLabel(120))
        assertEquals("5 min 10 sec", durationLabel(310))
        assertEquals("1h 8s", callTimeLabel(3608))
        assertEquals("2m 15s", callTimeLabel(135))
        assertEquals("0s", callTimeLabel(0))
        assertEquals("Sun", activityDateLabel(at(8, 9, 0), now, zone)) // 8 Nov 2026 is a Sunday
        assertEquals("Nov 1", activityDateLabel(at(1, 9, 0), now, zone))
    }
}
