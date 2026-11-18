package br.com.chegadetelemarketing

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var roleManager: RoleManager

    private lateinit var status: TextView
    private lateinit var btnActivate: Button
    private lateinit var swUnknown: Switch
    private lateinit var prefixes: EditText
    private lateinit var counter: TextView
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = Settings(this)
        roleManager = getSystemService(RoleManager::class.java)

        status = findViewById(R.id.status)
        btnActivate = findViewById(R.id.btnActivate)
        prefixes = findViewById(R.id.prefixes)
        counter = findViewById(R.id.counter)
        logView = findViewById(R.id.log)

        bind(R.id.swEnabled, settings.enabled) { settings.enabled = it }
        bind(R.id.sw0303, settings.blockTelemarketing) { settings.blockTelemarketing = it }
        bind(R.id.swHidden, settings.blockHidden) { settings.blockHidden = it }
        bind(R.id.swRepeat, settings.allowRepeat) { settings.allowRepeat = it }
        swUnknown = bind(R.id.swUnknown, settings.blockUnknown && Rules.hasContactsPermission(this)) { on ->
            if (on && !Rules.hasContactsPermission(this)) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            } else {
                settings.blockUnknown = on
            }
        }

        prefixes.setText(settings.customPrefixesText)
        findViewById<Button>(R.id.btnSavePrefixes).setOnClickListener {
            settings.customPrefixesText = prefixes.text.toString()
            Toast.makeText(this, "Prefixos salvos: ${settings.customPrefixes.size}", Toast.LENGTH_SHORT).show()
        }

        btnActivate.setOnClickListener { requestRole() }
        findViewById<Button>(R.id.btnDefaultApps).setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            settings.clearLog()
            refreshLog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        settings.customPrefixesText = prefixes.text.toString()
    }

    private fun bind(id: Int, initial: Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = findViewById<Switch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        return sw
    }

    private fun hasRole() = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun requestRole() {
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Este aparelho não permite apps de filtro de chamadas.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ROLE) {
            refreshStatus()
            if (!hasRole()) {
                Toast.makeText(
                    this,
                    "Não ativou. Use \"Abrir apps padrão\" e escolha este app em \"App de ID de chamadas e spam\".",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CONTACTS) {
            val ok = Rules.hasContactsPermission(this)
            settings.blockUnknown = ok
            swUnknown.isChecked = ok
            if (!ok) {
                Toast.makeText(this, "Sem acesso aos contatos não dá para saber quem é conhecido.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshStatus() {
        if (hasRole()) {
            status.text = "✅ Proteção ATIVA"
            status.setTextColor(0xFF2E7D32.toInt())
            btnActivate.visibility = View.GONE
        } else {
            status.text = "⚠️ Proteção DESLIGADA — toque em Ativar"
            status.setTextColor(0xFFC62828.toInt())
            btnActivate.visibility = View.VISIBLE
        }
    }

    private fun refreshLog() {
        counter.text = "Ligações bloqueadas: ${settings.blockedCount}"
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
        val entries = settings.log()
        logView.text = if (entries.isEmpty()) {
            "Nenhuma ligação bloqueada ainda."
        } else {
            entries.joinToString("\n") { "${fmt.format(Date(it.time))}  ${it.number}\n      ${it.reason}" }
        }
    }

    companion object {
        private const val REQ_ROLE = 1
        private const val REQ_CONTACTS = 2
    }
}
