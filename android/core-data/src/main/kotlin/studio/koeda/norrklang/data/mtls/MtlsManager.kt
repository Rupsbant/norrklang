package studio.koeda.norrklang.data.mtls

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import io.ktor.client.statement.bodyAsBytes
import studio.koeda.norrklang.data.di.ApplicationScope
import studio.koeda.norrklang.data.settings.ServerSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import studio.koeda.norrklang.data.diagnostics.Diagnostics

/**
 * Owns the device's mTLS identity: the EC keypair generated here (the private
 * key never leaves the device — it is stored encrypted, see
 * [ServerSettingsRepository]) and the certificate the server signs for its
 * public key.
 *
 * The public key leaves the device only as the user hands it over: as a QR
 * code, copied, or via [uploadPublicKey] to the user's own endpoint. The
 * signed certificate comes back via [downloadCertificate] +
 * [installCertificate], which verifies it matches the generated keypair.
 */
@Singleton
class MtlsManager @Inject constructor(
    private val settings: ServerSettingsRepository,
    @ApplicationScope scope: CoroutineScope,
    private val http: HttpClient = defaultHttp,
) {

    sealed interface Status {
        data object None : Status
        data class KeyGenerated(val publicKeyPem: String) : Status
        data class Ready(val publicKeyPem: String, val certificateSubject: String) : Status
    }

    enum class UploadMethod { GET, POST }

    /** 2xx means the key arrived; [location] is the response's `Location` header, if any. */
    data class UploadResult(val status: Int, val location: String?)

    /** Why [installCertificate] (or a store read) failed. */
    enum class Failure {
        /** No keypair has been generated yet. */
        NO_KEYPAIR,

        /** The download is not a parseable X.509 certificate. */
        INVALID_CERTIFICATE,

        /** The certificate was signed for a different key. */
        KEY_MISMATCH,
    }

    class MtlsException(val failure: Failure) : Exception(failure.name)

    /**
     * The live identity for transport (see [MtlsOkHttpProvider]). Backed by a
     * volatile so the (non-suspend) engine factory can read it; refreshed by
     * every mutation and by [ensureLoaded] before sessions are built.
     */
    @Volatile
    var identityNow: MtlsIdentity? = null
        private set

    val status: Flow<Status> = settings.mtlsMaterial
        .map(::toStatus)
        .distinctUntilChanged()

    init {
        scope.launch {
            try {
                ensureLoaded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.record("mtls-load", e)
            }
        }
    }

    /** Reads the identity once from the store; safe to call repeatedly. */
    suspend fun ensureLoaded() {
        identityNow = (settings.mtlsMaterial.first() as? MtlsMaterial.Complete)?.identity
    }

    /** The generated public key as an SPKI PEM block, for QR/copy/upload. */
    fun publicKeyPem(publicKey: PublicKey): String = runCatching {
        val base64 = Base64.getEncoder().encodeToString(publicKey.encoded)
        "-----BEGIN PUBLIC KEY-----\n" +
            base64.chunked(PEM_LINE_LENGTH).joinToString("\n") +
            "\n-----END PUBLIC KEY-----"
    }.getOrThrow()

    /** The generated public key, before or after signing; null when nothing is generated. */
    suspend fun currentPublicKey(): PublicKey? = (settings.mtlsMaterial.first())?.let {
        when (it) {
            is MtlsMaterial.KeyPairOnly -> it.keyPair.public
            is MtlsMaterial.Complete -> it.identity.keyPair.public
        }
    }

    /**
     * Generates a fresh EC P-256 keypair and stores it, replacing any
     * previous one (which also drops its certificate — a new key invalidates
     * it).
     */
    suspend fun generateKeyPair() {
        val generator = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE_P256))
        }
        settings.storeMtlsKeyPair(generator.generateKeyPair())
        ensureLoaded()
    }

    /**
     * Parses [data] (DER or PEM), verifies the certificate was signed for the
     * stored keypair's public key and installs it.
     */
    suspend fun installCertificate(data: ByteArray): Result<MtlsIdentity> {
        val keyPair = (settings.mtlsMaterial.first())?.let {
            when (it) {
                is MtlsMaterial.KeyPairOnly -> it.keyPair
                is MtlsMaterial.Complete -> it.identity.keyPair
            }
        } ?: return Result.failure(MtlsException(Failure.NO_KEYPAIR))
        val certificate = try {
            parseCertificate(data)
        } catch (e: Exception) {
            return Result.failure(MtlsException(Failure.INVALID_CERTIFICATE))
        }
        if (certificate.publicKey != keyPair.public) {
            return Result.failure(MtlsException(Failure.KEY_MISMATCH))
        }
        settings.storeMtlsCertificate(certificate)
        ensureLoaded()
        return Result.success(
            MtlsIdentity(keyPair, certificate).also { identityNow = it },
        )
    }

    /** Removes the keypair and any certificate; the next connection is plain TLS. */
    suspend fun reset() {
        settings.removeMtlsIdentity()
        ensureLoaded()
    }

    /**
     * Sends [pem] to the user's upload endpoint. GET: as the `cert` query
     * parameter (URL-encoded by Ktor); POST: as a `text/plain` body. The
     * endpoint is user-hosted (the issue leaves its implementation to the
     * server owner), so only the transport is standardized here.
     */
    suspend fun uploadPublicKey(
        url: String,
        method: UploadMethod,
        pem: String,
    ): UploadResult = withContext(Dispatchers.IO) {
        val response = if (method == UploadMethod.GET) {
            http.get(url) { parameter("cert", pem) }
        } else {
            http.post(url) {
                // `setBody` wraps the PEM in a `TextContent` (text/plain); Ktor
                // drops any manually set Content-Type and the engine takes it
                // from the body.
                setBody(pem)
            }
        }
        UploadResult(response.status.value, response.headers[HttpHeaders.Location])
    }

    /** Downloads the signed certificate the user points at; no special hosting needed. */
    suspend fun downloadCertificate(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val response = http.get(url)
            if (!response.status.isSuccess()) {
                Result.failure(IOException("HTTP ${response.status.value}"))
            } else {
                Result.success(response.bodyAsBytes())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toStatus(material: MtlsMaterial?): Status = when (material) {
        null -> Status.None
        is MtlsMaterial.KeyPairOnly ->
            Status.KeyGenerated(publicKeyPem(material.keyPair.public))
        is MtlsMaterial.Complete ->
            Status.Ready(
                publicKeyPem(material.identity.keyPair.public),
                material.identity.certificate.subjectX500Principal.name,
            )
    }

    private fun parseCertificate(data: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        try {
            return factory.generateCertificate(ByteArrayInputStream(data)) as X509Certificate
        } catch (e: Exception) {
            // Not raw DER — try a PEM document.
        }
        val text = String(data, Charsets.ISO_8859_1)
        val begin = "-----BEGIN CERTIFICATE-----"
        val end = "-----END CERTIFICATE-----"
        val start = text.indexOf(begin)
        val finish = text.indexOf(end, start.coerceAtLeast(0))
        if (start < 0 || finish < 0) throw IllegalArgumentException("Not a DER or PEM certificate")
        val base64 = text.substring(start + begin.length, finish).filterNot(Char::isWhitespace)
        return factory.generateCertificate(
            ByteArrayInputStream(Base64.getDecoder().decode(base64)),
        ) as X509Certificate
    }

    companion object {
        /** Fixed key length per the feature request: NIST P-256 ECDSA. */
        private const val CURVE_P256 = "secp256r1"
        private const val PEM_LINE_LENGTH = 64

        private val defaultHttp = HttpClient(OkHttp.create()) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
            }
        }
    }
}
