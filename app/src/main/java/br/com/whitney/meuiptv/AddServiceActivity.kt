package br.com.whitney.meuiptv

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Cadastro de um serviço feito pela PRÓPRIA pessoa: ela informa a URL do
 * painel dela, ou o link M3U / dados Xtream Codes da lista de canais dela.
 * O app não valida, não bloqueia e não entra em contato com nenhum
 * servidor próprio: só guarda o que a pessoa digitou, neste aparelho.
 */
class AddServiceActivity : AppCompatActivity() {

    private var tipoSelecionado = ServiceType.PAINEL
    private var listaModoXtream = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_service)

        val editNome = findViewById<TextInputEditText>(R.id.editNome)
        val editPanelUrl = findViewById<TextInputEditText>(R.id.editPanelUrl)
        val editM3uUrl = findViewById<TextInputEditText>(R.id.editM3uUrl)
        val editXtreamServer = findViewById<TextInputEditText>(R.id.editXtreamServer)
        val editXtreamUser = findViewById<TextInputEditText>(R.id.editXtreamUser)
        val editXtreamPass = findViewById<TextInputEditText>(R.id.editXtreamPass)

        val grupoPainel = findViewById<LinearLayout>(R.id.grupoPainel)
        val grupoLista = findViewById<LinearLayout>(R.id.grupoLista)
        val campoM3uUrl = findViewById<TextInputLayout>(R.id.campoM3uUrl)
        val camposXtream = findViewById<LinearLayout>(R.id.camposXtream)

        val toggleTipo = findViewById<MaterialButtonToggleGroup>(R.id.toggleTipo)
        val btnTipoPainel = findViewById<MaterialButton>(R.id.btnTipoPainel)
        val toggleListaModo = findViewById<MaterialButtonToggleGroup>(R.id.toggleListaModo)
        val btnModoM3u = findViewById<MaterialButton>(R.id.btnModoM3u)

        toggleTipo.check(btnTipoPainel.id)
        toggleListaModo.check(btnModoM3u.id)

        toggleTipo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            tipoSelecionado = if (checkedId == R.id.btnTipoPainel) ServiceType.PAINEL else ServiceType.M3U
            grupoPainel.visibility = if (tipoSelecionado == ServiceType.PAINEL) View.VISIBLE else View.GONE
            grupoLista.visibility = if (tipoSelecionado == ServiceType.PAINEL) View.GONE else View.VISIBLE
        }

        toggleListaModo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            listaModoXtream = checkedId == R.id.btnModoXtream
            campoM3uUrl.visibility = if (listaModoXtream) View.GONE else View.VISIBLE
            camposXtream.visibility = if (listaModoXtream) View.VISIBLE else View.GONE
        }

        findViewById<MaterialButton>(R.id.btnSalvar).setOnClickListener {
            val nome = editNome.text?.toString()?.trim().orEmpty()
            if (nome.isEmpty()) {
                Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val servico: Service? = when {
                tipoSelecionado == ServiceType.PAINEL -> {
                    val url = normalizarUrl(editPanelUrl.text?.toString())
                    if (url == null) {
                        Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show()
                        null
                    } else {
                        Service(name = nome, type = ServiceType.PAINEL, panelUrl = url)
                    }
                }
                !listaModoXtream -> {
                    val url = normalizarUrl(editM3uUrl.text?.toString())
                    if (url == null) {
                        Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show()
                        null
                    } else {
                        Service(name = nome, type = ServiceType.M3U, m3uUrl = url)
                    }
                }
                else -> {
                    val servidor = normalizarUrl(editXtreamServer.text?.toString())
                    val usuario = editXtreamUser.text?.toString()?.trim().orEmpty()
                    val senha = editXtreamPass.text?.toString().orEmpty()
                    if (servidor == null || usuario.isEmpty() || senha.isEmpty()) {
                        Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show()
                        null
                    } else {
                        Service(
                            name = nome,
                            type = ServiceType.XTREAM,
                            xtreamServer = servidor,
                            xtreamUser = usuario,
                            xtreamPass = senha,
                        )
                    }
                }
            }

            if (servico != null) {
                ServiceStore.adicionar(this, servico)
                finish()
            }
        }
    }

    private fun normalizarUrl(valorDigitado: String?): String? {
        val v = valorDigitado?.trim().orEmpty()
        if (v.isEmpty()) return null
        return if (v.startsWith("http://") || v.startsWith("https://")) v else "https://$v"
    }
}
