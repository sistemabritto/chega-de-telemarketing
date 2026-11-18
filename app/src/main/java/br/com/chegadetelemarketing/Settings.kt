package br.com.chegadetelemarketing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Configurações e histórico, guardados só no próprio celular. */
class Settings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("config", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun put(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    var enabled: Boolean
        get() = bool("enabled", true)
        set(v) = put("enabled", v)

    /** 0303 = telemarketing ativo, 0304 = doações (regra da Anatel). */
    var blockTelemarketing: Boolean
        get() = bool("block_0303", true)
        set(v) = put("block_0303", v)

    var blockHidden: Boolean
        get() = bool("block_hidden", true)
        set(v) = put("block_hidden", v)

    /** A operadora avisou que o número exibido é falso (verificação de origem falhou). */
    var blockSpoofed: Boolean
        get() = bool("block_spoofed", true)
        set(v) = put("block_spoofed", v)

    var blockInternational: Boolean
        get() = bool("block_international", false)
        set(v) = put("block_international", v)

    var blockUnknown: Boolean
        get() = bool("block_unknown", false)
        set(v) = put("block_unknown", v)

    /** Se um número desconhecido insistir 2x em poucos minutos, deixa passar (pode ser urgente). */
    var allowRepeat: Boolean
        get() = bool("allow_repeat", true)
        set(v) = put("allow_repeat", v)

    /** Deixa passar números para os quais você ligou ou que você atendeu (últimos 90 dias). */
    var allowKnownFromLog: Boolean
        get() = bool("allow_dialed", true)
        set(v) = put("allow_dialed", v)

    /** Em vez de recusar, só silencia: a ligação aparece como perdida. */
    var silenceOnly: Boolean
        get() = bool("silence_only", false)
        set(v) = put("silence_only", v)

    var customPrefixesText: String
        get() = prefs.getString("custom_prefixes", "") ?: ""
        set(v) = prefs.edit().putString("custom_prefixes", v).apply()

    var allowPrefixesText: String
        get() = prefs.getString("allow_prefixes", "") ?: ""
        set(v) = prefs.edit().putString("allow_prefixes", v).apply()

    val customPrefixes: List<String> get() = parsePrefixes(customPrefixesText)
    val allowPrefixes: List<String> get() = parsePrefixes(allowPrefixesText)

    fun addAllowPrefix(number: String) {
        val n = Rules.national(number.filter { it.isDigit() })
        if (n.isEmpty() || n in allowPrefixes) return
        allowPrefixesText = listOf(allowPrefixesText.trim(), n).filter { it.isNotEmpty() }.joinToString(", ")
    }

    val blockedCount: Int get() = prefs.getInt("blocked_count", 0)

    // ---------- níveis prontos ----------

    enum class Level { RECOMMENDED, STRICT, CONTACTS_ONLY, CUSTOM }

    fun applyLevel(level: Level) {
        when (level) {
            Level.RECOMMENDED -> set(unknown = false, intl = false, repeat = true, known = true)
            Level.STRICT -> set(unknown = true, intl = true, repeat = true, known = true)
            Level.CONTACTS_ONLY -> set(unknown = true, intl = true, repeat = false, known = false)
            Level.CUSTOM -> Unit
        }
    }

    private fun set(unknown: Boolean, intl: Boolean, repeat: Boolean, known: Boolean) {
        blockTelemarketing = true
        blockHidden = true
        blockSpoofed = true
        blockUnknown = unknown
        blockInternational = intl
        allowRepeat = repeat
        allowKnownFromLog = known
    }

    fun currentLevel(): Level {
        if (!blockTelemarketing || !blockHidden || !blockSpoofed) return Level.CUSTOM
        return when (listOf(blockUnknown, blockInternational, allowRepeat, allowKnownFromLog)) {
            listOf(false, false, true, true) -> Level.RECOMMENDED
            listOf(true, true, true, true) -> Level.STRICT
            listOf(true, true, false, false) -> Level.CONTACTS_ONLY
            else -> Level.CUSTOM
        }
    }

    // ---------- histórico ----------

    data class Entry(val time: Long, val number: String, val reason: String, val silenced: Boolean)

    @Synchronized
    fun addLog(number: String, reason: String, silenced: Boolean) {
        val arr = JSONArray(prefs.getString("log", "[]"))
        val out = JSONArray()
        out.put(
            JSONObject().put("t", System.currentTimeMillis()).put("n", number)
                .put("r", reason).put("s", silenced)
        )
        for (i in 0 until minOf(arr.length(), MAX_LOG - 1)) out.put(arr.get(i))
        prefs.edit()
            .putString("log", out.toString())
            .putInt("blocked_count", blockedCount + 1)
            .apply()
    }

    fun log(): List<Entry> {
        val arr = JSONArray(prefs.getString("log", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getLong("t"), o.getString("n"), o.getString("r"), o.optBoolean("s", false))
        }
    }

    fun clearLog() {
        prefs.edit().putString("log", "[]").putInt("blocked_count", 0).apply()
    }

    // ---------- insistência (mesmo número ligando de novo) ----------

    /** Retorna true se este número já tentou ligar nos últimos minutos. Sempre registra a tentativa atual. */
    @Synchronized
    fun calledRecentlyAndRecord(number: String): Boolean {
        val now = System.currentTimeMillis()
        val obj = JSONObject(prefs.getString("recent", "{}"))
        val last = obj.optLong(number, 0L)
        val fresh = JSONObject()
        obj.keys().forEach { k -> if (now - obj.getLong(k) < REPEAT_WINDOW_MS) fresh.put(k, obj.getLong(k)) }
        fresh.put(number, now)
        prefs.edit().putString("recent", fresh.toString()).apply()
        return last > 0 && now - last < REPEAT_WINDOW_MS
    }

    companion object {
        const val MAX_LOG = 200
        const val REPEAT_WINDOW_MS = 5 * 60 * 1000L

        fun parsePrefixes(text: String): List<String> = text
            .split(',', ';', '\n')
            .map { p -> p.filter { it.isDigit() }.trimStart('0') }
            .filter { it.length >= 2 }
    }
}
