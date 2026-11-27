package tj.zehn.rmbt.ui.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tj.zehn.rmbt.ui.model.GeoPoint
import tj.zehn.rmbt.ui.model.NetworkKind
import tj.zehn.rmbt.ui.model.TestRecord

/** Where the test screen keeps a finished test and the client UUID; [HistoryStore] on the device. */
internal interface TestRecordSink {
    suspend fun clientUuid(): String?
    suspend fun saveClientUuid(uuid: String)
    suspend fun add(record: TestRecord)
}

/** The raw storage of [HistoryStore]: blocking calls, made off the main thread. */
internal interface HistoryStorage {
    fun readHistory(): String?
    fun writeHistory(json: String)
    var clientUuid: String?
}

private class PreferencesHistoryStorage(private val prefs: SharedPreferences) : HistoryStorage {
    override fun readHistory(): String? = prefs.getString(KEY_HISTORY, null)

    override fun writeHistory(json: String) = prefs.edit().putString(KEY_HISTORY, json).apply()

    override var clientUuid: String?
        get() = prefs.getString(KEY_CLIENT_UUID, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_UUID, value).apply()

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_CLIENT_UUID = "client_uuid"
    }
}

/**
 * The tests made on this device, newest first, kept in the host app's private storage. Also keeps the client UUID
 * the Control Server assigned, so the server sees one client across tests. Reading, decoding and writing the up to
 * [MAX_RECORDS] records happen on [io], never on the main thread.
 */
internal class HistoryStore(
    private val storage: HistoryStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : TestRecordSink {

    private val mutex = Mutex()
    private val records = MutableStateFlow<List<TestRecord>?>(null)

    /** The history, newest first; null until it has been read from storage. */
    val history: StateFlow<List<TestRecord>?> = records.asStateFlow()

    init {
        scope.launch { loaded() }
    }

    suspend fun find(testUuid: String): TestRecord? = loaded().firstOrNull { it.testUuid == testUuid }

    override suspend fun clientUuid(): String? = withContext(io) { storage.clientUuid }

    override suspend fun saveClientUuid(uuid: String) = withContext(io) { storage.clientUuid = uuid }

    override suspend fun add(record: TestRecord) = mutex.withLock {
        val updated = (listOf(record) + readLocked().filter { it.testUuid != record.testUuid }).take(MAX_RECORDS)
        withContext(io) { storage.writeHistory(encode(updated)) }
        records.value = updated
    }

    private suspend fun loaded(): List<TestRecord> = records.value ?: mutex.withLock { readLocked() }

    /** Under [mutex]: the records in memory, read from storage the first time. */
    private suspend fun readLocked(): List<TestRecord> = records.value
        ?: withContext(io) { runCatching { decode(storage.readHistory()) }.getOrDefault(emptyList()) }
            .also { records.value = it }

    companion object {
        private const val PREFS = "tj.zehn.rmbt.ui.history"
        internal const val MAX_RECORDS = 500

        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore = instance ?: synchronized(this) {
            instance ?: HistoryStore(
                PreferencesHistoryStorage(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
            ).also { instance = it }
        }

        internal fun encode(records: List<TestRecord>): String = JSONArray().apply {
            records.forEach { r ->
                put(
                    JSONObject()
                        .put("testUuid", r.testUuid)
                        .put("openTestUuid", r.openTestUuid ?: JSONObject.NULL)
                        .put("time", r.timeMillis)
                        .put("timezone", r.timezone)
                        .put("download", r.downloadMbps)
                        .put("upload", r.uploadMbps)
                        .put("ping", r.pingMs)
                        .put("server", r.serverName)
                        .put("network", r.network.name)
                        .put("networkName", r.networkName ?: JSONObject.NULL)
                        .put("signalLevel", r.signalLevel ?: JSONObject.NULL)
                        .put("location", r.location?.toJson() ?: JSONObject.NULL)
                )
            }
        }.toString()

        internal fun decode(json: String?): List<TestRecord> {
            if (json.isNullOrEmpty()) return emptyList()
            val array = JSONArray(json)
            return List(array.length()) { i ->
                val o = array.getJSONObject(i)
                TestRecord(
                    testUuid = o.getString("testUuid"),
                    openTestUuid = o.optStringOrNull("openTestUuid"),
                    timeMillis = o.getLong("time"),
                    timezone = o.getString("timezone"),
                    downloadMbps = o.getDouble("download"),
                    uploadMbps = o.getDouble("upload"),
                    pingMs = o.getDouble("ping"),
                    serverName = o.getString("server"),
                    network = runCatching { NetworkKind.valueOf(o.getString("network")) }.getOrDefault(NetworkKind.OTHER),
                    networkName = o.optStringOrNull("networkName"),
                    signalLevel = if (o.isNull("signalLevel")) null else o.optInt("signalLevel"),
                    // Records saved before the location was kept have no "location" at all.
                    location = o.optJSONObject("location")?.toGeoPoint(),
                )
            }
        }

        private fun GeoPoint.toJson(): JSONObject = JSONObject()
            .put("lat", latitude)
            .put("lon", longitude)
            .put("accuracy", accuracyMeters?.toDouble() ?: JSONObject.NULL)

        private fun JSONObject.toGeoPoint(): GeoPoint? = runCatching {
            GeoPoint(
                latitude = getDouble("lat"),
                longitude = getDouble("lon"),
                accuracyMeters = if (isNull("accuracy")) null else getDouble("accuracy").toFloat(),
            )
        }.getOrNull()

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
    }
}
