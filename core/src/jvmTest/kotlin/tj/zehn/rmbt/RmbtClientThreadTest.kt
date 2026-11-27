package tj.zehn.rmbt

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tj.zehn.rmbt.measure.StepClock
import tj.zehn.rmbt.transport.FakeRmbtServer
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** On Android the collector is often the main thread, where any network call throws. */
class RmbtClientThreadTest {

    @Test
    fun theTestDoesNotRunOnTheCollectorsThread() = runBlocking {
        val connectThreads = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = when (request.url.encodedPath.substringAfterLast('/')) {
                "settings" -> """{"settings":[{"uuid":"c-1"}]}"""
                "testRequest" -> """{"test_uuid":"t-1","test_token":"t-1_1_x","test_server_address":"h",
                    "test_server_port":1,"test_server_type":"RMBThttp","test_server_name":"Local",
                    "test_duration":"1","test_numthreads":"1","test_numpings":"1","test_wait":0,
                    "open_test_uuid":"O1","error":[]}"""
                else -> """{"error":[]}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = RmbtClient(
            config = RmbtConfig(controlUrl = "http://control/RMBTControlServer", transport = Transport.TCP),
            httpClient = HttpClient(engine),
            connector = {
                synchronized(connectThreads) { connectThreads += Thread.currentThread().name }
                FakeRmbtServer(httpUpgrade = true)
            },
            clock = StepClock(500_000_000),
        )
        val main = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }
        try {
            val events = withContext(main.asCoroutineDispatcher()) { client.start().toList() }

            assertIs<RmbtEvent.Finished>(events.last())
            assertTrue(connectThreads.isNotEmpty())
            // Coroutines debug mode appends " @coroutine#N" to thread names.
            assertTrue(connectThreads.none { it.startsWith("fake-main") }, "connected on the collector's thread: $connectThreads")
        } finally {
            main.shutdown()
        }
    }
}
