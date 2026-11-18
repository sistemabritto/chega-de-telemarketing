package br.com.chegadetelemarketing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Configurações e histórico, guardados só no próprio celular. */
class Settings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("config", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** 0303 = telemarketing ativo, 0304 = doações (regra da Anatel). */
    var blockTelemarketing: Boolean
        get() = prefs.getBoolean("block_0303", true)
        set(v) = prefs.edit().putBoolean("block_0303", v).apply()

    var blockHidden: Boolean
        get() = prefs.getBoolean("block_hidden", true)
        set(v) = prefs.edit().putBoolean("block_hidden", v).apply()

    var blockUnknown: Boolean
        get() = prefs.getBoolean("block_unknown", false)
        set(v) = prefs.edit().putBoolean("block_unknown", v).apply()

    /** Se um número desconhecido insistir 2x em poucos minutos, deixa passar (pode ser urgente). */
    var allowRepeat: Boolean
        get() = prefs.getBoolean("allow_repeat", true)
        set(v) = prefs.edit().putBoolean("allow_repeat", v).apply()

    var customPrefixesText: String
        get() = prefs.getString("custom_prefixes", "") ?: ""
        set(v) = prefs.edit().putString("custom_prefixes", v).apply()

    val customPrefixes: List<String>
        get() = customPrefixesText
            .split(',', ';', '\n', ' ')
            .map { p -> p.filter { it.isDigit() }.trimStart('0') }
            .filter { it.length >= 2 }

    val blockedCount: Int get() = prefs.getInt("blocked_count", 0)

    // ---------- histórico ----------

    data class Entry(val time: Long, val number: String, val reason: String)

    @Synchronized
    fun addLog(number: String, reason: String) {
        val arr = JSONArray(prefs.getString("log", "[]"))
        val out = JSONArray()
        out.put(JSONObject().put("t", System.currentTimeMillis()).put("n", number).put("r", reason))
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
            Entry(o.getLong("t"), o.getString("n"), o.getString("r"))
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
    }
}
