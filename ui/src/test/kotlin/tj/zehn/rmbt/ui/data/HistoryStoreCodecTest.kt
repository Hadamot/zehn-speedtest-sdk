package tj.zehn.rmbt.ui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tj.zehn.rmbt.ui.model.GeoPoint
import tj.zehn.rmbt.ui.model.NetworkKind
import tj.zehn.rmbt.ui.model.testRecord

class HistoryStoreCodecTest {

    @Test
    fun `records survive encode and decode in order`() {
        val records = listOf(
            testRecord(testUuid = "new", network = NetworkKind.CELLULAR, networkName = "Tcell", signalLevel = 4),
            testRecord(testUuid = "old", network = NetworkKind.ETHERNET, networkName = null, signalLevel = null, openTestUuid = null),
        )

        assertEquals(records, HistoryStore.decode(HistoryStore.encode(records)))
    }

    @Test
    fun `the test location survives encode and decode, with or without accuracy`() {
        val records = listOf(
            testRecord(testUuid = "fix", location = GeoPoint(38.5598, 68.787, accuracyMeters = 12.5f)),
            testRecord(testUuid = "rough", location = GeoPoint(37.0, 69.0, accuracyMeters = null)),
            testRecord(testUuid = "none", location = null),
        )

        assertEquals(records, HistoryStore.decode(HistoryStore.encode(records)))
    }

    @Test
    fun `nothing stored yet is an empty history`() {
        assertEquals(emptyList<Any>(), HistoryStore.decode(null))
        assertEquals(emptyList<Any>(), HistoryStore.decode(""))
        assertEquals(emptyList<Any>(), HistoryStore.decode("[]"))
    }

    @Test
    fun `records stored before signalLevel and networkName existed still load`() {
        val json = """
            [{"testUuid":"t1","openTestUuid":null,"time":1764050400000,"timezone":"Asia/Dushanbe",
              "download":45.2,"upload":12.1,"ping":18.0,"server":"Dushanbe","network":"WIFI"}]
        """.trimIndent()

        val record = HistoryStore.decode(json).single()

        assertEquals("t1", record.testUuid)
        assertEquals(NetworkKind.WIFI, record.network)
        assertNull(record.openTestUuid)
        assertNull(record.networkName)
        assertNull(record.signalLevel)
        assertNull(record.location)
    }

    @Test
    fun `a broken location reads as absent and keeps the record`() {
        val json = HistoryStore.encode(listOf(testRecord(location = GeoPoint(38.5, 68.7))))
            .replace("\"lat\":38.5", "\"lat\":\"north\"")

        val record = HistoryStore.decode(json).single()

        assertEquals("test-1", record.testUuid)
        assertNull(record.location)
    }

    @Test
    fun `an unknown network kind falls back to other`() {
        val json = HistoryStore.encode(listOf(testRecord())).replace("\"WIFI\"", "\"SATELLITE\"")

        assertEquals(NetworkKind.OTHER, HistoryStore.decode(json).single().network)
    }

    @Test
    fun `empty optional strings read as absent`() {
        val json = HistoryStore.encode(listOf(testRecord(networkName = ""))).replace("\"open-1\"", "\"\"")

        val record = HistoryStore.decode(json).single()

        assertNull(record.networkName)
        assertNull(record.openTestUuid)
    }
}
