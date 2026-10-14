package br.com.whitney.meuiptv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText

/**
 * Carrega e mostra a lista de canais de um serviço M3U ou Xtream Codes
 * cadastrado pela própria pessoa, e toca o canal escolhido.
 */
class ChannelListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVICE_ID = "service_id"
    }

    private lateinit var adapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_list)

        val serviceId = intent.getStringExtra(EXTRA_SERVICE_ID)
        val service = ServiceStore.listar(this).find { it.id == serviceId }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = service?.name ?: getString(R.string.app_name)
        toolbar.setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recyclerCanais)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val txtEstado = findViewById<TextView>(R.id.txtEstado)
        val editBusca = findViewById<TextInputEditText>(R.id.editBusca)

        adapter = ChannelAdapter { canal ->
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra(PlayerActivity.EXTRA_NOME, canal.name)
            i.putExtra(PlayerActivity.EXTRA_URL, canal.streamUrl)
            startActivity(i)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        editBusca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filtrar(s?.toString().orEmpty())
            }
        })

        if (service == null) {
            progress.visibility = View.GONE
            txtEstado.visibility = View.VISIBLE
            txtEstado.text = getString(R.string.channels_error)
            return
        }

        progress.visibility = View.VISIBLE
        txtEstado.visibility = View.GONE

        Thread {
            try {
                val canais = PlaylistLoader.carregar(service)
                runOnUiThread {
                    progress.visibility = View.GONE
                    if (canais.isEmpty()) {
                        txtEstado.visibility = View.VISIBLE
                        txtEstado.text = getString(R.string.channels_empty)
                    } else {
                        adapter.definirLista(canais)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    txtEstado.visibility = View.VISIBLE
                    txtEstado.text = getString(R.string.channels_error)
                }
            }
        }.start()
    }
}
