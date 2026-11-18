package br.com.chegadetelemarketing

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.telecom.TelecomManager

/**
 * O Android chama este serviço a cada ligação recebida, antes de tocar.
 * Se a regra mandar bloquear, a chamada é recusada (ou silenciada) e a rede móvel continua normal.
 */
class BlockerService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val number = details.handle?.schemeSpecificPart
        val call = Rules.Incoming(
            rawNumber = number,
            hiddenByNetwork = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED,
            verificationFailed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED,
        )

        val decision = try {
            Rules.evaluate(this, call)
        } catch (e: Exception) {
            Rules.Decision(false, "Erro no app (${e.javaClass.simpleName}): deixou tocar por segurança")
        }

        val settings = Settings(this)
        val silence = settings.silenceOnly

        val response = when {
            !decision.block -> CallResponse.Builder().build()
            silence -> CallResponse.Builder().setSilenceCall(true).build()
            else -> CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
        }
        respondToCall(details, response)

        // Registra TODA ligação analisada, inclusive as que passaram, com o motivo.
        val action = when {
            !decision.block -> Settings.Action.ALLOWED
            silence -> Settings.Action.SILENCED
            else -> Settings.Action.BLOCKED
        }
        settings.addLog(number?.takeIf { it.isNotBlank() } ?: "Oculto", decision.reason, action)
    }
}
