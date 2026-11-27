package tj.zehn.rmbt.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tj.zehn.rmbt.ui.model.Classification
import tj.zehn.rmbt.ui.model.GeoPoint
import tj.zehn.rmbt.ui.model.MeasurementState
import java.util.Locale

class ResultFormattingTest {

    @Test
    fun `coordinates have five decimals with a dot in any language`() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
        try {
            assertEquals("38.55980, 68.78700", ResultValueFormat.coordinates(GeoPoint(38.5598, 68.787)))
            assertEquals("-33.86882, 151.20930", ResultValueFormat.coordinates(GeoPoint(-33.868820, 151.209296)))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `accuracy is whole metres and absent when unknown or under a metre`() {
        assertEquals(13, ResultValueFormat.accuracyMeters(GeoPoint(0.0, 0.0, accuracyMeters = 12.6f)))
        assertNull(ResultValueFormat.accuracyMeters(GeoPoint(0.0, 0.0, accuracyMeters = 0.4f)))
        assertNull(ResultValueFormat.accuracyMeters(GeoPoint(0.0, 0.0, accuracyMeters = null)))
    }

    private val ru = Locale.forLanguageTag("ru")

    @Test
    fun `speed has one decimal, rounded half up, in the language's format`() {
        assertEquals("23,9", ResultValueFormat.speedNumber(23_940, ru))
        assertEquals("24.0", ResultValueFormat.speedNumber(23_960, Locale.US))
        assertEquals("0.1", ResultValueFormat.speedNumber(100, Locale.US))
    }

    @Test
    fun `ping keeps a decimal only below 10 ms`() {
        assertEquals("4,5", ResultValueFormat.pingNumber(4.5, ru))
        assertEquals("4", ResultValueFormat.pingNumber(4.0, Locale.US))
        assertEquals("15", ResultValueFormat.pingNumber(14.6, Locale.US))
    }

    @Test
    fun `server values get the unit in the app language`() {
        assertEquals("14 Мбит/с", ResultValueFormat.serverValue("14 Mbps", "Мбит/с", "мс"))
        assertEquals("31 мс", ResultValueFormat.serverValue("31ms", "Мбит/с", "мс"))
        assertEquals("5 dBm", ResultValueFormat.serverValue("5 dBm", "Мбит/с", "мс"))
        assertNull(ResultValueFormat.serverValue(null, "Мбит/с", "мс"))
    }

    @Test
    fun `verdict is the weakest rated value and ignores missing ones`() {
        assertEquals(
            Classification.NORMAL,
            ResultExplanations.overall(Classification.EXCELLENT, Classification.NORMAL, Classification.GOOD),
        )
        assertEquals(
            Classification.GOOD,
            ResultExplanations.overall(Classification.GOOD, Classification.NONE, null),
        )
        assertEquals(Classification.NONE, ResultExplanations.overall(null, Classification.NONE, null))
    }

    @Test
    fun `steps are numbered 1 to 4 without QoS`() {
        assertEquals(1, MeasurementPhases.of(MeasurementState.INIT, false)?.step)
        assertEquals(2, MeasurementPhases.of(MeasurementState.PING, false)?.step)
        assertEquals(3, MeasurementPhases.of(MeasurementState.DOWNLOAD, false)?.step)
        assertEquals(4, MeasurementPhases.of(MeasurementState.UPLOAD, false)?.step)
        assertEquals(4, MeasurementPhases.of(MeasurementState.UPLOAD, false)?.total)
        assertNull(MeasurementPhases.of(MeasurementState.FINISH, false))
        assertNull(MeasurementPhases.of(MeasurementState.ERROR, false))
    }

    @Test
    fun `a value is final when the test moves on to the next step`() {
        assertEquals(MeasurementState.PING, MeasurementPhases.finishedValue(MeasurementState.PING, MeasurementState.DOWNLOAD))
        assertEquals(MeasurementState.DOWNLOAD, MeasurementPhases.finishedValue(MeasurementState.DOWNLOAD, MeasurementState.UPLOAD))
        assertEquals(MeasurementState.UPLOAD, MeasurementPhases.finishedValue(MeasurementState.UPLOAD, MeasurementState.FINISH))
        assertNull(MeasurementPhases.finishedValue(null, MeasurementState.INIT))
        assertNull(MeasurementPhases.finishedValue(MeasurementState.DOWNLOAD, MeasurementState.DOWNLOAD))
    }
}
