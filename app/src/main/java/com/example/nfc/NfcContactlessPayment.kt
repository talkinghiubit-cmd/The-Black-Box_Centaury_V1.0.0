package com.example.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.provider.Settings
import com.example.model.VaultCategory
import com.example.model.VaultItem
import com.example.security.TaskAnalyticsEngine
import com.example.security.TaskLifecycleState
import com.example.security.TrackedTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

enum class NfcCardNetwork(val displayName: String, val aidHex: String) {
    VISA("VISA", "A0000000031010"),
    MASTERCARD("MASTERCARD", "A0000000041010"),
    AMEX("AMEX", "A000000025010801"),
    DISCOVER("DISCOVER", "A0000001523010"),
    GENERIC_CARD("CONTACTLESS CARD", "F0424C41434B424F58")
}

data class NfcHardwareStatus(
    val hasNfcHardware: Boolean = false,
    val isNfcEnabled: Boolean = false,
    val hasHceSupport: Boolean = false,
    val isDefaultPaymentService: Boolean = false
)

data class ArmedNfcCardSession(
    val itemId: String,
    val title: String,
    val network: NfcCardNetwork,
    val maskedLast4: String,
    val tokenReference: String,
    val armedAtMillis: Long,
    val expiresAtMillis: Long,
    val tapExchangeCount: Int = 0
) {
    fun remainingSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
        return ((expiresAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
    }
}

/**
 * Volatile RAM-only contactless session controller for "Pay with NFC".
 *
 * Security invariants:
 * - Ephemeral session metadata lives strictly in RAM and never writes card data to disk.
 * - Duress partition records can never arm contactless NFC payment sessions.
 * - Uses a per-session random SHA-256 token reference instead of exposing raw CVV or full PAN.
 * - Auto-expires after 60 seconds and immediately clears on vault lock or OS memory trim.
 */
object NfcPaymentSession {

    const val DEFAULT_TTL_MILLIS = 60_000L
    private val lock = Any()
    private val secureRandom = SecureRandom()

    private val _sessionFlow = MutableStateFlow<ArmedNfcCardSession?>(null)
    val sessionFlow: StateFlow<ArmedNfcCardSession?> = _sessionFlow.asStateFlow()

    fun detectCardNetwork(cardNumber: String): NfcCardNetwork {
        val digits = cardNumber.filter(Char::isDigit)
        if (digits.isEmpty()) return NfcCardNetwork.GENERIC_CARD
        return when {
            digits.startsWith("4") -> NfcCardNetwork.VISA
            digits.startsWith("34") || digits.startsWith("37") -> NfcCardNetwork.AMEX
            digits.startsWith("6011") || digits.startsWith("65") -> NfcCardNetwork.DISCOVER
            digits.length >= 2 && digits.substring(0, 2).toIntOrNull() in 51..55 -> NfcCardNetwork.MASTERCARD
            digits.length >= 4 && digits.substring(0, 4).toIntOrNull() in 2221..2720 -> NfcCardNetwork.MASTERCARD
            else -> NfcCardNetwork.GENERIC_CARD
        }
    }

    fun formatMaskedLast4(cardNumber: String): String {
        val digits = cardNumber.filter(Char::isDigit)
        return if (digits.length >= 4) "•••• ${digits.takeLast(4)}" else "••••"
    }

    fun arm(
        context: Context,
        item: VaultItem,
        isDuress: Boolean,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        nowMillis: Long = System.currentTimeMillis()
    ): ArmedNfcCardSession? {
        if (isDuress || item.category != VaultCategory.CARD || !item.isNfcPayEnabled) {
            return null
        }
        val digits = item.cardNumber.filter(Char::isDigit)
        if (digits.length < 4) {
            return null
        }

        val network = detectCardNetwork(digits)
        val masked = formatMaskedLast4(digits)
        val nonce = ByteArray(16).also { secureRandom.nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(item.id.toByteArray(Charsets.UTF_8))
            update(nowMillis.toString().toByteArray(Charsets.UTF_8))
            update(nonce)
        }.digest()
        val tokenRef = digest.take(8).joinToString("") { "%02X".format(it) }

        val session = ArmedNfcCardSession(
            itemId = item.id,
            title = item.title,
            network = network,
            maskedLast4 = masked,
            tokenReference = tokenRef,
            armedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + ttlMillis
        )
        synchronized(lock) {
            _sessionFlow.value = session
        }
        TaskAnalyticsEngine.recordTaskStarted(
            context = context.applicationContext,
            taskType = TrackedTaskType.NFC_PAYMENT_SESSION,
            taskName = "Pay with NFC (${network.displayName})",
            timestampMillis = nowMillis
        )
        return session
    }

    fun getActiveSession(nowMillis: Long = System.currentTimeMillis()): ArmedNfcCardSession? {
        synchronized(lock) {
            val current = _sessionFlow.value ?: return null
            if (nowMillis >= current.expiresAtMillis) {
                _sessionFlow.value = null
                return null
            }
            return current
        }
    }

    fun recordApduExchange(context: Context, nowMillis: Long = System.currentTimeMillis()): ArmedNfcCardSession? {
        val updated = synchronized(lock) {
            val current = _sessionFlow.value ?: return null
            if (nowMillis >= current.expiresAtMillis) {
                _sessionFlow.value = null
                return null
            }
            val next = current.copy(tapExchangeCount = current.tapExchangeCount + 1)
            _sessionFlow.value = next
            next
        }
        TaskAnalyticsEngine.recordTaskCompleted(
            context = context.applicationContext,
            taskType = TrackedTaskType.NFC_PAYMENT_SESSION,
            taskName = "Pay with NFC (${updated.network.displayName})",
            durationMs = (nowMillis - updated.armedAtMillis).coerceAtLeast(0L),
            note = "Contactless APDU handshake completed",
            timestampMillis = nowMillis
        )
        return updated
    }

    fun disarm(
        context: Context? = null,
        reason: String = "NFC contactless session disarmed",
        expired: Boolean = false
    ) {
        val previous = synchronized(lock) {
            val current = _sessionFlow.value
            _sessionFlow.value = null
            current
        }
        if (previous != null && context != null) {
            val elapsed = (System.currentTimeMillis() - previous.armedAtMillis).coerceAtLeast(0L)
            if (expired && previous.tapExchangeCount > 0) {
                TaskAnalyticsEngine.recordTaskCompleted(
                    context = context.applicationContext,
                    taskType = TrackedTaskType.NFC_PAYMENT_SESSION,
                    taskName = "Pay with NFC (${previous.network.displayName})",
                    durationMs = elapsed,
                    note = "Completed ${previous.tapExchangeCount} tap handshake(s)"
                )
            } else {
                TaskAnalyticsEngine.recordTaskStopped(
                    context = context.applicationContext,
                    taskType = TrackedTaskType.NFC_PAYMENT_SESSION,
                    taskName = "Pay with NFC (${previous.network.displayName})",
                    stopReasonCode = TaskAnalyticsEngine.STOP_REASON_APP_CANCELLED,
                    stopReasonLabel = reason,
                    state = TaskLifecycleState.STOPPED,
                    durationMs = elapsed
                )
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            _sessionFlow.value = null
        }
    }
}

object NfcContactlessHelper {

    fun queryHardwareStatus(context: Context): NfcHardwareStatus {
        val appContext = context.applicationContext
        val adapter = runCatching { NfcAdapter.getDefaultAdapter(appContext) }.getOrNull()
        val hasHardware = adapter != null ||
            runCatching { appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) }.getOrDefault(false)
        val isEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val hasHce = runCatching {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        }.getOrDefault(false)
        val isDefaultPayment = if (adapter != null && hasHce) {
            runCatching {
                val cardEmulation = CardEmulation.getInstance(adapter)
                val component = ComponentName(appContext, BlackBoxHceService::class.java)
                cardEmulation.isDefaultServiceForCategory(component, CardEmulation.CATEGORY_PAYMENT)
            }.getOrDefault(false)
        } else {
            false
        }
        return NfcHardwareStatus(
            hasNfcHardware = hasHardware,
            isNfcEnabled = isEnabled,
            hasHceSupport = hasHce,
            isDefaultPaymentService = isDefaultPayment
        )
    }

    fun enableForegroundPreferredService(activity: Activity): Boolean {
        return runCatching {
            val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
            val cardEmulation = CardEmulation.getInstance(adapter)
            cardEmulation.setPreferredService(
                activity,
                ComponentName(activity, BlackBoxHceService::class.java)
            )
        }.getOrDefault(false)
    }

    fun disableForegroundPreferredService(activity: Activity): Boolean {
        return runCatching {
            val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
            CardEmulation.getInstance(adapter).unsetPreferredService(activity)
        }.getOrDefault(false)
    }

    fun openNfcSettings(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openTapAndPayDefaultSettings(context: Context): Boolean {
        val component = ComponentName(context.applicationContext, BlackBoxHceService::class.java)
        return runCatching {
            val intent = Intent(CardEmulation.ACTION_CHANGE_DEFAULT).apply {
                putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
                putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            openNfcSettings(context)
        }
    }
}

/**
 * Android Host-based Card Emulation (HCE) service for contactless "Pay with NFC" sessions.
 *
 * Protected by `android.permission.BIND_NFC_SERVICE` and `android:requireDeviceUnlock="true"`.
 * Only responds positively (`90 00`) to terminal `SELECT` APDUs while an ephemeral,
 * user-armed [NfcPaymentSession] is active in RAM; otherwise fails closed with `69 85`.
 */
class BlackBoxHceService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_CONDITIONS_NOT_SATISFIED
        }

        val session = NfcPaymentSession.getActiveSession()
            ?: return STATUS_CONDITIONS_NOT_SATISFIED

        // Check ISO 7816-4 SELECT APDU header: CLA=0x00, INS=0xA4, P1=0x04
        val isSelectCommand = commandApdu[0] == 0x00.toByte() &&
            commandApdu[1] == 0xA4.toByte() &&
            commandApdu[2] == 0x04.toByte()

        if (!isSelectCommand) {
            return STATUS_INS_NOT_SUPPORTED
        }

        NfcPaymentSession.recordApduExchange(applicationContext)
        val payloadBytes = "BBX-${session.network.name}-${session.tokenReference}".toByteArray(Charsets.US_ASCII)
        return payloadBytes + STATUS_SUCCESS
    }

    override fun onDeactivated(reason: Int) {
        val reasonLabel = when (reason) {
            DEACTIVATION_LINK_LOSS -> "NFC terminal link deactivated after tap"
            DEACTIVATION_DESELECTED -> "NFC reader deselected Black-Box HCE service"
            else -> "NFC contactless session deactivated ($reason)"
        }
        val active = NfcPaymentSession.getActiveSession()
        if (active != null && active.tapExchangeCount > 0) {
            NfcPaymentSession.disarm(applicationContext, reason = reasonLabel, expired = true)
        }
    }

    companion object {
        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val STATUS_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
        val STATUS_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
    }
}
