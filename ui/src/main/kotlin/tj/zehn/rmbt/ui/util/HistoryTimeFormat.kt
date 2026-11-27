package tj.zehn.rmbt.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Time of a history entry as people say it: "Today, 12:00", "Yesterday, 18:30", otherwise
 * "20.11.18, 12:00". Shown in the time zone the test was made in.
 */
internal object HistoryTimeFormat {

    fun format(timeMillis: Long, timezone: String, nowMillis: Long, today: String, yesterday: String): String {
        val zone = TimeZone.getTimeZone(timezone)
        val test = Calendar.getInstance(zone).apply { timeInMillis = timeMillis }
        val now = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
        val clock = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(test.time)
        val day = when (daysBetween(test, now)) {
            0 -> today
            1 -> yesterday
            else -> SimpleDateFormat("dd.MM.yy", Locale.US).apply { timeZone = zone }.format(test.time)
        }
        return "$day, $clock"
    }

    /** Calendar days from [from] to [to] (0 for the same day), both in the same time zone. */
    private fun daysBetween(from: Calendar, to: Calendar): Int {
        if (from.get(Calendar.YEAR) == to.get(Calendar.YEAR)) {
            return to.get(Calendar.DAY_OF_YEAR) - from.get(Calendar.DAY_OF_YEAR)
        }
        val dayBefore = (to.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val sameDay = { a: Calendar, b: Calendar ->
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        }
        return if (sameDay(from, dayBefore)) 1 else Int.MAX_VALUE
    }
}
