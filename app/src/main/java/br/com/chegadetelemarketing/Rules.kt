package br.com.chegadetelemarketing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup

object Rules {

    /** Retorna o motivo do bloqueio, ou null para deixar a ligação tocar normalmente. */
    fun evaluate(ctx: Context, rawNumber: String?, hiddenByNetwork: Boolean): String? {
        val s = Settings(ctx)
        if (!s.enabled) return null

        val raw = rawNumber.orEmpty()
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty() || hiddenByNetwork) {
            return if (s.blockHidden) "Número oculto" else null
        }

        // Quem está nos contatos sempre passa.
        if (isContact(ctx, raw)) return null

        val national = national(digits)

        if (s.blockTelemarketing && (national.startsWith("303") || national.startsWith("304"))) {
            return "Telemarketing (0303/0304)"
        }

        s.customPrefixes.firstOrNull { national.startsWith(it) || digits.startsWith(it) }?.let {
            return "Prefixo bloqueado ($it)"
        }

        // Só bloqueia desconhecidos se puder ler os contatos; senão bloquearia amigos também.
        if (s.blockUnknown && hasContactsPermission(ctx)) {
            val insisted = s.calledRecentlyAndRecord(national)
            if (s.allowRepeat && insisted) return null
            return "Fora dos contatos"
        }
        return null
    }

    /** Tira +55 e zeros/prefixo de operadora do começo: "+55 0303 123 4567" -> "3031234567". */
    fun national(digits: String): String {
        var n = digits
        if (n.startsWith("55") && n.length >= 12) n = n.substring(2)
        return n.trimStart('0')
    }

    fun hasContactsPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun isContact(ctx: Context, number: String): Boolean {
        if (!hasContactsPermission(ctx)) return false
        return try {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
