package tj.zehn.rmbt.ui.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tj.zehn.rmbt.ui.model.testRecord

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryStoreTest {

    /** In memory; records which thread name every call ran on. */
    private class MemoryStorage(var json: String? = null) : HistoryStorage {
        val threads = mutableListOf<String>()
        override fun readHistory(): String? = json.also { threads += Thread.currentThread().name }
        override fun writeHistory(json: String) {
            threads += Thread.currentThread().name
            this.json = json
        }
        override var clientUuid: String? = null
    }

    private fun TestScope.store(storage: MemoryStorage) =
        HistoryStore(storage, io = StandardTestDispatcher(testScheduler), scope = backgroundScope)

    @Test
    fun `the history is unknown until read, then loaded from storage`() = runTest {
        val storage = MemoryStorage(HistoryStore.encode(listOf(testRecord("a"))))
        val store = store(storage)

        assertNull(store.history.value)
        runCurrent()

        assertEquals(listOf("a"), store.history.value?.map { it.testUuid })
    }

    @Test
    fun `a new test comes first, replaces its older copy and is written to storage`() = runTest {
        val storage = MemoryStorage()
        val store = store(storage)

        store.add(testRecord("a"))
        store.add(testRecord("b"))
        store.add(testRecord("a", downloadMbps = 99.0))

        assertEquals(listOf("a", "b"), store.history.value?.map { it.testUuid })
        assertEquals(99.0, store.history.value!!.first().downloadMbps, 0.0)
        assertEquals(store.history.value, HistoryStore.decode(storage.json))
    }

    @Test
    fun `keeps at most the newest tests`() = runTest {
        val store = store(MemoryStorage())

        repeat(HistoryStore.MAX_RECORDS + 1) { store.add(testRecord("t$it")) }

        assertEquals(HistoryStore.MAX_RECORDS, store.history.value?.size)
        assertEquals("t${HistoryStore.MAX_RECORDS}", store.history.value?.first()?.testUuid)
    }

    @Test
    fun `a test added before the history was read keeps the stored ones`() = runTest {
        val storage = MemoryStorage(HistoryStore.encode(listOf(testRecord("old"))))
        val store = store(storage)

        store.add(testRecord("new"))

        assertEquals(listOf("new", "old"), store.history.value?.map { it.testUuid })
    }

    @Test
    fun `find waits for the history and sees a test just added`() = runTest {
        val store = store(MemoryStorage())
        store.add(testRecord("a"))

        assertEquals("a", store.find("a")?.testUuid)
        assertNull(store.find("missing"))
    }

    @Test
    fun `storage is read and written on the io dispatcher`() = runTest {
        val storage = MemoryStorage()
        val ioThread = "io-test"
        val io = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, ioThread) }
        val dispatcher = io.asCoroutineDispatcher()
        try {
            val store = HistoryStore(storage, io = dispatcher, scope = backgroundScope)
            store.add(testRecord("a"))
            store.saveClientUuid("client")

            assertTrue(storage.threads.isNotEmpty())
            assertTrue(storage.threads.all { it == ioThread })
            assertEquals("client", store.clientUuid())
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `an unreadable history starts empty`() = runTest {
        val store = store(MemoryStorage("not json"))
        runCurrent()

        assertEquals(emptyList<Any>(), store.history.value)
    }
}
