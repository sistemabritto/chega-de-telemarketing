package br.com.chegadetelemarketing

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager

/**
 * O Android chama este serviço a cada ligação recebida, antes de tocar.
 * Se a regra mandar bloquear, a chamada é recusada em silêncio e a rede móvel continua normal.
 */
class BlockerService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val number = details.handle?.schemeSpecificPart
        val hidden = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED

        val reason = try {
            Rules.evaluate(this, number, hidden)
        } catch (e: Exception) {
            null // na dúvida, deixa tocar
        }

        val response = if (reason != null) {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        respondToCall(details, response)

        if (reason != null) {
            Settings(this).addLog(number?.takeIf { it.isNotBlank() } ?: "Oculto", reason)
        }
    }
}
