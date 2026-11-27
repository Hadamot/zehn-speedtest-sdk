package tj.zehn.rmbt

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import tj.zehn.rmbt.measure.StepClock
import tj.zehn.rmbt.transport.Connection
import tj.zehn.rmbt.transport.Connector
import tj.zehn.rmbt.transport.Endpoint
import tj.zehn.rmbt.transport.FakeRmbtServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RmbtClientTest {
    private val paths = mutableListOf<String>()
    private val ticket = """{"test_uuid":"t-1","test_token":"t-1_1_x","test_server_address":"h","test_server_port":1,
        "test_server_type":"RMBThttp","test_server_name":"Local","test_duration":"1","test_numthreads":"2",
        "test_numpings":"3","test_wait":0,"open_test_uuid":"O1","error":[]}"""

    private fun client(
        resultBody: String = """{"error":[]}""",
        connector: Connector = Connector { FakeRmbtServer(httpUpgrade = true) },
    ): RmbtClient {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath.substringAfterLast('/')
            paths += path
            val body = when (path) {
                "settings" -> """{"settings":[{"uuid":"c-1"}]}"""
                "testRequest" -> ticket
                else -> resultBody
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return RmbtClient(
            config = RmbtConfig(controlUrl = "http://control/RMBTControlServer", transport = Transport.TCP),
            httpClient = HttpClient(engine),
            connector = connector,
            clock = StepClock(500_000_000),
        )
    }

    @Test
    fun runRegistersMeasuresAndSubmits() = runTest {
        val result = client().run()

        assertEquals(listOf("settings", "testRequest", "result"), paths)
        assertEquals("c-1", result.clientUuid)
        assertEquals("Local", result.serverName)
        assertTrue(result.downloadMbps > 0)
        assertEquals(2.0, result.pingMs)
    }

    @Test
    fun startEmitsPhasesAndEndsWithFinished() = runTest {
        val events = client().start().toList()

        assertEquals(TestPhase.SUBMIT, events.filterIsInstance<RmbtEvent.Phase>().last().phase)
        assertIs<RmbtEvent.Finished>(events.last())
    }

    @Test
    fun startEndsWithFailedWhenSubmitIsRejected() = runTest {
        val events = client(resultBody = """{"error":["rejected"]}""").start().toList()

        assertIs<RmbtEvent.Failed>(events.last())
    }

    @Test
    fun runWithCallbackReportsEveryEvent() = runTest {
        val events = mutableListOf<RmbtEvent>()

        client().run { events += it }

        assertIs<RmbtEvent.Finished>(events.last())
        assertTrue(events.any { it is RmbtEvent.Phase })
    }

    @Test
    fun cancelStopsTheRunningTest() = runTest {
        val connecting = CompletableDeferred<Unit>()
        val hanging = Connector {
            connecting.complete(Unit)
            awaitCancellation()
        }
        val client = client(connector = hanging)
        val collecting = launch { runCatching { client.start().toList() } }

        connecting.await()
        client.cancel()

        // The test runs on Dispatchers.Default: wait in real time, not in the test's virtual time.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            withTimeout(5_000) { collecting.join() }
        }
    }

    @Test
    fun closeClosesTheConnectorAndTheHttpClient() {
        val connector = ClosableConnector()
        val client = client(connector = connector)

        client.close()

        assertTrue(connector.closed)
    }

    private class ClosableConnector : Connector, AutoCloseable {
        var closed = false

        override suspend fun connect(endpoint: Endpoint): Connection = awaitCancellation()

        override fun close() {
            closed = true
        }
    }
}
