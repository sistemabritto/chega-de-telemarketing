package br.com.chegadetelemarketing

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var roleManager: RoleManager

    private lateinit var statusPill: TextView
    private lateinit var counter: TextView
    private lateinit var btnActivate: TextView
    private lateinit var levelHint: TextView
    private lateinit var prefixes: EditText
    private lateinit var allowPrefixes: EditText
    private lateinit var logList: LinearLayout
    private lateinit var emergencyNote: TextView

    private val levelChips = mutableMapOf<Settings.Level, TextView>()
    private val actionChips = mutableMapOf<Boolean, TextView>()
    private val toggles = mutableListOf<Pair<Toggle, Switch>>()

    private data class Toggle(
        val title: String,
        val subtitle: String,
        val get: () -> Boolean,
        val set: (Boolean) -> Unit,
        val permission: String? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = Settings(this)
        settings.migrate()
        roleManager = getSystemService(RoleManager::class.java)

        statusPill = findViewById(R.id.statusPill)
        counter = findViewById(R.id.counter)
        btnActivate = findViewById(R.id.btnActivate)
        levelHint = findViewById(R.id.levelHint)
        prefixes = findViewById(R.id.prefixes)
        allowPrefixes = findViewById(R.id.allowPrefixes)
        logList = findViewById(R.id.logList)

        buildLevelChips()
        buildRules()
        buildActionChips()

        prefixes.setText(settings.customPrefixesText)
        allowPrefixes.setText(settings.allowPrefixesText)
        findViewById<View>(R.id.btnSavePrefixes).setOnClickListener {
            saveLists()
            Toast.makeText(
                this,
                "Salvo: ${settings.customPrefixes.size} para bloquear, ${settings.allowPrefixes.size} para permitir",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnActivate.setOnClickListener { requestRole() }
        findViewById<View>(R.id.btnDefaultApps).setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            settings.clearLog()
            refreshLog()
        }
        findViewById<View>(R.id.footerBrand).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sistemabritto.com.br")))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        saveLists()
    }

    private fun saveLists() {
        settings.customPrefixesText = prefixes.text.toString()
        settings.allowPrefixesText = allowPrefixes.text.toString()
    }

    // ---------------- construção da tela ----------------

    private fun buildLevelChips() {
        val box = findViewById<LinearLayout>(R.id.levelChips)
        listOf(
            Settings.Level.RECOMMENDED to "Recomendado",
            Settings.Level.STRICT to "Rigoroso",
            Settings.Level.CONTACTS_ONLY to "Só contatos",
        ).forEachIndexed { i, (level, label) ->
            val chip = chip(label, first = i == 0)
            chip.setOnClickListener {
                settings.applyLevel(level)
                askMissingPermissions()
                refreshAll()
            }
            box.addView(chip)
            levelChips[level] = chip
        }
    }

    private fun buildActionChips() {
        val box = findViewById<LinearLayout>(R.id.actionChips)
        listOf(false to "Recusar", true to "Silenciar").forEachIndexed { i, (silence, label) ->
            val chip = chip(label, first = i == 0)
            chip.setOnClickListener {
                settings.silenceOnly = silence
                refreshAll()
            }
            box.addView(chip)
            actionChips[silence] = chip
        }
    }

    private fun buildRules() {
        val box = findViewById<LinearLayout>(R.id.rules)
        val list = listOf(
            Toggle("Bloqueio ligado", "Desligue para pausar tudo sem desinstalar.",
                { settings.enabled }, { settings.enabled = it }),
            Toggle("Telemarketing oficial (0303 e 0304)", "Prefixos que a Anatel obriga para telemarketing e doações.",
                { settings.blockTelemarketing }, { settings.blockTelemarketing = it }),
            Toggle("Números ocultos", "Quem liga como privado ou sem número.",
                { settings.blockHidden }, { settings.blockHidden = it }),
            Toggle("Número falsificado", "Quando a operadora avisa que o número foi forjado.",
                { settings.blockSpoofed }, { settings.blockSpoofed = it }),
            Toggle("Ligações internacionais", "Números de fora do Brasil, comuns em golpe.",
                { settings.blockInternational }, { settings.blockInternational = it }),
            Toggle("Todos fora dos contatos", "O modo que acaba com as 50 ligações por dia.",
                { settings.blockUnknown }, { settings.blockUnknown = it }, Manifest.permission.READ_CONTACTS),
            Toggle("Quem você já ligou passa", "Clínica, entregador, quem você retornou nos últimos 90 dias.",
                { settings.allowKnownFromLog }, { settings.allowKnownFromLog = it }, Manifest.permission.READ_CALL_LOG),
            Toggle("Insistiu 2x em 5 minutos, passa", "Pode ser urgente: hospital, escola, banco.",
                { settings.allowRepeat }, { settings.allowRepeat = it }),
        )
        list.forEach { t -> box.addView(toggleRow(t)) }

        emergencyNote = TextView(this).apply {
            setTextAppearanceBody(13f)
            setTextColor(getColor(R.color.gold))
            setPadding(0, dp(14), 0, 0)
        }
        box.addView(emergencyNote)
    }

    private fun toggleRow(t: Toggle): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = t.title
            typeface = resources.getFont(R.font.space_grotesk_medium)
            setTextColor(getColor(R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        texts.addView(TextView(this).apply {
            text = t.subtitle
            setTextAppearanceBody(13f)
        })
        val sw = Switch(this).apply {
            setPadding(dp(12), 0, 0, 0)
            setOnCheckedChangeListener(listenerFor(t))
        }
        row.addView(texts)
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        toggles += t to sw
        return row
    }

    private fun chip(label: String, first: Boolean) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        typeface = resources.getFont(R.font.space_grotesk_medium)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(getColorStateList(R.color.chip_text))
        background = getDrawable(R.drawable.bg_chip)
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            if (!first) marginStart = dp(8)
        }
    }

    private fun TextView.setTextAppearanceBody(sizeSp: Float) {
        typeface = resources.getFont(R.font.dm_sans_regular)
        setTextColor(getColor(R.color.muted))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------- atualização ----------------

    private fun refreshAll() {
        toggles.forEach { (t, sw) ->
            val on = t.get() && (t.permission == null || Rules.hasPermission(this, t.permission))
            if (sw.isChecked != on) {
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = on
                sw.setOnCheckedChangeListener(listenerFor(t))
            }
        }
        actionChips.forEach { (silence, chip) -> chip.isSelected = settings.silenceOnly == silence }
        emergencyNote.text = if (Rules.hasPermission(this, Manifest.permission.READ_CALL_LOG)) {
            "🚨 Ligou para 190, 192 ou 193? Por 1 hora tudo passa, até número oculto."
        } else {
            "🚨 Permita acesso ao registro de chamadas para liberar tudo depois que você ligar para 190, 192 ou 193."
        }
        refreshLevel()
        refreshStatus()
        refreshLog()
    }

    private fun listenerFor(t: Toggle) =
        android.widget.CompoundButton.OnCheckedChangeListener { _, checked ->
            if (checked && t.permission != null && !Rules.hasPermission(this, t.permission)) {
                t.set(true)
                requestPermissions(arrayOf(t.permission), REQ_PERMS)
            } else {
                t.set(checked)
            }
            refreshLevel()
            refreshStatus()
        }

    private fun refreshLevel() {
        val level = settings.currentLevel()
        levelChips.forEach { (l, chip) -> chip.isSelected = l == level }
        levelHint.text = when (level) {
            Settings.Level.RECOMMENDED -> "Barra 0303, ocultos e falsificados. Número comum desconhecido ainda toca."
            Settings.Level.STRICT -> "Só toca quem está nos contatos ou quem você já ligou. Acaba com o call center."
            Settings.Level.CONTACTS_ONLY -> "Só toca quem está na sua agenda. Mais nada."
            Settings.Level.CUSTOM -> "Personalizado: você ajustou as regras do seu jeito."
        }
    }

    private fun hasRole() = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun refreshStatus() {
        val active = hasRole() && settings.enabled
        statusPill.text = when {
            active -> "●  PROTEÇÃO ATIVA"
            hasRole() -> "●  BLOQUEIO PAUSADO"
            else -> "●  PROTEÇÃO DESLIGADA"
        }
        statusPill.setTextColor(getColor(if (active) R.color.lime else R.color.danger))
        statusPill.background = getDrawable(if (active) R.drawable.bg_pill_on else R.drawable.bg_pill_off)
        btnActivate.visibility = if (hasRole()) View.GONE else View.VISIBLE
    }

    private fun refreshLog() {
        counter.text = settings.blockedCount.toString()
        logList.removeAllViews()
        val entries = settings.log()
        if (entries.isEmpty()) {
            logList.addView(TextView(this).apply {
                text = if (hasRole()) {
                    "Nenhuma ligação recebida desde a ativação. Quando alguém ligar, aparece aqui se passou ou foi barrada, e por quê."
                } else {
                    "O app ainda não está ativado, então não vê nenhuma ligação. Toque em Ativar proteção."
                }
                setTextAppearanceBody(14f)
            })
            return
        }
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
        entries.take(60).forEach { e -> logList.addView(logRow(e, fmt)) }
    }

    private fun logRow(e: Settings.Entry, fmt: SimpleDateFormat): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_row)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(TextView(this).apply {
            text = e.number
            typeface = resources.getFont(R.font.space_grotesk_medium)
            setTextColor(getColor(R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = fmt.format(Date(e.time))
            setTextAppearanceBody(12f)
        })
        row.addView(top)
        val (label, color) = when (e.action) {
            Settings.Action.BLOCKED -> "✕ Barrada" to R.color.lime
            Settings.Action.SILENCED -> "🔇 Silenciada" to R.color.lime
            Settings.Action.ALLOWED -> "✓ Tocou" to R.color.gold
        }
        row.addView(TextView(this).apply {
            text = "$label · ${e.reason}"
            typeface = resources.getFont(R.font.dm_sans_regular)
            setTextColor(getColor(color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        if (e.number != "Oculto") row.setOnClickListener { showEntryMenu(e) }
        return row
    }

    private fun showEntryMenu(e: Settings.Entry) {
        val first = if (e.action == Settings.Action.ALLOWED) "Sempre bloquear este número" else "Sempre permitir este número"
        AlertDialog.Builder(this)
            .setTitle(e.number)
            .setItems(arrayOf(first, "Copiar número")) { _, which ->
                when (which) {
                    0 -> if (e.action == Settings.Action.ALLOWED) {
                        settings.addBlockPrefix(e.number)
                        prefixes.setText(settings.customPrefixesText)
                        Toast.makeText(this, "Bloqueado. Da próxima vez não toca.", Toast.LENGTH_SHORT).show()
                    } else {
                        settings.addAllowPrefix(e.number)
                        allowPrefixes.setText(settings.allowPrefixesText)
                        Toast.makeText(this, "Liberado. Da próxima vez ele toca.", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("número", e.number))
                        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    // ---------------- papel e permissões ----------------

    private fun requestRole() {
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Este aparelho não permite apps de filtro de chamadas.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE)
    }

    /** Pede de uma vez as permissões que as regras ligadas precisam. */
    private fun askMissingPermissions() {
        val needed = toggles.mapNotNull { (t, _) -> t.permission?.takeIf { t.get() } }
            .plus(Manifest.permission.READ_CONTACTS) // para nunca bloquear contato
            .distinct()
            .filterNot { Rules.hasPermission(this, it) }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQ_PERMS)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ROLE) {
            if (hasRole()) {
                askMissingPermissions()
            } else {
                Toast.makeText(
                    this,
                    "Não ativou. Toque em \"Abrir apps padrão\" e escolha este app em \"App de ID de chamadas e spam\".",
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshAll()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if (!Rules.hasPermission(this, Manifest.permission.READ_CONTACTS) && settings.blockUnknown) {
            settings.blockUnknown = false
            Toast.makeText(this, "Sem acesso aos contatos não dá para saber quem é conhecido.", Toast.LENGTH_LONG).show()
        }
        refreshAll()
    }

    companion object {
        private const val REQ_ROLE = 1
        private const val REQ_PERMS = 2
    }
}
