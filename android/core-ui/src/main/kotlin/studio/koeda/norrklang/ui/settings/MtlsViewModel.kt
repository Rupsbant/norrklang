package studio.koeda.norrklang.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.koeda.norrklang.data.mtls.MtlsManager
import studio.koeda.norrklang.data.mtls.MtlsManager.Failure
import studio.koeda.norrklang.data.mtls.MtlsManager.MtlsException
import studio.koeda.norrklang.data.mtls.MtlsManager.Status
import studio.koeda.norrklang.data.mtls.MtlsManager.UploadMethod
import studio.koeda.norrklang.ui.R

/**
 * Drives the mTLS page: generate the keypair, hand the public key to the
 * server (QR, copy, or the upload endpoint), and download the signed
 * certificate. One-shot messages are surfaced as [message] and cleared by
 * the next action.
 */
@HiltViewModel
class MtlsViewModel @Inject constructor(
    private val mtls: MtlsManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val status: StateFlow<Status> = mtls.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Status.None)

    private val _uploadUrl = MutableStateFlow("")
    val uploadUrl: StateFlow<String> = _uploadUrl.asStateFlow()

    fun setUploadUrl(value: String) {
        _uploadUrl.value = value
    }

    private val _downloadUrl = MutableStateFlow("")
    val downloadUrl: StateFlow<String> = _downloadUrl.asStateFlow()

    fun setDownloadUrl(value: String) {
        _downloadUrl.value = value
    }

    private val _uploadMethod = MutableStateFlow(UploadMethod.GET)
    val uploadMethod: StateFlow<UploadMethod> = _uploadMethod.asStateFlow()

    fun setUploadMethod(method: UploadMethod) {
        _uploadMethod.value = method
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private fun publicKeyPem(): String? = when (val s = status.value) {
        is Status.KeyGenerated -> s.publicKeyPem
        is Status.Ready -> s.publicKeyPem
        Status.None -> null
    }

    fun generateKeyPair() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { mtls.generateKeyPair() }
                .onSuccess { _message.value = context.getString(R.string.mtls_generated) }
                .onFailure { _message.value = context.getString(R.string.mtls_error_generic, it.message) }
            _busy.value = false
        }
    }

    fun copyPublicKey() {
        val pem = publicKeyPem() ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Norrklang public key", pem))
        _message.value = context.getString(R.string.mtls_copied)
    }

    fun uploadPublicKey() {
        val pem = publicKeyPem()
        if (pem == null) {
            message(R.string.mtls_need_keypair)
            return
        }
        val url = _uploadUrl.value.trim()
        if (url.isEmpty()) {
            message(R.string.mtls_need_url)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { mtls.uploadPublicKey(url, _uploadMethod.value, pem) }
                .onSuccess { result ->
                    if (result.status in 200..299) {
                        result.location?.let { _downloadUrl.value = it }
                        _message.value =
                            if (result.location != null) {
                                context.getString(R.string.mtls_upload_location)
                            } else {
                                context.getString(R.string.mtls_upload_success, result.status)
                            }
                    } else {
                        _message.value =
                            context.getString(R.string.mtls_upload_failed, result.status)
                    }
                }
                .onFailure {
                    _message.value = context.getString(R.string.mtls_error_network)
                }
            _busy.value = false
        }
    }

    fun downloadCertificate() {
        if (publicKeyPem() == null) {
            message(R.string.mtls_need_keypair)
            return
        }
        val url = _downloadUrl.value.trim()
        if (url.isEmpty()) {
            message(R.string.mtls_need_url)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { mtls.downloadCertificate(url) }
                .flatMap { mtls.installCertificate(it) }
                .onSuccess { _message.value = context.getString(R.string.mtls_certificate_installed) }
                .onFailure { e ->
                    _message.value = when ((e as? MtlsException)?.failure) {
                        Failure.KEY_MISMATCH ->
                            context.getString(R.string.mtls_certificate_mismatch)
                        Failure.INVALID_CERTIFICATE ->
                            context.getString(R.string.mtls_certificate_invalid)
                        else -> context.getString(R.string.mtls_error_network)
                    }
                }
            _busy.value = false
        }
    }

    fun resetIdentity() {
        viewModelScope.launch {
            _busy.value = true
            runCatching { mtls.reset() }
                .onSuccess { _message.value = context.getString(R.string.mtls_reset_done) }
                .onFailure { _message.value = context.getString(R.string.mtls_error_generic, it.message) }
            _busy.value = false
        }
    }

    private fun message(resId: Int) {
        _message.value = context.getString(resId)
    }
}
