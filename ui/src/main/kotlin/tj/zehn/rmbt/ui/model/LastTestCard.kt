package tj.zehn.rmbt.ui.model

import tj.zehn.rmbt.ui.util.ResultExplanations
import tj.zehn.rmbt.ui.model.Classification
import tj.zehn.rmbt.ui.util.ResultValueFormat
import java.util.Locale

/** What the start screen's "last test" card under the wave shows. */
internal sealed interface LastTestCard {

    /** Not loaded yet, or the first load failed: the card is not shown at all. */
    data object Hidden : LastTestCard

    /** The history is empty: the card invites the user to make the first test. */
    data object NoTests : LastTestCard

    data class Result(val summary: LastTestSummary) : LastTestCard

    companion object {

        /**
         * The card for the stored history: the newest test, or [NoTests] for an empty history.
         */
        fun after(latest: List<TestRecord>, locale: Locale): LastTestCard =
            if (latest.isEmpty()) NoTests else Result(LastTestSummary.from(latest.first(), locale))
    }
}

/** The newest test as the card shows it: "Today, 09:10 · ↓ 45.2 ↑ 12.1 ⏱ 18". */
internal data class LastTestSummary(
    val testUUID: String,
    val isCoverageResult: Boolean,
    val timeMillis: Long,
    val timezone: String,
    val download: Value,
    val upload: Value,
    val ping: Value,
) {

    /** One value of the test and its rating, which colours its icon. */
    data class Value(val text: String, val classification: Classification)

    /** The overall rating: the weakest of the three, [Classification.NONE] when none is rated. */
    val rating: Classification
        get() = ResultExplanations.overall(download.classification, upload.classification, ping.classification)

    companion object {

        /** Shown for a value the server did not send. */
        const val MISSING_VALUE = "–"

        fun from(record: TestRecord, locale: Locale) = LastTestSummary(
            testUUID = record.testUuid,
            isCoverageResult = false,
            timeMillis = record.timeMillis,
            timezone = record.timezone,
            download = value(speed(record.downloadMbps, locale), record.downloadClass),
            upload = value(speed(record.uploadMbps, locale), record.uploadClass),
            ping = value(ping(record.pingMs, locale), record.pingClass),
        )

        private fun speed(mbps: Double, locale: Locale): String? =
            if (mbps > 0) ResultValueFormat.speedNumber((mbps * 1000).toLong(), locale) else null

        private fun ping(ms: Double, locale: Locale): String? =
            if (ms > 0) ResultValueFormat.pingNumber(ms, locale) else null

        private fun value(text: String?, classification: Classification) =
            Value(text?.takeIf { it.isNotBlank() } ?: MISSING_VALUE, classification)
    }
}
