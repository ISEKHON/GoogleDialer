package com.dialer.calllogmaker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog.Calls
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Reads the phone's real call history — the same system call log Google Phone shows.
 * Read-only: nothing here writes to or deletes from it.
 */
object PhoneCallLog {
    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Calls newer than [since] (epoch millis), newest first. Empty if permission is missing. */
    fun read(ctx: Context, since: Long = 0): List<CallLog> {
        if (!hasPermission(ctx)) return emptyList()
        val country = (ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
            ?.networkCountryIso?.uppercase()?.ifBlank { null } ?: Locale.getDefault().country
        val cols = arrayOf(
            Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.TYPE, Calls.DATE, Calls.DURATION, Calls.FEATURES,
            Calls.GEOCODED_LOCATION, Calls.CACHED_NUMBER_TYPE, Calls.CACHED_NUMBER_LABEL, Calls.PHONE_ACCOUNT_ID,
        )
        val rows = mutableListOf<Pair<CallLog, String?>>()
        runCatching {
            ctx.contentResolver.query(Calls.CONTENT_URI, cols, "${Calls.DATE} > ?", arrayOf("$since"), "${Calls.DATE} DESC")
        }.getOrNull()?.use { c ->
            while (c.moveToNext()) {
                val raw = c.getString(1).orEmpty()
                if (raw.isBlank()) continue // private / unknown caller
                val name = c.getString(2).orEmpty()
                val features = c.getInt(6)
                val duration = c.getLong(5).toInt()
                val type = callTypeFromAndroid(c.getInt(3))
                val label = if (name.isNotBlank()) {
                    Phone.getTypeLabel(ctx.resources, c.getInt(8), c.getString(9)).toString()
                } else c.getString(7)?.ifBlank { null } ?: "Unknown"
                rows += CallLog(
                    id = importedId(c.getLong(0)),
                    name = name,
                    number = PhoneNumberUtils.formatNumber(raw, country) ?: raw,
                    label = label,
                    type = type,
                    time = c.getLong(4),
                    durationSec = duration,
                    video = features and Calls.FEATURES_VIDEO != 0,
                    hd = if (Build.VERSION.SDK_INT >= 30) features and Calls.FEATURES_HD_CALL != 0
                    else duration > 0 && type.hasDuration,
                ) to c.getString(10)
            }
        }
        // shortcut: SIM slot guessed from the order of distinct phone accounts; exact slot needs READ_PHONE_STATE.
        val accounts = rows.mapNotNull { it.second }.distinct().sorted()
        return rows.map { (log, acc) -> if (accounts.size > 1 && acc != null) log.copy(sim = accounts.indexOf(acc) + 1) else log }
    }
}
