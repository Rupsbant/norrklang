package studio.koeda.norrklang.plex

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlin.coroutines.cancellation.CancellationException
import studio.koeda.norrklang.plex.model.PlexHome
import studio.koeda.norrklang.plex.model.PlexHomeUser
import studio.koeda.norrklang.plex.model.PlexPin
import studio.koeda.norrklang.plex.model.PlexResource
import studio.koeda.norrklang.plex.model.PlexSwitchedUser
import studio.koeda.norrklang.plex.model.PlexUser

/**
 * The plex.tv side of sign-in: PIN-based account linking and server discovery.
 *
 * [engine] exists for tests (Ktor MockEngine); production shares one OkHttp
 * engine across instances. [close] releases the client's own resources but
 * never the shared engine.
 */
class PlexTvClient(
    private val clientInfo: PlexClientInfo,
    engine: HttpClientEngine = PlexHttp.sharedEngine,
) : AutoCloseable {

    private val http = PlexHttp.client(engine)

    /**
     * Creates a link PIN. The user enters [PlexPin.code] at plex.tv/link
     * (or scans a QR of the prefilled URL); [checkPin] polls for the claim.
     *
     * Deliberately NOT a strong pin: strong pins get a ~24-character code
     * for the auth.app.plex.tv redirect flow, while plex.tv/link expects the
     * short 4-character code only regular pins carry.
     */
    suspend fun createPin(): PlexPin {
        val response = PlexHttp.request(BASE) {
            http.post("$BASE/api/v2/pins") { plexHeaders(clientInfo) }
        }
        return parse(response)
    }

    /** The PIN's current state; [PlexPin.authToken] is non-null once claimed. */
    suspend fun checkPin(id: Long): PlexPin {
        val response = PlexHttp.request(BASE) {
            http.get("$BASE/api/v2/pins/$id") { plexHeaders(clientInfo) }
        }
        return parse(response)
    }

    /** The linked account's display identity. */
    suspend fun user(token: String): PlexUser {
        val response = PlexHttp.request(BASE) {
            http.get("$BASE/api/v2/user") { plexHeaders(clientInfo, token) }
        }
        return parse(response)
    }

    /**
     * The profiles of the Plex Home the account administers. plex.tv refuses
     * the call for accounts outside a Home and for non-admin members, so
     * callers treat any failure as "nothing to pick".
     */
    suspend fun homeUsers(token: String): List<PlexHomeUser> {
        val response = PlexHttp.request(BASE) {
            http.get("$BASE/api/v2/home/users") { plexHeaders(clientInfo, token) }
        }
        return parse<PlexHome>(response).users
    }

    /**
     * Switches to a Home profile and returns that profile's plex.tv token.
     * The token is plex.tv-only — PMS rejects it outright — so it must go
     * through [servers] for the per-server access token before any server
     * call. A wrong or missing [pin] surfaces as [PlexException.AuthFailed].
     */
    suspend fun switchHomeUser(token: String, user: PlexHomeUser, pin: String?): String {
        val response = PlexHttp.request(BASE) {
            http.submitForm(
                url = "$BASE/api/v2/home/users/${user.uuid}/switch",
                formParameters = parameters { if (pin != null) append("pin", pin) },
            ) { plexHeaders(clientInfo, token) }
        }
        val switched = parse<PlexSwitchedUser>(response)
        return switched.authToken?.takeIf { it.isNotEmpty() }
            ?: throw PlexException.ServerError(null, "plex.tv switched user without a token")
    }

    /**
     * The account's Plex Media Servers, with their per-server access tokens
     * and candidate connections (local/remote/relay).
     */
    suspend fun servers(token: String): List<PlexResource> {
        val response = PlexHttp.request(BASE) {
            http.get("$BASE/api/v2/resources") {
                parameter("includeHttps", "1")
                parameter("includeRelay", "1")
                plexHeaders(clientInfo, token)
            }
        }
        return parse<List<PlexResource>>(response).filter { it.isServer }
    }

    private suspend inline fun <reified T> parse(response: HttpResponse): T {
        if (!response.status.isSuccess()) {
            // An expired or unknown PIN id answers 404; the caller treats
            // NotFound as "start over with a fresh PIN".
            throw PlexException.fromStatusCode(response.status.value, BASE)
        }
        return try {
            response.body<T>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PlexException.ServerError(null, "Unexpected response from plex.tv", e)
        }
    }

    override fun close() {
        http.close()
    }

    private companion object {
        const val BASE = "https://plex.tv"
    }
}
