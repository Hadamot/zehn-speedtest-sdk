package tj.zehn.rmbt.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LastTestCardTest {

    private val ru = Locale.forLanguageTag("ru")

    @Test
    fun `empty history invites the first test`() {
        assertEquals(LastTestCard.NoTests, LastTestCard.after(emptyList(), Locale.US))
    }

    @Test
    fun `shows the newest test, which is first in the history`() {
        val card = LastTestCard.after(listOf(testRecord(testUuid = "new"), testRecord(testUuid = "old")), Locale.US)

        assertTrue(card is LastTestCard.Result)
        assertEquals("new", (card as LastTestCard.Result).summary.testUUID)
    }

    @Test
    fun `values use the decimal separator of the language and keep their ratings`() {
        val summary = summaryOf(testRecord(downloadMbps = 23.94, uploadMbps = 4.0, pingMs = 4.5), ru)

        assertEquals(LastTestSummary.Value("23,9", Classification.GOOD), summary.download)
        assertEquals(LastTestSummary.Value("4,0", Classification.NORMAL), summary.upload)
        assertEquals(LastTestSummary.Value("4,5", Classification.EXCELLENT), summary.ping)
    }

    @Test
    fun `overall rating is the weakest value`() {
        val summary = summaryOf(testRecord(downloadMbps = 200.0, uploadMbps = 1.0, pingMs = 5.0), Locale.US)

        assertEquals(Classification.BAD, summary.rating)
    }

    @Test
    fun `values that were not measured show a dash and are not rated`() {
        val summary = summaryOf(testRecord(downloadMbps = 0.0, uploadMbps = 0.0, pingMs = 0.0), Locale.US)

        val missing = LastTestSummary.Value(LastTestSummary.MISSING_VALUE, Classification.NONE)
        assertEquals(missing, summary.download)
        assertEquals(missing, summary.upload)
        assertEquals(missing, summary.ping)
        assertEquals(Classification.NONE, summary.rating)
    }

    private fun summaryOf(record: TestRecord, locale: Locale): LastTestSummary =
        (LastTestCard.after(listOf(record), locale) as LastTestCard.Result).summary
}
