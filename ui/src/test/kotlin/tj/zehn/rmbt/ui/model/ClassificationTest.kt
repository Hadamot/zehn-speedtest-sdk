package tj.zehn.rmbt.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassificationTest {

    @Test
    fun `download thresholds 5, 10 and 100 Mbit per s`() {
        assertEquals(Classification.NONE, Classification.ofDownload(0.0))
        assertEquals(Classification.NONE, Classification.ofDownload(-1.0))
        assertEquals(Classification.BAD, Classification.ofDownload(4.99))
        assertEquals(Classification.NORMAL, Classification.ofDownload(5.0))
        assertEquals(Classification.NORMAL, Classification.ofDownload(9.99))
        assertEquals(Classification.GOOD, Classification.ofDownload(10.0))
        assertEquals(Classification.GOOD, Classification.ofDownload(99.9))
        assertEquals(Classification.EXCELLENT, Classification.ofDownload(100.0))
    }

    @Test
    fun `upload thresholds 2,5, 5 and 50 Mbit per s`() {
        assertEquals(Classification.NONE, Classification.ofUpload(0.0))
        assertEquals(Classification.BAD, Classification.ofUpload(2.49))
        assertEquals(Classification.NORMAL, Classification.ofUpload(2.5))
        assertEquals(Classification.GOOD, Classification.ofUpload(5.0))
        assertEquals(Classification.GOOD, Classification.ofUpload(49.9))
        assertEquals(Classification.EXCELLENT, Classification.ofUpload(50.0))
    }

    @Test
    fun `ping thresholds 75, 25 and 10 ms, lower is better`() {
        assertEquals(Classification.NONE, Classification.ofPing(0.0))
        assertEquals(Classification.EXCELLENT, Classification.ofPing(9.9))
        assertEquals(Classification.GOOD, Classification.ofPing(10.0))
        assertEquals(Classification.GOOD, Classification.ofPing(24.9))
        assertEquals(Classification.NORMAL, Classification.ofPing(25.0))
        assertEquals(Classification.NORMAL, Classification.ofPing(74.9))
        assertEquals(Classification.BAD, Classification.ofPing(75.0))
    }

    @Test
    fun `test record rates its own values`() {
        val record = testRecord(downloadMbps = 50.0, uploadMbps = 1.0, pingMs = 8.0)
        assertEquals(Classification.GOOD, record.downloadClass)
        assertEquals(Classification.BAD, record.uploadClass)
        assertEquals(Classification.EXCELLENT, record.pingClass)
    }
}
