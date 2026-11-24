package tj.zehn.rmbt.control

import tj.zehn.rmbt.measure.PingSample
import tj.zehn.rmbt.measure.Rate
import tj.zehn.rmbt.measure.Sample
import tj.zehn.rmbt.run.Measurement
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultMapperTest {
    private val ticket = TestTicket.Parsed("t-1", "tok", "h", 8081, true, true, "S", 7, 2, 2, 0)
    private val measurement = Measurement(
        threads = 2,
        download = Rate(bytes = 1_250_000.0, nanos = 1_000_000_000),
        upload = Rate(bytes = 125_000.0, nanos = 1_000_000_000),
        downloadSamples = listOf(
            listOf(Sample(50_000_000, 10), Sample(120_000_000, 20), Sample(150_000_000, 30), Sample(260_000_000, 40)),
            listOf(Sample(90_000_000, 5)),
        ),
        uploadSamples = listOf(listOf(Sample(1_000_000_000, 7)), emptyList()),
        pings = listOf(PingSample(3_000_000, 2_000_000, 100), PingSample(1_000_000, 900_000, 600)),
    )

    private val result = ResultMapper.map(measurement, ticket, clientUuid = "c-1", info = ClientInfo(), networkType = 98, timeMillis = 5)

    @Test
    fun convertsRatesToKilobitsAndBytes() {
        assertEquals(10_000, result.speedDownloadKbps)
        assertEquals(1_000, result.speedUploadKbps)
        assertEquals(1_250_000, result.bytesDownload)
        assertEquals(1_000_000_000, result.nsecDownload)
    }

    @Test
    fun totalsLastBytesOfEveryThread() {
        assertEquals(45, result.totalBytesDownload)
        assertEquals(7, result.totalBytesUpload)
    }

    @Test
    fun reportsShortestClientPingAndRelativePingTimes() {
        assertEquals(1_000_000, result.pingShortestNanos)
        assertEquals(listOf(0L, 500L), result.pings.map { it.timeNs })
    }

    @Test
    fun keepsOneSpeedDetailPerIntervalPlusLast() {
        val firstThread = result.speedDetail.filter { it.direction == "download" && it.thread == 0 }

        assertEquals(listOf(10L, 20L, 40L), firstThread.map { it.bytes })
    }

    @Test
    fun marksEncryptionAndToken() {
        assertEquals("TLS", result.encryption)
        assertEquals("tok", result.testToken)
    }
}
