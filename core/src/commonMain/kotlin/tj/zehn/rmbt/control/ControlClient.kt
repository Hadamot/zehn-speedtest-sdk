package tj.zehn.rmbt.control

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import tj.zehn.rmbt.protocol.RmbtException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The Control Server refused a request or answered with something that isn't a valid answer. */
class ControlServerException(errors: List<String>) : RmbtException("control server: ${errors.joinToString("; ")}")

internal class ControlClient(
    private val baseUrl: String,
    httpClient: HttpClient,
    private val apiKey: String? = null,
    private val client: ClientInfo = ClientInfo(),
) : AutoCloseable {
    private val http = httpClient.config { install(ContentNegotiation) { json(JSON) } }

    suspend fun register(clientUuid: String?): String {
        val request = SettingsRequest(uuid = clientUuid, type = client.type, language = client.language, platform = client.platform)
        val response: SettingsResponse = post("settings", request)
        failOn(response.error)
        return response.settings.firstOrNull()?.uuid ?: throw ControlServerException(listOf("no client uuid"))
    }

    suspend fun requestTest(clientUuid: String, threads: Int?, tag: String? = null, timeMillis: Long = 0): TestTicket {
        val request = TestRequest(
            uuid = clientUuid,
            type = client.type,
            language = client.language,
            platform = client.platform,
            time = timeMillis,
            timezone = client.timezone,
            threads = threads,
            tag = tag,
        )
        val ticket: TestTicket = post("testRequest", request)
        return ticket.also { failOn(it.error) }
    }

    suspend fun submitResult(result: ResultRequest) {
        failOn(post<ResultRequest, ErrorResponse>("result", result).error)
    }

    private suspend inline fun <reified T, reified R> post(path: String, payload: T): R {
        val response = http.post("${baseUrl.trimEnd('/')}/$path") {
            contentType(ContentType.Application.Json)
            apiKey?.let { header(API_KEY_HEADER, it) }
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            // RMBT servers put their reasons in the usual error list; a proxy or gateway answers with a page.
            val errors = runCatching { response.body<ErrorResponse>().error }.getOrNull().orEmpty()
            throw ControlServerException(errors.ifEmpty { listOf("HTTP ${response.status.value}") })
        }
        return response.body()
    }

    /** Closes the client this one derived from the given one (`config` shares the engine, which stays open). */
    override fun close() = http.close()

    private fun failOn(errors: List<String>) {
        if (errors.isNotEmpty()) throw ControlServerException(errors)
    }

    companion object {
        const val API_KEY_HEADER = "X-Api-Key"
        val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
    }
}
