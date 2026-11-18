package br.com.chegadetelemarketing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract.PhoneLookup

object Rules {

    /** Números de emergência no Brasil. Depois de ligar para um deles, tudo passa por um tempo. */
    private val EMERGENCY = setOf("190", "191", "192", "193", "194", "197", "198", "199", "100", "180", "181", "188")
    private const val EMERGENCY_WINDOW_MS = 60 * 60 * 1000L
    private const val KNOWN_WINDOW_MS = 90L * 24 * 60 * 60 * 1000

    data class Incoming(
        val rawNumber: String?,
        val hiddenByNetwork: Boolean,
        val verificationFailed: Boolean,
    )

    /** Resultado da análise: bloquear ou não, e sempre o porquê (aparece no histórico). */
    data class Decision(val block: Boolean, val reason: String)

    private fun allow(reason: String) = Decision(false, reason)
    private fun block(reason: String) = Decision(true, reason)

    fun evaluate(ctx: Context, call: Incoming): Decision {
        val s = Settings(ctx)
        s.migrate()
        if (!s.enabled) return allow("Bloqueio pausado")

        // Ligou para a emergência há pouco? Deixa tudo passar, até número oculto (pode ser o retorno).
        if (dialedEmergencyRecently(ctx)) return allow("Você ligou para emergência há menos de 1 hora")

        val raw = call.rawNumber.orEmpty().trim()
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty() || call.hiddenByNetwork) {
            return if (s.blockHidden) block("Número oculto") else allow("Número oculto (regra desligada)")
        }

        // Quem está nos contatos sempre passa.
        if (isContact(ctx, raw)) return allow("Está nos seus contatos")

        val national = national(digits)

        // Lista "sempre permitir".
        s.allowPrefixes.firstOrNull { national.startsWith(it) || digits.startsWith(it) }?.let {
            return allow("Está na sua lista sempre permitir ($it)")
        }

        if (s.blockSpoofed && call.verificationFailed) return block("Número falsificado")

        if (s.blockTelemarketing && (national.startsWith("303") || national.startsWith("304"))) {
            return block("Telemarketing (0303/0304)")
        }

        s.customPrefixes.firstOrNull { national.startsWith(it) || digits.startsWith(it) }?.let {
            return block("Está na sua lista sempre bloquear ($it)")
        }

        if (s.blockInternational && raw.startsWith("+") && !raw.startsWith("+55")) {
            return block("Internacional")
        }

        if (!s.blockUnknown) return allow("Desconhecido, mas a regra \"Todos fora dos contatos\" está desligada")

        // Só bloqueia desconhecidos se puder ler os contatos; senão bloquearia amigos também.
        if (!hasPermission(ctx, Manifest.permission.READ_CONTACTS)) {
            return allow("Sem permissão de Contatos: o app não sabe quem é conhecido")
        }
        if (s.allowKnownFromLog && dialedRecently(ctx, national)) {
            return allow("Você ligou para este número nos últimos 90 dias")
        }
        val insisted = s.calledRecentlyAndRecord(national)
        if (s.allowRepeat && insisted) return allow("Insistiu 2x em 5 minutos")
        return block("Fora dos contatos")
    }

    /** Tira +55 e zeros do começo: "+55 0303 123 4567" -> "3031234567". */
    fun national(digits: String): String {
        var n = digits
        if (n.startsWith("55") && n.length >= 12) n = n.substring(2)
        return n.trimStart('0')
    }

    private fun sameNumber(a: String, b: String): Boolean {
        if (a == b) return true
        return a.length >= 8 && b.length >= 8 && a.takeLast(8) == b.takeLast(8)
    }

    fun hasPermission(ctx: Context, permission: String) =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun isContact(ctx: Context, number: String): Boolean {
        if (!hasPermission(ctx, Manifest.permission.READ_CONTACTS)) return false
        return try {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Você LIGOU para este número nos últimos 90 dias?
     * Ligação recebida e atendida não conta: quem atende um call center uma vez não pode liberá-lo para sempre.
     */
    private fun dialedRecently(ctx: Context, national: String): Boolean =
        scanCallLog(
            ctx,
            since = System.currentTimeMillis() - KNOWN_WINDOW_MS,
            types = listOf(CallLog.Calls.OUTGOING_TYPE),
        ) { number -> sameNumber(national(number.filter { it.isDigit() }), national) }

    private fun dialedEmergencyRecently(ctx: Context): Boolean =
        scanCallLog(
            ctx,
            since = System.currentTimeMillis() - EMERGENCY_WINDOW_MS,
            types = listOf(CallLog.Calls.OUTGOING_TYPE),
        ) { number -> number.filter { it.isDigit() } in EMERGENCY }

    private fun scanCallLog(ctx: Context, since: Long, types: List<Int>, match: (String) -> Boolean): Boolean {
        if (!hasPermission(ctx, Manifest.permission.READ_CALL_LOG)) return false
        return try {
            val typeSql = types.joinToString(",")
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.TYPE} IN ($typeSql)",
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                var checked = 0
                while (c.moveToNext() && checked++ < 2000) {
                    if (match(c.getString(0).orEmpty())) return true
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
